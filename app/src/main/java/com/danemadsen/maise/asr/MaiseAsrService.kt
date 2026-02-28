package com.danemadsen.maise.asr

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioManager
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
import com.konovalov.vad.webrtc.Vad
import com.konovalov.vad.webrtc.config.FrameSize
import com.konovalov.vad.webrtc.config.Mode
import com.konovalov.vad.webrtc.config.SampleRate
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "MaiseAsrService"
private const val NOTIF_CHANNEL = "maise_asr"
private const val NOTIF_ID = 1001

// Matches WhisperIMEplus — 30ms frame at 16 kHz, same as WebRTC VAD frame size
private const val VAD_FRAME_SAMPLES = 480
private const val VAD_FRAME_BYTES   = VAD_FRAME_SAMPLES * 2  // 960 bytes, 16-bit PCM

private const val SAMPLE_RATE       = 16000
private const val MAX_BYTES         = SAMPLE_RATE * 2 * 30  // 30 seconds

/**
 * Android [RecognitionService] backed by distil-whisper/distil-small.en via ONNX Runtime.
 *
 * Ported from WhisperIMEplus (WhisperRecognitionService + Recorder) with our asset-based
 * WhisperASR substituted for their external-storage model loader.
 *
 * The [android.permission.QUERY_ALL_PACKAGES] permission in the manifest is critical:
 * without it the RecognitionService base class cannot resolve the caller's attribution
 * chain in checkPermissionAndStartDataDelivery(), producing
 * "caller doesn't have permission: android.permission.RECORD_AUDIO" and firing onCancel.
 *
 * Recording uses WebRTC VAD for automatic end-of-speech detection (same as WhisperIMEplus).
 * onStopListening() provides a manual fallback stop.
 */
class MaiseAsrService : RecognitionService() {

    private val exceptionHandler = CoroutineExceptionHandler { _, t ->
        Log.e(TAG, "Uncaught coroutine exception", t)
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)

    @Volatile private var asr: WhisperASR? = null
    @Volatile private var isRecording = false
    @Volatile private var activeJob: Job? = null

