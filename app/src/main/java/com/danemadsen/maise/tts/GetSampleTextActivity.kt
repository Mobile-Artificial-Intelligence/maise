package com.danemadsen.maise.tts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log

private const val TAG = "GetSampleTextActivity"

/**
 * Headless activity handling android.speech.tts.engine.GET_SAMPLE_TEXT.
 *
 * System Settings ("Text-to-speech output") launches this on every load of its
 * TTS screen and on every engine/locale change to prefetch the "Listen to an
 * example" text. Without a handler, Settings logs an ActivityNotFoundException
 * and its preview button silently plays nothing.
 *
 * Settings passes the locale via the hidden keys "language"/"country"/"variant"
 * (Engine.KEY_PARAM_* — there are no public constants) and accepts the result
 * only when the result code is TextToSpeech.LANG_AVAILABLE (NOT RESULT_OK),
 * with the sentence in Engine.EXTRA_SAMPLE_TEXT. If we can't serve the
 * requested locale, we cancel and let Settings fall back to its own canned
 * string rather than speak English text through a non-English voice.
 */
class GetSampleTextActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val language = intent?.getStringExtra("language")?.trim()?.lowercase()
        val country = intent?.getStringExtra("country")?.trim()?.uppercase()

        val sample = language?.let { SampleTexts.forLocale(it, country) }
        if (sample != null) {
            Log.d(TAG, "Sample for $language-$country: $sample")
            setResult(TextToSpeech.LANG_AVAILABLE, Intent()
                .putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, sample))
        } else {
            Log.d(TAG, "No sample for language='$language' country='$country'")
            setResult(RESULT_CANCELED)
        }
        finish()
    }
}

/**
 * One short sentence per shipped locale, in its native script.
 *
 * Deliberately a hardcoded Kotlin map and NOT per-locale strings resources:
 * Android resolves string resources by the phone's system locale, not the
 * requested TTS locale — a values-ja sentence would never load on an
 * English-locale phone previewing the ja-JP voice, which is exactly the case
 * this activity exists to fix.
 */
object SampleTexts {

    private val byTag = mapOf(
        "en-us" to "This is an example of speech synthesis in English.",
        "en-gb" to "This is an example of British English speech synthesis.",
        "de-de" to "Dies ist ein Beispiel für deutschsprachige Sprachsynthese.",
        "fr-fr" to "Voici un exemple de synthèse vocale en français.",
        "el-gr" to "Αυτό είναι ένα παράδειγμα σύνθεσης ελληνικής ομιλίας.",
        "it-it" to "Questo è un esempio di sintesi vocale in italiano.",
        "ja-jp" to "これは日本語音声合成のサンプルです。",
        "pt-br" to "Este é um exemplo de síntese de voz em português.",
        "zh-cn" to "这是中文语音合成的示例。",
    )

    /**
     * Exact lang-country match, else that language's first entry (e.g. "en"+"AU"
     * falls back to the generic English sentence), else null.
     */
    fun forLocale(language: String, country: String?): String? {
        val lang = language.lowercase()
        if (!country.isNullOrEmpty()) {
            byTag["$lang-${country.lowercase()}"]?.let { return it }
        }
        return byTag.entries.firstOrNull { it.key.startsWith("$lang-") }?.value
    }
}