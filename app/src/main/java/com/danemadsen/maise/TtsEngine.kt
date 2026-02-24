package com.danemadsen.maise

import android.content.Context

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
