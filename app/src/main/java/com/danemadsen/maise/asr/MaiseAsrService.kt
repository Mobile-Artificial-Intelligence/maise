package com.danemadsen.maise.asr

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.danemadsen.maise.R
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val TAG = "MaiseAsrService"
private const val NOTIF_CHANNEL = "maise_asr"
private const val NOTIF_ID = 1001

// Recording parameters — 16 kHz mono 16-bit matches Whisper directly (no resampling)
private const val REC_SAMPLE_RATE = 16000
private const val MAX_RECORD_SECONDS = 30

/**
 * Android [RecognitionService] backed by distil-whisper/distil-small.en via ONNX Runtime.
 *
 * Why startForeground(MICROPHONE) is required:
 * After onStartListening() returns, the RecognitionService base class runs a strict
 * AppOps data-delivery check for RECORD_AUDIO. Only FOREGROUND_SERVICE_TYPE_MICROPHONE
 * grants RECORD_AUDIO AppOps access from background; other types (including SHORT_SERVICE)
 * do not satisfy this check → base class calls onCancel().
 *
 * Why MICROPHONE type works on Android 14+ (API 34+) without a visible activity:
 * The system's SpeechRecognitionManagerService binds to this service with
 * BIND_INCLUDE_CAPABILITIES | BIND_FOREGROUND_SERVICE when this service is the
 * configured default recognizer (Settings.Secure.VOICE_RECOGNITION_SERVICE). This
 * propagates the calling app's foreground state to our process, satisfying the
 * "eligible state" check for FOREGROUND_SERVICE_TYPE_MICROPHONE.
 *
 * Why the service must be pre-started from MainActivity:
 * startForeground() inside a bound-only service still requires the service to have
 * been started (via startService/startForegroundService) on some API levels.
 * We call startService from MainActivity.onCreate() with START_STICKY.
 */
class MaiseAsrService : RecognitionService() {

    private val exceptionHandler = CoroutineExceptionHandler { _, t ->
        Log.e(TAG, "Uncaught coroutine exception", t)
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)

    @Volatile private var asr: WhisperASR? = null
    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var recordedSamples: ShortArray? = null
    @Volatile private var isRecording = false
    @Volatile private var activeJob: Job? = null

