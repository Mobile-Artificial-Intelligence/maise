package com.danemadsen.maise

data class TokenizerConfig(
    val textSymbols: Map<String, Int>,
    val phonemeSymbols: Map<Int, String>,
    val charRepeats: Int,
    val languages: Set<String>,
)

internal val tokenizer = TokenizerConfig(
    textSymbols = mapOf(
        "_" to 0,
        "<en_us>" to 1,
        "<end>" to 2,
        "a" to 3,
        "b" to 4,
        "c" to 5,
        "d" to 6,
        "e" to 7,
        "f" to 8,
        "g" to 9,
        "h" to 10,
        "i" to 11,
        "j" to 12,
        "k" to 13,
        "l" to 14,
        "m" to 15,
        "n" to 16,
        "o" to 17,
        "p" to 18,
        "q" to 19,
        "r" to 20,
        "s" to 21,
        "t" to 22,
        "u" to 23,
        "v" to 24,
        "w" to 25,
        "x" to 26,
        "y" to 27,
        "z" to 28,
        "A" to 29,
        "B" to 30,
        "C" to 31,
        "D" to 32,
        "E" to 33,
        "F" to 34,
        "G" to 35,
        "H" to 36,
        "I" to 37,
        "J" to 38,
        "K" to 39,
        "L" to 40,
        "M" to 41,
        "N" to 42,
        "O" to 43,
        "P" to 44,
        "Q" to 45,
        "R" to 46,
        "S" to 47,
        "T" to 48,
        "U" to 49,
        "V" to 50,
        "W" to 51,
        "X" to 52,
        "Y" to 53,
        "Z" to 54,
    ),
    phonemeSymbols = mapOf(
        0 to "_",
        1 to "<en_us>",
        2 to "<end>",
        3 to "a",
        4 to "b",
        5 to "d",
        6 to "e",
        7 to "f",
        8 to "g",
        9 to "h",
        10 to "i",
        11 to "j",
        12 to "k",
        13 to "l",
        14 to "m",
        15 to "n",
        16 to "o",
        17 to "p",
        18 to "r",
        19 to "s",
        20 to "t",
        21 to "u",
        22 to "v",
        23 to "w",
        24 to "x",
        25 to "y",
        26 to "z",
        27 to "æ",
        28 to "ç",
        29 to "ð",
        30 to "ø",
        31 to "ŋ",
        32 to "œ",
        33 to "ɐ",
        34 to "ɑ",
        35 to "ɔ",
        36 to "ə",
        37 to "ɛ",
        38 to "ɜ",
        39 to "ɝ",
        40 to "ɹ",
        41 to "ɚ",
        42 to "ɡ",
        43 to "ɪ",
        44 to "ʁ",
        45 to "ʃ",
        46 to "ʊ",
        47 to "ʌ",
        48 to "ʏ",
        49 to "ʒ",
        50 to "ʔ",
        51 to "ˈ",
        52 to "ˌ",
        53 to "ː",
        54 to "̃",
        55 to "̍",
        56 to "̥",
        57 to "̩",
        58 to "̯",
        59 to "͡",
        60 to "θ",
        61 to "'",
        62 to "ɾ",
        63 to "ᵻ",
    ),
    charRepeats = 3,
    languages = setOf("en_us"),
)

fun encode(text: String): IntArray {
    // Mirror JS: text.toLowerCase().replace(/ /g, "_").trim()
    val cleaned = text
        .lowercase()
        .replace(" ", "_")
        .trim()

    val out = ArrayList<Int>(cleaned.length * tokenizer.charRepeats + 2)

    // Start token
    val langToken = tokenizer.textSymbols["<en_us>"]
        ?: error("Missing <en_us> in textSymbols")
    out.add(langToken)

    // Push repeated chars
    for (ch in cleaned) {
        val code = tokenizer.textSymbols[ch.toString()] ?: continue
        repeat(tokenizer.charRepeats) { out.add(code) }
    }

    // End token
    val endToken = tokenizer.textSymbols["<end>"]
        ?: error("Missing <end> in textSymbols")
    out.add(endToken)

    return out.toIntArray()
}

fun decode(ids: IntArray): String {
    val sb = StringBuilder()

    for (id in ids) {
        val ch = tokenizer.phonemeSymbols[id] ?: break

        if (ch == "<end>") break

        // Skip language tags like <en_us> if listed in languages
        if (ch.startsWith("<") && ch.endsWith(">")) {
            val inner = ch.substring(1, ch.length - 1)
            if (inner in tokenizer.languages) continue
        }

        if (ch == " ") continue
        sb.append(ch)
    }

    return sb.toString()
}