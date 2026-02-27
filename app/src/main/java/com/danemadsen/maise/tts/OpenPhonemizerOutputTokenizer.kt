package com.danemadsen.maise.tts

/**
 * Character-level tokenizer for the Kokoro TTS model.
 *
 * Vocab derived from the model's tokenizer.json.
 * Unknown characters map to the pad/unk token (ID 0, "$").
 * encode() wraps the sequence: [0, ...ids..., 0]
 */
object OpenPhonemizerOutputTokenizer {

    private const val SPECIAL_ID = 0  // "$" — pad / unk / sequence wrapper

    // Full character → token ID mapping from tokenizer.json
    private val vocab: Map<String, Int> = mapOf(
        "$"  to 0,
        ";"  to 1,
        ":"  to 2,
        ","  to 3,
        "."  to 4,
        "!"  to 5,
        "?"  to 6,
        // IDs 7, 8 not in standard vocab
        "\u2014" to 9,   // em dash —
        "\u2026" to 10,  // ellipsis …
        "\""     to 11,
        "("      to 12,
        ")"      to 13,
        "\u201C" to 14,  // left double quotation mark "
        "\u201D" to 15,  // right double quotation mark "
        " "      to 16,
        "\u0303" to 17,  // combining tilde ̃
        "\u02A3" to 18,  // ʣ
        "\u02A5" to 19,  // ʥ
        "\u02A6" to 20,  // ʦ
        "\u02A8" to 21,  // ʨ
        "\u1D5D" to 22,  // ᵝ
        "\uAB67" to 23,  // ꭧ
        "A"      to 24,
        "I"      to 25,
        // 26-30 not listed
        "O"      to 31,
        // 32 not listed
        "Q"      to 33,
        // 34 not listed
        "S"      to 35,
        "T"      to 36,
        // 37-38 not listed
        "W"      to 39,
        // 40 not listed
        "Y"      to 41,
        "\u1D4A" to 42,  // ᵊ
        "a"      to 43,
        "b"      to 44,
        "c"      to 45,
        "d"      to 46,
        "e"      to 47,
        "f"      to 48,
        "g"      to 49,
        "h"      to 50,
        "i"      to 51,
        "j"      to 52,
        "k"      to 53,
        "l"      to 54,
        "m"      to 55,
        "n"      to 56,
        "o"      to 57,
        "p"      to 58,
        "q"      to 59,
        "r"      to 60,
        "s"      to 61,
        "t"      to 62,
        "u"      to 63,
        "v"      to 64,
        "w"      to 65,
        "x"      to 66,
        "y"      to 67,
        "z"      to 68,
        "\u0251" to 69,  // ɑ
        "\u0250" to 70,  // ɐ
        "\u0252" to 71,  // ɒ
        "\u00E6" to 72,  // æ
        // 73-74 not listed
        "\u03B2" to 75,  // β
        "\u0254" to 76,  // ɔ
        "\u0255" to 77,  // ɕ
        "\u00E7" to 78,  // ç
        // 79 not listed
        "\u0256" to 80,  // ɖ
        "\u00F0" to 81,  // ð
        "\u02A4" to 82,  // ʤ
        "\u0259" to 83,  // ə
        // 84 not listed
        "\u025A" to 85,  // ɚ
        "\u025B" to 86,  // ɛ
        "\u025C" to 87,  // ɜ
        // 88-89 not listed
        "\u025F" to 90,  // ɟ
        // 91 not listed
        "\u0261" to 92,  // ɡ
        // 93-98 not listed
        "\u0265" to 99,  // ɥ
        // 100 not listed
        "\u0268" to 101, // ɨ
        "\u026A" to 102, // ɪ
        "\u029D" to 103, // ʝ
        // 104-110 not listed
        "\u0270" to 111, // ɰ
        "\u014B" to 112, // ŋ
        "\u0273" to 113, // ɳ
        "\u0272" to 114, // ɲ
        "\u0274" to 115, // ɴ
        "\u00F8" to 116, // ø
        // 117 not listed
        "\u0278" to 118, // ɸ
        "\u03B8" to 119, // θ
        "\u0153" to 120, // œ
        // 121-122 not listed
        "\u0279" to 123, // ɹ
        // 124 not listed
        "\u027E" to 125, // ɾ
        "\u027B" to 126, // ɻ
        // 127 not listed
        "\u0281" to 128, // ʁ
        "\u027D" to 129, // ɽ
        "\u0282" to 130, // ʂ
        "\u0283" to 131, // ʃ
        "\u0288" to 132, // ʈ
        "\u02A7" to 133, // ʧ
        // 134 not listed
        "\u028A" to 135, // ʊ
        "\u028B" to 136, // ʋ
        // 137 not listed
        "\u028C" to 138, // ʌ
        "\u0263" to 139, // ɣ
        "\u0264" to 140, // ɤ
        // 141 not listed
        "\u03C7" to 142, // χ
        "\u028E" to 143, // ʎ
        // 144-146 not listed
        "\u0292" to 147, // ʒ
        // 148-155 not listed
        "\u02C8" to 156, // ˈ primary stress
        "\u02CC" to 157, // ˌ secondary stress
        "\u02D0" to 158, // ː long vowel
        // 159-161 not listed
        "\u02B0" to 162, // ʰ aspiration
        // 163 not listed
        "\u02B2" to 164, // ʲ palatalization
        // 165-168 not listed
        "\u2193" to 169, // ↓ downstep
        // 170 not listed
        "\u2192" to 171, // → level
        "\u2197" to 172, // ↗ rise
        "\u2198" to 173, // ↘ fall
        // 174-176 not listed
        "\u1D3B" to 177, // ᵻ
    )

    /**
     * Encode a phoneme string to token IDs, wrapped with special tokens.
     * Returns: [0, id1, id2, ..., idN, 0]
     */
    fun encode(phonemes: String): IntArray {
        val ids = ArrayList<Int>(phonemes.length + 2)
        ids.add(SPECIAL_ID)
        for (ch in phonemes) {
            ids.add(vocab[ch.toString()] ?: SPECIAL_ID)
        }
        ids.add(SPECIAL_ID)
        return ids.toIntArray()
    }
}
