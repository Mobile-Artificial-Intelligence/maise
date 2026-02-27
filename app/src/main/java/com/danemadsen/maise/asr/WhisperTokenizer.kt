package com.danemadsen.maise.asr

import android.content.Context
import android.util.Log
import org.json.JSONObject

private const val TAG = "WhisperTokenizer"

// Token IDs for distil-whisper (same as whisper-small, GPT-2 base vocab + special tokens)
const val WHISPER_TOKEN_EOT           = 50256  // <|endoftext|>
const val WHISPER_TOKEN_SOT           = 50257  // <|startoftranscript|>
const val WHISPER_TOKEN_EN            = 50258  // <|en|>
const val WHISPER_TOKEN_TRANSCRIBE    = 50358  // <|transcribe|>
const val WHISPER_TOKEN_NOTIMESTAMPS  = 50362  // <|notimestamps|>

/**
 * Whisper tokenizer for decoding token IDs to text.
 *
 * Loads vocab.json from the "whisper" asset pack. The file maps token string → token ID.
 * Uses the GPT-2 byte-level BPE unicode→byte mapping for decoding.
 *
 * Only decoding (token IDs → text) is implemented, since encoding is not needed
 * for the ASR inference pipeline (encoder input is mel spectrogram, not tokens).
 *
 * Required assets (place in whisper/src/main/assets/):
 *   - vocab.json  (from https://huggingface.co/distil-whisper/distil-small.en)
 */
class WhisperTokenizer(context: Context) {

    // Reverse vocab: token_id → token_string
    private val idToToken: Map<Int, String>

    // GPT-2 byte-level BPE reverse map: unicode char in token strings → raw byte
    private val unicodeToByte: Map<Char, Byte> = buildUnicodeToByte()

    init {
        idToToken = loadVocab(context)
        Log.i(TAG, "Loaded ${idToToken.size} tokens")
    }

    /**
     * Decode a list of token IDs to a UTF-8 string.
     * Skips any token IDs >= [WHISPER_TOKEN_EOT] (special tokens).
     */
    fun decode(tokenIds: List<Int>): String {
        val bytes = mutableListOf<Byte>()
        for (id in tokenIds) {
            if (id >= WHISPER_TOKEN_EOT) continue  // skip special tokens
            val tokenStr = idToToken[id] ?: continue
            for (ch in tokenStr) {
                val b = unicodeToByte[ch]
                if (b != null) bytes.add(b) else {
                    // Fallback: encode char as UTF-8 bytes
                    ch.toString().toByteArray(Charsets.UTF_8).forEach { bytes.add(it) }
                }
            }
        }
        return bytes.toByteArray().toString(Charsets.UTF_8)
    }

    private fun loadVocab(context: Context): Map<Int, String> {
        val json = context.assets.open("vocab.json").bufferedReader().use { it.readText() }
        val obj = JSONObject(json)
        val map = HashMap<Int, String>(obj.length())
        val keys = obj.keys()
        while (keys.hasNext()) {
            val token = keys.next()
            map[obj.getInt(token)] = token
        }
        return map
    }

    companion object {
        /**
         * Build the inverse of GPT-2's bytes_to_unicode() mapping.
         * Maps each unicode character found in BPE token strings back to its raw byte value.
         *
         * GPT-2 maps "printable" bytes to themselves and the rest to codepoints starting at U+0100.
         * The 188 "natural" bytes: '!' (33) – '~' (126), '¡' (161) – '¬' (172), '®' (174) – 'ÿ' (255).
         * The 68 "unnatural" bytes (0–32, 127, 128–160, 173) map to U+0100 onward.
         */
        fun buildUnicodeToByte(): Map<Char, Byte> {
            // Build the forward map: byte → unicode char
            val bs = mutableListOf<Int>()
            for (b in 33..126)  bs.add(b)   // '!' to '~'
            for (b in 161..172) bs.add(b)   // '¡' to '¬'
            for (b in 174..255) bs.add(b)   // '®' to 'ÿ'

            val cs = bs.toMutableList()     // these bytes map to the same codepoint
            var n = 0
            for (b in 0..255) {
                if (b !in bs) {
                    bs.add(b)
                    cs.add(256 + n)         // unnatural bytes → U+0100 onward
                    n++
                }
            }

            // Invert: unicode char → byte
            val result = HashMap<Char, Byte>(256)
            for (i in bs.indices) {
                result[cs[i].toChar()] = bs[i].toByte()
            }
            return result
        }
    }
}