    private val initLock = Object()

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        runCatching { startService(Intent(this, MaiseAsrService::class.java)) }

        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(NOTIF_CHANNEL, "Speech Recognition", NotificationManager.IMPORTANCE_LOW)
                .apply { setSound(null, null) }
        )

        // Pre-load the Whisper model so the first recognition session is fast
        scope.launch {
            try {
                val engine = WhisperASR(applicationContext)
                synchronized(initLock) { asr = engine; initLock.notifyAll() }
                Log.i(TAG, "WhisperASR ready")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialise WhisperASR", e)
                synchronized(initLock) { initLock.notifyAll() }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        // DATA_SYNC foreground keeps the process alive. Has no eligible-state restriction
        // so it always succeeds, even on START_STICKY restarts with no visible activity.
        // RECORD_AUDIO access is handled by QUERY_ALL_PACKAGES + the caller's attribution.
        try {
            val notification = NotificationCompat.Builder(this, NOTIF_CHANNEL)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Speech recognition ready")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSilent(true)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed: ${e.message}")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        isRecording = false
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
    // RecognitionService callbacks — matching WhisperIMEplus's pattern exactly
    // -------------------------------------------------------------------------

    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        Log.d(TAG, "onStartListening")

        // Mirror WhisperIMEplus: check permission ourselves before touching AudioRecord
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO not granted")
            listener.safe { error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) }
            return
        }

        if (isRecording) {
            listener.safe { error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY) }
            return
        }

        startRecordingWithVad(listener)
    }

    override fun onStopListening(listener: Callback) {
        Log.d(TAG, "onStopListening — manual stop")
        // Setting isRecording = false exits the VAD loop; the recording coroutine then
        // transcribes whatever was collected and calls results() via listener.
        isRecording = false
    }

    override fun onCancel(listener: Callback) {
        Log.d(TAG, "onCancel")
        activeJob?.cancel()
        isRecording = false
    }

    // -------------------------------------------------------------------------
    // VAD-based recording (ported from WhisperIMEplus Recorder.java)
    // -------------------------------------------------------------------------

    private fun startRecordingWithVad(listener: Callback) {
        val bufSize = maxOf(
            AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT),
            VAD_FRAME_BYTES
        )

        val rec = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufSize)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, bufSize
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord creation failed", e)
            listener.safe { error(SpeechRecognizer.ERROR_AUDIO) }
            return
        }

        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            listener.safe { error(SpeechRecognizer.ERROR_AUDIO) }
            return
        }

        isRecording = true

        activeJob = scope.launch {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true

            val vad = Vad.builder()
                .setSampleRate(SampleRate.SAMPLE_RATE_16K)
                .setFrameSize(FrameSize.FRAME_SIZE_480)
                .setMode(Mode.VERY_AGGRESSIVE)
                .setSilenceDurationMs(800)
                .setSpeechDurationMs(200)
                .build()

            val output = ByteArrayOutputStream()
            val chunk  = ByteArray(VAD_FRAME_BYTES)
            var speechStarted = false

            rec.startRecording()
            // Mirror WhisperIMEplus: readyForSpeech then beginningOfSpeech right at start
            listener.safe { readyForSpeech(Bundle()) }
            listener.safe { beginningOfSpeech() }
            Log.d(TAG, "Recording started (VAD)")

            while (isRecording && output.size() < MAX_BYTES) {
                val read = rec.read(chunk, 0, chunk.size)
                if (read < 0) {
                    Log.w(TAG, "AudioRecord read error: $read")
                    break
                }
                output.write(chunk, 0, read)

                if (vad.isSpeech(chunk)) {
                    if (!speechStarted) {
                        speechStarted = true
                        try { listener.rmsChanged(10f) } catch (_: RemoteException) {}
                    }
                } else if (speechStarted) {
                    // VAD detected silence after speech — auto-stop (same as WhisperIMEplus)
                    Log.d(TAG, "VAD: silence after speech, stopping")
                    break
                }
            }

            runCatching { rec.stop() }
            runCatching { rec.release() }
            runCatching { vad.close() }
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            isRecording = false

            Log.d(TAG, "Recording done, ${output.size()} bytes")

            // Mirror WhisperIMEplus threshold: > 6400 bytes (~0.2 s at 16 kHz 16-bit)
            if (output.size() <= 6400) {
                listener.safe { error(SpeechRecognizer.ERROR_SPEECH_TIMEOUT) }
                return@launch
            }

            // Convert bytes → ShortArray for WhisperASR
            val bytes = output.toByteArray()
            val samples = ShortArray(bytes.size / 2)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)

            transcribeAsync(samples, listener)
        }
    }

    // -------------------------------------------------------------------------
    // ASR inference
    // -------------------------------------------------------------------------

    private fun transcribeAsync(samples: ShortArray, listener: Callback) {
        // transcribeAsync is called from within activeJob's coroutine, so no new launch needed
        listener.safe { endOfSpeech() }

        // Wait for model if it hasn't finished loading yet
        synchronized(initLock) {
            if (asr == null) initLock.wait(10_000L)
        }

        val engine = asr
        if (engine == null) {
            listener.safe { error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY) }
            return
        }

        try {
            val text = engine.transcribe(samples, SAMPLE_RATE)
            Log.d(TAG, "Transcribed: \"$text\"")

            if (text.isBlank()) {
                listener.safe { error(SpeechRecognizer.ERROR_NO_MATCH) }
                return
            }

            val bundle = Bundle().apply {
                putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
                putFloatArray(SpeechRecognizer.CONFIDENCE_SCORES, floatArrayOf(1.0f))
            }
            listener.safe { results(bundle) }
        } catch (e: Exception) {
            Log.e(TAG, "Transcription failed", e)
            listener.safe { error(SpeechRecognizer.ERROR_CLIENT) }
        }
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private inline fun Callback.safe(block: Callback.() -> Unit) {
        try { block() } catch (e: Exception) {
            Log.w(TAG, "Callback IPC failed: ${e.message}")
        }
    }
}
