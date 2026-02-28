package com.danemadsen.maise.asr

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.danemadsen.maise.R
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
 * Clients bind via [SpeechRecognizer] as normal. The service:
 *  1. Starts recording on [onStartListening]
 *  2. Stops recording and transcribes on [onStopListening]
 *  3. Returns results via [Callback.results]
 *
 * The RECORD_AUDIO permission must be granted to this app by the user (e.g. through
 * [MainActivity]) before speech recognition will work.
 */
class MaiseAsrService : RecognitionService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(NOTIF_CHANNEL, "Speech Recognition", NotificationManager.IMPORTANCE_LOW)
                .apply { setSound(null, null) }
        )
        // Initialise the ASR engine on a background thread so service start is fast
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

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        stopRecording()
        asr?.close()
        asr = null
    }

    // -------------------------------------------------------------------------
    // RecognitionService callbacks
    // -------------------------------------------------------------------------

    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        Log.d(TAG, "onStartListening")

        // Promote to foreground before any mic access — required on Android 9+ for
        // background services, and mandatory on Android 14+ with FOREGROUND_SERVICE_MICROPHONE.
        startForegroundCompat()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            stopForegroundCompat()
            listener.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            return
        }

        if (isRecording) {
            stopForegroundCompat()
            listener.error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
            return
        }

        listener.readyForSpeech(Bundle())
        startRecording(listener)
    }

    override fun onStopListening(listener: Callback) {
        Log.d(TAG, "onStopListening")
        val samples = finishRecording()
        if (samples == null || samples.isEmpty()) {
            stopForegroundCompat()
            listener.error(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
            return
        }
        transcribeAsync(samples, listener)
    }

    override fun onCancel(listener: Callback) {
        Log.d(TAG, "onCancel")
        activeJob?.cancel()
        stopRecording()
        stopForegroundCompat()
    }

    // -------------------------------------------------------------------------
    // Foreground service helpers
    // -------------------------------------------------------------------------

    private fun startForegroundCompat() {
        val notification = NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Listening…")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed", e)
        }
    }

    private fun stopForegroundCompat() {
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
            listener.error(SpeechRecognizer.ERROR_AUDIO)
            return
        }

        val bufSize = maxOf(minBuf, REC_SAMPLE_RATE * 2)   // at least 1 s buffer
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            REC_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufSize
        )

        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            listener.error(SpeechRecognizer.ERROR_AUDIO)
            return
        }

        audioRecord = rec
        isRecording = true

        activeJob = scope.launch {
            val maxSamples = REC_SAMPLE_RATE * MAX_RECORD_SECONDS
            val buffer     = ShortArray(maxSamples)
            val chunk      = ShortArray(1024)
            var total      = 0

            rec.startRecording()
            listener.beginningOfSpeech()
            Log.d(TAG, "Recording started")

            while (isRecording && total < maxSamples) {
                val read = rec.read(chunk, 0, chunk.size.coerceAtMost(maxSamples - total))
                if (read < 0) break
                chunk.copyInto(buffer, total, 0, read)
                total += read

                // Notify RMS level to keep the client UI alive
                val rms = computeRms(chunk, read)
                listener.rmsChanged(rms)
            }

            rec.stop()
            rec.release()
            audioRecord = null

            recordedSamples = if (total > 0) buffer.copyOf(total) else null
            isRecording = false
            Log.d(TAG, "Recording stopped, ${total} samples")
        }
    }

    /** Stop recording and return the captured samples (or null). */
    private fun finishRecording(): ShortArray? {
        isRecording = false
        // Wait briefly for the recording coroutine to exit
        var waited = 0
        while (audioRecord != null && waited < 500) {
            Thread.sleep(10); waited += 10
        }
        return recordedSamples.also { recordedSamples = null }
    }

    private fun stopRecording() {
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
            listener.endOfSpeech()

            // Wait up to 5 s for the engine to initialise (handles cold-start)
            synchronized(initLock) {
                if (asr == null) initLock.wait(5000L)
            }

            val engine = asr
            if (engine == null) {
                stopForegroundCompat()
                listener.error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
                return@launch
            }

            try {
                val text = engine.transcribe(samples, REC_SAMPLE_RATE)
                if (text.isBlank()) {
                    stopForegroundCompat()
                    listener.error(SpeechRecognizer.ERROR_NO_MATCH)
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
                stopForegroundCompat()
                listener.results(bundle)
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                stopForegroundCompat()
                listener.error(SpeechRecognizer.ERROR_CLIENT)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private fun computeRms(buf: ShortArray, len: Int): Float {
        var sum = 0.0
        for (i in 0 until len) {
            val s = buf[i] / 32768.0
            sum += s * s
        }
        val rms = Math.sqrt(sum / len.coerceAtLeast(1))
        // Convert to dBFS, clamp to [-160, 0]
        val db = 20.0 * Math.log10(rms.coerceAtLeast(1e-8))
        return db.toFloat().coerceIn(-160f, 0f)
    }
}
