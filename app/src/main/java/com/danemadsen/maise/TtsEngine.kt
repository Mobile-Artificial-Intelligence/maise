package com.danemadsen.maise

import android.content.Context

/**
 * Split [text] into sentences so each one stays within the model's phoneme
 * token limit. Splits after . ! ? followed by whitespace or end-of-string,
 * keeping the punctuation attached to the preceding chunk.
 */
fun splitSentences(text: String): List<String> {
    val raw = text.split(Regex("(?<=[.!?])(?:\\s+|$)"))
    return raw.map { it.trim() }.filter { it.isNotEmpty() }
}

/**
 * Unified TTS engine that dispatches to either [KokoroTTS] or [KittenTTS]
 * depending on the engine suffix embedded in the voice ID:
 *   - "*-kokoro" → Kokoro TTS
 *   - "*-kitten" → Kitten TTS
 */
class TtsEngine(context: Context) {

    private val kokoro = KokoroTTS(context)
    private val kitten = KittenTTS(context)

    fun synthesize(text: String, voiceId: String, speed: Float = 1.0f): ShortArray {
        return if (voiceId.endsWith("-kitten")) {
            kitten.synthesize(text, voiceId, speed)
        } else {
            kokoro.synthesize(text, voiceId, speed)
        }
    }

    fun close() {
        kokoro.close()
        kitten.close()
    }
}
