package com.danemadsen.maise.asr

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.danemadsen.maise.R
import com.konovalov.vad.webrtc.Vad
import com.konovalov.vad.webrtc.config.FrameSize
import com.konovalov.vad.webrtc.config.Mode
import com.konovalov.vad.webrtc.config.SampleRate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.isActive
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
private const val RMS_INTERVAL_MS   = 66L                   // ~15 level updates/s

// VadWebRTC debounces internally (isContinuousSpeech): speech latches after ~7
// consecutive speech frames, and unlatches only after 27 consecutive silent
// 30 ms frames — these values drive that state machine, not a raw classifier.
private const val VAD_SPEECH_MS     = 200
private const val VAD_SILENCE_MS    = 800
private const val NO_SPEECH_TIMEOUT_MS = 10_000L            // abort if speech never latches

/**
 * Android [RecognitionService] backed by distil-whisper/distil-small.en via ONNX Runtime.
 *
 * Ported from WhisperIMEplus (WhisperRecognitionService + Recorder) with our asset-based
 * WhisperASR substituted for their external-storage model loader.
 *
 * Package visibility is load-bearing: as the system RecognitionService we are
 * bound by arbitrary apps, and the framework's checkPermissionAndStartDataDelivery()
 * validates the caller via an AppOps proxy-op that applies package-visibility
 * filtering with OUR uid. A caller invisible to this process is hard-denied with
 * ERROR_INSUFFICIENT_PERMISSIONS (logcat: "RecognitionService: #startListening
 * received from a caller without permission android.permission.RECORD_AUDIO"),
 * onCancel fires, and onStartListening never records — even though the caller
 * holds RECORD_AUDIO. The manifest declares the launcher intent under <queries>
 * instead of QUERY_ALL_PACKAGES (Play restricts the latter): every launchable
 * caller is visible; launcher-less service-only callers are not. In-app use
 * always works (an app is visible to itself). Verified on Android 17, see the
 * manifest comment. Do not remove the <queries> element.
 *
 * Callers must also hold a granted [android.permission.RECORD_AUDIO], enforced
 * by the framework's checkPermissionAndStartDataDelivery() against the caller's
 * AttributionSource.
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

    // Lazy, not a field initializer: a Service's base context is attached only
    // after the constructor, so touching this at <init> time crashes with a
    // NullPointerException inside ContextWrapper.getApplicationContext().
    private val sounds by lazy { RecognitionSounds(this) }

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
        // MICROPHONE foreground, matching the manifest's foregroundServiceType. Requires
        // the app to be in an eligible state (e.g. visible) plus RECORD_AUDIO; both
        // failures are caught below so a background start degrades gracefully instead of
        // crashing. External binds keep working without the FGS: the caller-attribution
        // path (checkPermissionAndStartDataDelivery) allows the mic while a foreground
        // client is bound, verified on Android 17.
        try {
            val notification = NotificationCompat.Builder(this, NOTIF_CHANNEL)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Speech recognition ready")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSilent(true)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
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
            sounds.playError()
            listener.safe { error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) }
            return
        }

        if (isRecording) {
            listener.safe { error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY) }
            return
        }

        // Partials are opt-in via the documented EXTRA_PARTIAL_RESULTS extra —
        // callers that don't request them get exactly one final results().
        val wantPartials = recognizerIntent.getBooleanExtra(
            RecognizerIntent.EXTRA_PARTIAL_RESULTS, false
        )

        startRecordingWithVad(listener, wantPartials)
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

    // onStartListening verifies RECORD_AUDIO before calling this
    @SuppressLint("MissingPermission")
    private fun startRecordingWithVad(listener: Callback, wantPartials: Boolean) {
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
            sounds.playError()
            listener.safe { error(SpeechRecognizer.ERROR_AUDIO) }
            return
        }

        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            sounds.playError()
            listener.safe { error(SpeechRecognizer.ERROR_AUDIO) }
            return
        }

        isRecording = true

        activeJob = scope.launch {
            // Start cue must COMPLETE before the mic opens: VOICE_RECOGNITION has no
            // AEC, so a still-playing tone would be captured, falsely latch the
            // WebRTC VAD (its ~200 ms speech debounce trips on this ~410 ms tone)
            // and contaminate the transcript. Cancellation (onCancel) must also stop
            // the tone instantly and leave the mic closed.
            try {
                sounds.playStart()
            } catch (e: CancellationException) {
                runCatching { rec.stop() }
                runCatching { rec.release() }
                throw e
            }

            val vad = Vad.builder()
                .setSampleRate(SampleRate.SAMPLE_RATE_16K)
                .setFrameSize(FrameSize.FRAME_SIZE_480)
                .setMode(Mode.VERY_AGGRESSIVE)
                .setSilenceDurationMs(VAD_SILENCE_MS)
                .setSpeechDurationMs(VAD_SPEECH_MS)
                .build()

            val output = ByteArrayOutputStream()
            val chunk  = ByteArray(VAD_FRAME_BYTES)
            var speechStarted = false
            var lastRmsAt = 0L
            // No-speech clock starts AFTER the cue so the 10 s budget isn't
            // consumed by the ~0.4 s tone.
            val startedAt = SystemClock.elapsedRealtime()

            rec.startRecording()
            listener.safe { readyForSpeech(Bundle()) }
            Log.d(TAG, "Recording started (VAD)")

            while (isRecording && output.size() < MAX_BYTES) {
                val read = rec.read(chunk, 0, chunk.size)
                if (read < 0) {
                    Log.w(TAG, "AudioRecord read error: $read")
                    break
                }
                output.write(chunk, 0, read)

                // Real audio level for the caller's UI, throttled to ~15 updates/s
                // (each dispatch is a Binder transaction into the caller's process).
                val now = SystemClock.elapsedRealtime()
                if (now - lastRmsAt >= RMS_INTERVAL_MS) {
                    lastRmsAt = now
                    listener.safe { rmsChanged(frameRmsDb(chunk, read)) }
                }

                // Abort if speech never latches — otherwise we'd record 30 s of
                // silence and hand Whisper a hallucination-prone clip.
                if (!speechStarted && now - startedAt >= NO_SPEECH_TIMEOUT_MS) {
                    Log.d(TAG, "VAD: no speech detected, timing out")
                    break
                }

                // VAD needs a full frame; on short reads the buffer tail is stale.
                if (read == VAD_FRAME_BYTES) {
                    if (vad.isSpeech(chunk)) {
                        if (!speechStarted) {
                            speechStarted = true
                            listener.safe { beginningOfSpeech() }
                        }
                    } else if (speechStarted) {
                        // VadWebRTC debounces internally: isSpeech() keeps returning
                        // true through gaps until ~27 consecutive silent 30 ms frames
                        // (~810 ms) elapse, so mid-sentence pauses are tolerated —
                        // reaching here means sustained end-of-utterance silence.
                        Log.d(TAG, "VAD: sustained silence after speech, stopping")
                        break
                    }
                }
            }

            runCatching { rec.stop() }
            runCatching { rec.release() }
            runCatching { vad.close() }
            isRecording = false

            Log.d(TAG, "Recording done, ${output.size()} bytes")

            // Speech never latched — report a timeout rather than transcribing noise.
            if (!speechStarted) {
                if (isActive) sounds.playError()
                listener.safe { error(SpeechRecognizer.ERROR_SPEECH_TIMEOUT) }
                return@launch
            }

            // Mirror WhisperIMEplus threshold: > 6400 bytes (~0.2 s at 16 kHz 16-bit)
            if (output.size() <= 6400) {
                if (isActive) sounds.playError()
                listener.safe { error(SpeechRecognizer.ERROR_SPEECH_TIMEOUT) }
                return@launch
            }

            // Usable capture ended (VAD silence, manual stop, or 30 s cap) — the mic
            // is closed, so the cue can't bleed into it. isActive keeps a cancelled
            // session (popover dismissed) from beeping after the fact.
            if (isActive) sounds.playStop()

            // Convert bytes → ShortArray for WhisperASR
            val bytes = output.toByteArray()
            val samples = ShortArray(bytes.size / 2)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)

            transcribeAsync(samples, listener, wantPartials)
        }
    }

    // -------------------------------------------------------------------------
    // ASR inference
    // -------------------------------------------------------------------------

    private fun transcribeAsync(samples: ShortArray, listener: Callback, wantPartials: Boolean) {
        // transcribeAsync is called from within activeJob's coroutine, so no new launch needed
        listener.safe { endOfSpeech() }

        // Wait for model if it hasn't finished loading yet
        synchronized(initLock) {
            if (asr == null) initLock.wait(10_000L)
        }

        val engine = asr
        if (engine == null) {
            sounds.playError()
            listener.safe { error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY) }
            return
        }

        try {
            // Stream word-boundary partials, but only to callers that opted in
            // via EXTRA_PARTIAL_RESULTS — unsolicited partials can be mistaken
            // for final text by apps that don't expect them.
            val onPartial: ((String) -> Unit)? = if (wantPartials) {
                { partial ->
                    listener.safe {
                        partialResults(Bundle().apply {
                            putStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(partial)
                            )
                        })
                    }
                }
            } else null
            val text = engine.transcribe(samples, SAMPLE_RATE, onPartial)
            Log.d(TAG, "Transcribed: \"$text\"")

            if (text.isBlank()) {
                sounds.playError()
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
            sounds.playError()
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

    /**
     * RMS level of a 16-bit little-endian PCM frame, in dBFS (0 = full scale,
     * roughly -60 for silence). Forwarded via [Callback.rmsChanged] so callers
     * can animate a level meter.
     */
    private fun frameRmsDb(chunk: ByteArray, length: Int): Float {
        var sumSquares = 0.0
        val sampleCount = length / 2
        for (i in 0 until sampleCount) {
            val lo = chunk[i * 2].toInt()
            val hi = chunk[i * 2 + 1].toInt()
            val sample = ((hi shl 8) or (lo and 0xFF)).toShort().toInt()
            sumSquares += (sample * sample).toDouble()
        }
        if (sampleCount == 0) return -60f
        val rms = kotlin.math.sqrt(sumSquares / sampleCount)
        if (rms < 1.0) return -60f
        return (20.0 * kotlin.math.log10(rms / 32768.0)).toFloat()
    }
}