    private val initLock = Object()

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        // Self-start so the service is in "started" state before onStartListening() is called.
        // startService() posts onStartCommand() to the main thread's Handler immediately.
        // The speech client can only call startListening() AFTER receiving the IBinder from
        // onBind(), which happens after onCreate() returns — so onStartCommand() is always
        // processed first, guaranteeing startForeground(SHORT_SERVICE) will succeed.
        runCatching { startService(Intent(this, MaiseAsrService::class.java)) }

        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(NOTIF_CHANNEL, "Speech Recognition", NotificationManager.IMPORTANCE_LOW)
                .apply { setSound(null, null) }
        )
        scope.launch {
            try {
                val engine = WhisperASR(applicationContext)
                synchronized(initLock) {
                    asr = engine
                    initLock.notifyAll()
                }
                Log.i(TAG, "WhisperASR ready")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialise WhisperASR", e)
                synchronized(initLock) { initLock.notifyAll() }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Called when MainActivity pre-starts us. Returning START_STICKY ensures the
        // system restarts us if killed, keeping us in "started" state for future
        // startForeground(SHORT_SERVICE) calls in onStartListening().
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        stopListeningInternal()
        asr?.close()
        asr = null
    }

    // -------------------------------------------------------------------------
    // RecognitionService callbacks
    // -------------------------------------------------------------------------

    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        val defaultRecognizer = android.provider.Settings.Secure.getString(
            contentResolver, "voice_recognition_service"
        )
        Log.d(TAG, "onStartListening — default recognizer: $defaultRecognizer")

        // Must call startForeground() here, BEFORE returning, so our process state
        // is elevated before the base class runs checkPermissionAndStartDataDelivery()
        // (which happens after this method returns). Without foreground status that
        // AppOps check fails → base class calls onCancel().
        startListeningForeground()

        if (isRecording) {
            stopListeningForeground()
            listener.safe { error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY) }
            return
        }

        listener.safe { readyForSpeech(Bundle()) }
        startRecording(listener)
    }

    override fun onStopListening(listener: Callback) {
        Log.d(TAG, "onStopListening")
        val samples = finishRecording()
        if (samples == null || samples.isEmpty()) {
            stopListeningForeground()
            listener.safe { error(SpeechRecognizer.ERROR_SPEECH_TIMEOUT) }
            return
        }
        transcribeAsync(samples, listener)
    }

    override fun onCancel(listener: Callback) {
        Log.d(TAG, "onCancel")
        activeJob?.cancel()
        stopListeningInternal()
        stopListeningForeground()
    }

    // -------------------------------------------------------------------------
    // Foreground service
    // -------------------------------------------------------------------------

    private fun startListeningForeground() {
        val notification = NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Listening…")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // API 30+: MICROPHONE type grants RECORD_AUDIO AppOps access for background
                // recording. On API 34+, eligible state is required; it is satisfied when the
                // system binds us with BIND_INCLUDE_CAPABILITIES (which happens when we are the
                // device's configured default recognizer) and the calling app is in the foreground.
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIF_ID, notification)
            }
            Log.d(TAG, "startForeground succeeded")
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed: ${e.message}")
        }
    }

    private fun stopListeningForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    // -------------------------------------------------------------------------
    // Audio recording
    // -------------------------------------------------------------------------

    private fun startRecording(listener: Callback) {
        val minBuf = AudioRecord.getMinBufferSize(
            REC_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf == AudioRecord.ERROR_BAD_VALUE || minBuf == AudioRecord.ERROR) {
            stopListeningForeground()
            listener.safe { error(SpeechRecognizer.ERROR_AUDIO) }
            return
        }

        val bufSize = maxOf(minBuf, REC_SAMPLE_RATE * 2)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            REC_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufSize
        )

        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            stopListeningForeground()
            listener.safe { error(SpeechRecognizer.ERROR_AUDIO) }
            return
        }

        audioRecord = rec
        isRecording = true

        activeJob = scope.launch {
            val maxSamples = REC_SAMPLE_RATE * MAX_RECORD_SECONDS
            val buffer = ShortArray(maxSamples)
            val chunk  = ShortArray(1024)
            var total  = 0

            rec.startRecording()
            listener.safe { beginningOfSpeech() }
            Log.d(TAG, "Recording started")

            while (isRecording && total < maxSamples) {
                val read = rec.read(chunk, 0, chunk.size.coerceAtMost(maxSamples - total))
                if (read < 0) break
                chunk.copyInto(buffer, total, 0, read)
                total += read

                val rms = computeRms(chunk, read)
                try {
                    listener.rmsChanged(rms)
                } catch (e: RemoteException) {
                    Log.w(TAG, "Client disconnected during recording")
                    isRecording = false
                    break
                }
            }

            // Use runCatching: stopListeningInternal() may have already stopped/released
            // the recorder (e.g. if onCancel fired concurrently), causing IllegalStateException.
            runCatching { rec.stop() }
            runCatching { rec.release() }
            audioRecord = null
            recordedSamples = if (total > 0) buffer.copyOf(total) else null
            isRecording = false
            Log.d(TAG, "Recording stopped, $total samples")
        }
    }

    private fun finishRecording(): ShortArray? {
        isRecording = false
        var waited = 0
        while (audioRecord != null && waited < 500) {
            Thread.sleep(10); waited += 10
        }
        return recordedSamples.also { recordedSamples = null }
    }

    private fun stopListeningInternal() {
        isRecording = false
        audioRecord?.let { rec ->
            runCatching { rec.stop() }
            runCatching { rec.release() }
        }
        audioRecord = null
        recordedSamples = null
    }

    // -------------------------------------------------------------------------
    // ASR inference
    // -------------------------------------------------------------------------

    private fun transcribeAsync(samples: ShortArray, listener: Callback) {
        activeJob = scope.launch {
            listener.safe { endOfSpeech() }

            synchronized(initLock) {
                if (asr == null) initLock.wait(5000L)
            }

            val engine = asr
            if (engine == null) {
                stopListeningForeground()
                listener.safe { error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY) }
                return@launch
            }

            try {
                val text = engine.transcribe(samples, REC_SAMPLE_RATE)
                if (text.isBlank()) {
                    stopListeningForeground()
                    listener.safe { error(SpeechRecognizer.ERROR_NO_MATCH) }
                    return@launch
                }

                val bundle = Bundle()
                bundle.putStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION,
                    arrayListOf(text)
                )
                bundle.putFloatArray(
                    SpeechRecognizer.CONFIDENCE_SCORES,
                    floatArrayOf(1.0f)
                )
                stopListeningForeground()
                listener.safe { results(bundle) }
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                stopListeningForeground()
                listener.safe { error(SpeechRecognizer.ERROR_CLIENT) }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private inline fun Callback.safe(block: Callback.() -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "Callback IPC failed: ${e.message}")
        }
    }

    private fun computeRms(buf: ShortArray, len: Int): Float {
        var sum = 0.0
        for (i in 0 until len) {
            val s = buf[i] / 32768.0
            sum += s * s
        }
        val rms = Math.sqrt(sum / len.coerceAtLeast(1))
        val db = 20.0 * Math.log10(rms.coerceAtLeast(1e-8))
        return db.toFloat().coerceIn(-160f, 0f)
    }
}
