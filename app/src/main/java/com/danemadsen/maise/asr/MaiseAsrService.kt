package com.danemadsen.maise.asr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import com.danemadsen.maise.MainActivity
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
 * Foreground service strategy — why MICROPHONE type must be established from MainActivity:
 *
 * On Android 14+ (targetSDK ≥ 34), RECORD_AUDIO is a while-in-use permission. The only FGS
 * type that grants background RECORD_AUDIO access is FOREGROUND_SERVICE_TYPE_MICROPHONE. Other
 * types (DATA_SYNC, SHORT_SERVICE, etc.) do NOT satisfy this AppOps requirement.
 *
 * Starting MICROPHONE FGS requires "eligible state". The AOSP eligible state check examines the
 * ServiceRecord's `mAllowWhileInUsePermissionInFgs` flag. This flag is set the FIRST time
 * startForeground(MICROPHONE) is called with eligible state (process state ≤ PROCESS_STATE_TOP,
 * i.e., a visible activity). Once the flag is true it is NEVER re-evaluated — subsequent
 * startForeground(MICROPHONE) calls just update the notification without re-checking.
 *
 * Therefore the strategy is:
 *  1. MainActivity calls startForegroundService(AsrService) while the activity is visible.
 *     This gives the service "eligible state" and onStartCommand() calls
 *     startForeground(MICROPHONE) successfully, setting mAllowWhileInUsePermissionInFgs = true.
 *  2. The MICROPHONE foreground is kept running forever (stopForeground is never called between
 *     sessions). Between sessions the notification says "Ready"; during a session it says
 *     "Listening…". This is just a notification content update — no eligible state re-check.
 *  3. The RecognitionService base class's RECORD_AUDIO AppOps data-delivery check (run after
 *     onStartListening() returns) passes because the service is MICROPHONE foreground.
 *  4. On START_STICKY restarts (after a process kill), the service has no visible activity.
 *     startForeground(MICROPHONE) fails → falls back to DATA_SYNC with a "tap to reactivate"
 *     notification. The user opens Maise once to re-establish MICROPHONE foreground.
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
        // Self-start so the service is in "started" state. startForeground() requires the
        // service to be started (not just bound). MainActivity also calls
        // startForegroundService(), but this self-start covers the case where the service is
        // created via binding before MainActivity has been opened.
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
        // Attempt MICROPHONE foreground. Succeeds when called from MainActivity (visible
        // activity = eligible state). Sets mAllowWhileInUsePermissionInFgs = true in the
        // system's ServiceRecord, so no eligible state re-check is needed for future calls.
        // Falls back to DATA_SYNC with a tap-to-open notification on START_STICKY restarts.
        Log.d(TAG, "onStartCommand (intent=${if (intent == null) "null/restart" else "explicit"})")
        tryMicrophoneForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        stopListeningInternal()
        asr?.close()
        asr = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    // -------------------------------------------------------------------------
    // RecognitionService callbacks
    // -------------------------------------------------------------------------

    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        Log.d(TAG, "onStartListening")

        // Update foreground notification to "Listening…". Because mAllowWhileInUsePermissionInFgs
        // was already set to true by onStartCommand() (established from MainActivity), the system
        // skips the eligible state check and just updates the notification. This is the call that
        // puts the service in MICROPHONE foreground so the RECORD_AUDIO AppOps data-delivery
        // check (run by the base class after this method returns) passes.
        setMicrophoneNotification("Listening\u2026")

        if (isRecording) {
            setMicrophoneNotification("Ready")
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
            setMicrophoneNotification("Ready")
            listener.safe { error(SpeechRecognizer.ERROR_SPEECH_TIMEOUT) }
            return
        }
        transcribeAsync(samples, listener)
    }

    override fun onCancel(listener: Callback) {
        Log.d(TAG, "onCancel")
        activeJob?.cancel()
        stopListeningInternal()
        setMicrophoneNotification("Ready")
    }

    // -------------------------------------------------------------------------
    // Foreground service helpers
    // -------------------------------------------------------------------------

    /**
     * Called once from onStartCommand(). Tries MICROPHONE type (eligible when invoked from
     * a visible activity). Falls back to DATA_SYNC if eligible state is not met (restart case).
     */
    private fun tryMicrophoneForeground() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(NOTIF_ID, buildNotification("Ready"), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIF_ID, buildNotification("Ready"))
            }
            Log.d(TAG, "MICROPHONE foreground established")
        } catch (e: Exception) {
            Log.w(TAG, "MICROPHONE foreground failed (eligible state not met — open Maise to reactivate): ${e.message}")
            // DATA_SYNC fallback: keeps the process alive. The notification prompts the user to
            // open Maise so startForegroundService() from MainActivity re-establishes MICROPHONE.
            try {
                val pendingIntent = PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val notification = NotificationCompat.Builder(this, NOTIF_CHANNEL)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText("Tap to enable voice recognition")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentIntent(pendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(NOTIF_ID, notification)
                }
                Log.d(TAG, "DATA_SYNC fallback foreground established")
            } catch (e2: Exception) {
                Log.w(TAG, "DATA_SYNC fallback also failed: ${e2.message}")
            }
        }
    }

    /**
     * Updates the MICROPHONE foreground notification. Since mAllowWhileInUsePermissionInFgs
     * is already true (set by the initial eligible-state call in onStartCommand), the system
     * skips the eligible state check and treats this as a plain notification update.
     */
    private fun setMicrophoneNotification(text: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(NOTIF_ID, buildNotification(text), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIF_ID, buildNotification(text))
            }
        } catch (e: Exception) {
            Log.w(TAG, "setMicrophoneNotification($text) failed: ${e.message}")
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
            setMicrophoneNotification("Ready")
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
            setMicrophoneNotification("Ready")
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
                setMicrophoneNotification("Ready")
                listener.safe { error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY) }
                return@launch
            }

            try {
                val text = engine.transcribe(samples, REC_SAMPLE_RATE)
                if (text.isBlank()) {
                    setMicrophoneNotification("Ready")
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
                setMicrophoneNotification("Ready")
                listener.safe { results(bundle) }
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                setMicrophoneNotification("Ready")
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
