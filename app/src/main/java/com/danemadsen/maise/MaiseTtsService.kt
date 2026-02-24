package com.danemadsen.maise

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import android.util.Log

private const val TAG = "MaiseTtsService"
private const val PREFS_NAME = "maise_tts_prefs"
private const val PREF_VOICE = "selected_voice"
private const val INIT_TIMEOUT_MS = 2500L

class MaiseTtsService : TextToSpeechService() {

    @Volatile
    private var tts: KokoroTTS? = null

    @Volatile
    private var isStopped = false

    private val initLock = Object()

    override fun onCreate() {
        super.onCreate()
        Thread {
            try {
                val engine = KokoroTTS(applicationContext)
                synchronized(initLock) {
                    tts = engine
                    initLock.notifyAll()
                }
                Log.i(TAG, "KokoroTTS initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize KokoroTTS", e)
                synchronized(initLock) {
                    initLock.notifyAll()
                }
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.close()
        tts = null
    }

    // -------------------------------------------------------------------------
    // Language support — Kokoro works best with English; report English as supported
    // -------------------------------------------------------------------------

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        return TextToSpeech.LANG_AVAILABLE
    }

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        return TextToSpeech.LANG_AVAILABLE
    }

    override fun onGetLanguage(): Array<String> = arrayOf("en", "US", "")

    override fun onGetDefaultVoiceNameFor(lang: String, country: String, variant: String): String = DEFAULT_VOICE_ID

    override fun onGetFeaturesForLanguage(lang: String, country: String, variant: String): Set<String> = emptySet()

    // -------------------------------------------------------------------------
    // Voice enumeration (Android 5.0+ API)
    // -------------------------------------------------------------------------

    override fun onGetVoices(): MutableList<Voice> {
        return ALL_VOICES.map { info ->
            Voice(
                info.id,
                info.locale,
                Voice.QUALITY_HIGH,
                Voice.LATENCY_HIGH,
                false,
                emptySet()
            )
        }.toMutableList()
    }

    override fun onIsValidVoiceName(voiceName: String): Int =
        if (findVoiceById(voiceName) != null) TextToSpeech.SUCCESS else TextToSpeech.ERROR

    override fun onLoadVoice(voiceName: String): Int =
        if (findVoiceById(voiceName) != null) TextToSpeech.SUCCESS else TextToSpeech.ERROR

    // -------------------------------------------------------------------------
    // Synthesis
    // -------------------------------------------------------------------------

    override fun onStop() {
        Log.d(TAG, "onStop() — cancelling synthesis")
        isStopped = true
    }

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        isStopped = false

        // Resolve voice: prefer request.voiceName (set by the framework when the caller uses
        // setVoice()), then SharedPreferences, then hard default.
        // Do NOT use request.params["voiceName"] — that key is unreliable across OEMs.
        val requestedVoice = request.voiceName
            ?.takeIf { it.isNotEmpty() }
            ?: getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(PREF_VOICE, DEFAULT_VOICE_ID)
            ?: DEFAULT_VOICE_ID
        val voiceId = if (findVoiceById(requestedVoice) != null) requestedVoice else DEFAULT_VOICE_ID

        val text = request.charSequenceText?.toString()?.takeIf { it.isNotBlank() }
            ?: run { callback.done(); return }

        val speed = (request.speechRate / 100f).coerceIn(0.5f, 2.0f)
        Log.d(TAG, "onSynthesizeText: voice=$voiceId, rate=$speed, textLen=${text.length}")

        // Signal audio start immediately so OEM Settings apps don't time out waiting for data.
        // This must happen before the init wait and before synthesis.
        callback.start(SAMPLE_RATE, AudioFormat.ENCODING_PCM_16BIT, 1)
        Log.d(TAG, "callback.start() called at ${System.currentTimeMillis()}")

        // Wait for engine using a proper lock rather than a spin-wait.
        synchronized(initLock) {
            if (tts == null) {
                Log.d(TAG, "Waiting for KokoroTTS init (max ${INIT_TIMEOUT_MS}ms)")
                initLock.wait(INIT_TIMEOUT_MS)
            }
        }

        val engine = tts ?: run {
            Log.e(TAG, "TTS engine not ready after ${INIT_TIMEOUT_MS}ms — returning error")
            callback.error()
            return
        }

        if (isStopped) {
            Log.d(TAG, "Stopped before synthesis — exiting early")
            callback.done()
            return
        }

        val sentences = splitSentences(text)
        Log.d(TAG, "Split into ${sentences.size} sentence(s)")

        try {
            var firstChunkEver = true
            for ((index, sentence) in sentences.withIndex()) {
                if (isStopped) {
                    Log.d(TAG, "Stopped before sentence $index — exiting early")
                    break
                }

                val synthStart = System.currentTimeMillis()
                val pcm = engine.synthesize(sentence, voiceId, speed)
                Log.d(TAG, "Sentence $index synthesized in ${System.currentTimeMillis() - synthStart}ms, ${pcm.size} samples")

                if (isStopped) break

                // Stream this sentence's audio immediately so playback can begin
                val chunkSamples = 4096
                var offset = 0
                while (offset < pcm.size && !isStopped) {
                    val end = minOf(offset + chunkSamples, pcm.size)
                    val byteCount = (end - offset) * 2
                    val buf = ByteArray(byteCount)
                    for (i in offset until end) {
                        val s = pcm[i]
                        val byteIdx = (i - offset) * 2
                        buf[byteIdx]     = (s.toInt() and 0xFF).toByte()
                        buf[byteIdx + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
                    }
                    if (firstChunkEver) {
                        Log.d(TAG, "First audioAvailable() at ${System.currentTimeMillis()}")
                        firstChunkEver = false
                    }
                    callback.audioAvailable(buf, 0, byteCount)
                    offset = end
                }
            }

            callback.done()
        } catch (e: Exception) {
            Log.e(TAG, "Synthesis failed", e)
            callback.error()
        }
    }

    /**
     * Split [text] into individual sentences so each one stays within the model's
     * phoneme token limit.  Splits after sentence-ending punctuation (. ! ?) that
     * is followed by whitespace or end-of-string.  Preserves the terminal
     * punctuation on each chunk and trims surrounding whitespace.
     * Empty chunks are discarded.
     */
    private fun splitSentences(text: String): List<String> {
        // Split after . ! ? when followed by whitespace or end-of-input.
        // The look-behind keeps the punctuation attached to the preceding sentence.
        val raw = text.split(Regex("(?<=[.!?])(?:\\s+|$)"))
        return raw.map { it.trim() }.filter { it.isNotEmpty() }
    }
}
