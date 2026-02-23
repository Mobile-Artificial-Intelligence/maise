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

class MaiseTtsService : TextToSpeechService() {

    @Volatile
    private var tts: KokoroTTS? = null

    @Volatile
    private var isStopped = false

    override fun onCreate() {
        super.onCreate()
        // Initialize TTS engine in background; first synthesis will block if not ready
        Thread {
            try {
                tts = KokoroTTS(applicationContext)
                Log.i(TAG, "KokoroTTS initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize KokoroTTS", e)
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

    override fun onIsLanguageAvailable(lang: String, country: String, variant: String): Int {
        // Android passes ISO 639-2 (3-letter) language codes and ISO 3166 (3-letter) country codes.
        // locale.language is 2-letter, so we must also check locale.isO3Language for the match.
        fun localeMatchesLang(locale: java.util.Locale): Boolean =
            locale.language.equals(lang, ignoreCase = true) ||
            runCatching { locale.isO3Language }.getOrNull()?.equals(lang, ignoreCase = true) == true

        fun localeMatchesCountry(locale: java.util.Locale): Boolean =
            locale.country.equals(country, ignoreCase = true) ||
            runCatching { locale.isO3Country }.getOrNull()?.equals(country, ignoreCase = true) == true

        // First try to find an exact language+country match
        val exactMatch = country.isNotEmpty() &&
            ALL_VOICES.any { localeMatchesLang(it.locale) && localeMatchesCountry(it.locale) }
        if (exactMatch) {
            return if (variant.isNotEmpty()) TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
                   else TextToSpeech.LANG_COUNTRY_AVAILABLE
        }

        // Fall back to language-only match. Return LANG_COUNTRY_AVAILABLE rather than
        // LANG_AVAILABLE so Settings enables "Listen to an example" on all Android OEMs
        // (some check > 0 rather than >= 0).
        val langMatch = ALL_VOICES.any { localeMatchesLang(it.locale) }
        return if (langMatch) TextToSpeech.LANG_COUNTRY_AVAILABLE
               else TextToSpeech.LANG_NOT_SUPPORTED
    }

    override fun onLoadLanguage(lang: String, country: String, variant: String): Int =
        onIsLanguageAvailable(lang, country, variant)

    override fun onGetLanguage(): Array<String> = arrayOf("eng", "USA", "")

    override fun onGetDefaultVoiceNameFor(lang: String, country: String, variant: String): String {
        fun localeMatchesLang(locale: java.util.Locale): Boolean =
            locale.language.equals(lang, ignoreCase = true) ||
            runCatching { locale.isO3Language }.getOrNull()?.equals(lang, ignoreCase = true) == true

        fun localeMatchesCountry(locale: java.util.Locale): Boolean =
            locale.country.equals(country, ignoreCase = true) ||
            runCatching { locale.isO3Country }.getOrNull()?.equals(country, ignoreCase = true) == true

        // Prefer a voice that matches both language and country (e.g. en-GB for British users)
        val bestMatch = ALL_VOICES.firstOrNull { localeMatchesLang(it.locale) && localeMatchesCountry(it.locale) }
            ?: ALL_VOICES.firstOrNull { localeMatchesLang(it.locale) }
        return bestMatch?.id ?: DEFAULT_VOICE_ID
    }

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

    // -------------------------------------------------------------------------
    // Synthesis
    // -------------------------------------------------------------------------

    override fun onStop() {
        isStopped = true
    }

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        isStopped = false

        // Resolve voice: prefer the voice requested by caller, fall back to pref, then default
        val requestedVoice = request.params?.getString("voiceName")
            ?: getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(PREF_VOICE, DEFAULT_VOICE_ID)
            ?: DEFAULT_VOICE_ID
        val voiceId = if (findVoiceById(requestedVoice) != null) requestedVoice else DEFAULT_VOICE_ID

        val text = request.charSequenceText?.toString()?.takeIf { it.isNotBlank() }
            ?: run { callback.done(); return }

        // Wait for engine if still loading (up to 30 s)
        val deadline = System.currentTimeMillis() + 30_000L
        while (tts == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
        }

        val engine = tts ?: run {
            Log.e(TAG, "TTS engine not ready")
            callback.error()
            return
        }

        if (isStopped) { callback.done(); return }

        try {
            val speed = request.speechRate / 100f  // SpeechRate is 0–200 (100 = normal)
            val pcm = engine.synthesize(text, voiceId, speed.coerceIn(0.5f, 2.0f))

            if (isStopped) { callback.done(); return }

            callback.start(SAMPLE_RATE, AudioFormat.ENCODING_PCM_16BIT, 1)

            // Feed audio in chunks
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
                callback.audioAvailable(buf, 0, byteCount)
                offset = end
            }

            callback.done()
        } catch (e: Exception) {
            Log.e(TAG, "Synthesis failed", e)
            callback.error()
        }
    }
}
