package com.danemadsen.maise.asr

import android.app.Notification
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
 * Foreground service strategy (two-phase):
 *
 * 1. DATA_SYNC foreground (always-on):
 *    onStartCommand() enters FOREGROUND_SERVICE_TYPE_DATA_SYNC immediately. DATA_SYNC has NO
 *    "eligible state" restriction on API 29–34, so it succeeds regardless of whether the app
 *    has a visible activity. This keeps the service continuously foreground between sessions.
 *
 * 2. MICROPHONE foreground (during recognition only):
 *    onStartListening() upgrades to FOREGROUND_SERVICE_TYPE_MICROPHONE. This is the only type
 *    that satisfies the RECORD_AUDIO AppOps data-delivery check that RecognitionService runs
 *    immediately after onStartListening() returns. On API 34+, MICROPHONE requires "eligible
 *    state"; that condition is met because DATA_SYNC is already running (eligible state
 *    condition: "another non-shortService foreground service is already running in this app").
 *    After the session ends, we downgrade back to DATA_SYNC.
 *
 * Why the service must be pre-started from MainActivity:
 *    startForeground() requires the service to be in "started" state (via startService).
 *    We call startService from MainActivity.onCreate() with START_STICKY so the service
 *    survives independently of the bound client.
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
        // Enter DATA_SYNC foreground immediately. DATA_SYNC has no "eligible state" restriction,
        // so this succeeds on any restart (START_STICKY or initial start from MainActivity).
        // This persistent FGS is what satisfies the eligible state check for MICROPHONE type
        // when onStartListening() is called later.
        enterIdleForeground()
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
        Log.d(TAG, "onStartListening")

        // Upgrade from DATA_SYNC → MICROPHONE. Eligible state is satisfied because
        // DATA_SYNC is already running (onStartCommand always enters it first).
        // MICROPHONE type is the only type that passes RecognitionService's
        // RECORD_AUDIO AppOps data-delivery check that runs after this method returns.
        enterListeningForeground()

        if (isRecording) {
            enterIdleForeground()
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
            enterIdleForeground()
            listener.safe { error(SpeechRecognizer.ERROR_SPEECH_TIMEOUT) }
            return
        }
        transcribeAsync(samples, listener)
    }

    override fun onCancel(listener: Callback) {
        Log.d(TAG, "onCancel")
        activeJob?.cancel()
        stopListeningInternal()
        enterIdleForeground()
    }

    // -------------------------------------------------------------------------
    // Foreground service
    // -------------------------------------------------------------------------

    /** DATA_SYNC foreground — no eligible state restriction, runs between sessions. */
    private fun enterIdleForeground() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, buildNotification("Ready"), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIF_ID, buildNotification("Ready"))
            }
            Log.d(TAG, "enterIdleForeground succeeded")
        } catch (e: Exception) {
            Log.w(TAG, "enterIdleForeground failed: ${e.message}")
        }
    }

    /** MICROPHONE foreground — grants RECORD_AUDIO AppOps, used only during recognition. */
    private fun enterListeningForeground() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(NOTIF_ID, buildNotification("Listening…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIF_ID, buildNotification("Listening…"))
            }
            Log.d(TAG, "enterListeningForeground succeeded")
        } catch (e: Exception) {
            Log.w(TAG, "enterListeningForeground failed: ${e.message}")
        }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

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
            enterIdleForeground()
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
            enterIdleForeground()
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
                enterIdleForeground()
                listener.safe { error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY) }
                return@launch
            }

            try {
                val text = engine.transcribe(samples, REC_SAMPLE_RATE)
                if (text.isBlank()) {
                    enterIdleForeground()
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
                enterIdleForeground()
                listener.safe { results(bundle) }
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                enterIdleForeground()
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
