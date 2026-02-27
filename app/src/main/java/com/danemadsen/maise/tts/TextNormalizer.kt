package com.danemadsen.maise.tts

private val ABBREVIATIONS: Map<String, String> = mapOf(
    "mrs"    to "misess",
    "mr"     to "mister",
    "dr"     to "doctor",
    "st"     to "saint",
    "co"     to "company",
    "jr"     to "junior",
    "maj"    to "major",
    "gen"    to "general",
    "drs"    to "doctors",
    "rev"    to "reverend",
    "lt"     to "lieutenant",
    "hon"    to "honorable",
    "sgt"    to "sergeant",
    "capt"   to "captain",
    "esq"    to "esquire",
    "ltd"    to "limited",
    "col"    to "colonel",
    "ft"     to "foot",
    "pty"    to "proprietary",
    "vs"     to "versus",
    "approx" to "approximately",
    "dept"   to "department",
    "prof"   to "professor",
    "etc"    to "et cetera",
    "eg"     to "for example",
    "ie"     to "that is",
)

private val ONES = arrayOf(
    "", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
    "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
    "seventeen", "eighteen", "nineteen"
)

private val TENS = arrayOf(
    "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"
)

private val SCALES = arrayOf(
    "thousand", "million", "billion", "trillion", "quadrillion", "quintillion"
)

private fun hundredsToWords(n: Int): String {
    if (n == 0) return ""
    val sb = StringBuilder()
    if (n >= 100) {
        sb.append(ONES[n / 100]).append(" hundred")
        if (n % 100 != 0) sb.append(" and ")
    }
    val rem = n % 100
    when {
        rem == 0 -> Unit
        rem < 20 -> sb.append(ONES[rem])
        else -> {
            sb.append(TENS[rem / 10])
            if (rem % 10 != 0) sb.append(" ").append(ONES[rem % 10])
        }
    }
    return sb.toString().trim()
}

private fun longToWords(n: Long): String {
    if (n == 0L) return "zero"
    if (n < 0L) return "minus ${longToWords(-n)}"
    val groups = mutableListOf<Int>()
    var rem = n
    while (rem > 0) {
        groups.add(0, (rem % 1000).toInt())
        rem /= 1000
    }
    val parts = mutableListOf<String>()
    for (i in groups.indices) {
        val chunk = groups[i]
        if (chunk == 0) continue
        val words = hundredsToWords(chunk)
        val scaleIdx = groups.size - i - 2
        parts.add(if (scaleIdx in SCALES.indices) "$words ${SCALES[scaleIdx]}" else words)
    }
    return parts.joinToString(" ")
}

private fun digitToWord(c: Char): String = when (c) {
    '0' -> "zero"
    '1' -> "one"
    '2' -> "two"
    '3' -> "three"
    '4' -> "four"
    '5' -> "five"
    '6' -> "six"
    '7' -> "seven"
    '8' -> "eight"
    '9' -> "nine"
    else -> c.toString()
}

private val ORDINAL_ONES = arrayOf(
    "", "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth",
    "tenth", "eleventh", "twelfth", "thirteenth", "fourteenth", "fifteenth", "sixteenth",
    "seventeenth", "eighteenth", "nineteenth"
)

private val ORDINAL_TENS = arrayOf(
    "", "", "twentieth", "thirtieth", "fortieth", "fiftieth",
    "sixtieth", "seventieth", "eightieth", "ninetieth"
)

private fun hundredsToOrdinal(n: Int): String {
    if (n == 0) return ""
    val sb = StringBuilder()
    if (n >= 100) {
        if (n % 100 == 0) {
            sb.append(ONES[n / 100]).append(" hundredth")
            return sb.toString().trim()
        }
        sb.append(ONES[n / 100]).append(" hundred and ")
    }
    val rem = n % 100
    when {
        rem < 20 -> sb.append(ORDINAL_ONES[rem])
        rem % 10 == 0 -> sb.append(ORDINAL_TENS[rem / 10])
        else -> sb.append(TENS[rem / 10]).append(" ").append(ORDINAL_ONES[rem % 10])
    }
    return sb.toString().trim()
}

private fun longToOrdinal(n: Long): String {
    if (n == 0L) return "zeroth"
    if (n < 0L) return "minus ${longToOrdinal(-n)}"
    val groups = mutableListOf<Int>()
    var rem = n
    while (rem > 0) {
        groups.add(0, (rem % 1000).toInt())
        rem /= 1000
    }
    // If more than one non-zero group, only the last group is ordinal; the rest are cardinal
    val nonZeroCount = groups.count { it != 0 }
    if (nonZeroCount > 1) {
        val parts = mutableListOf<String>()
        for (i in groups.indices) {
            val chunk = groups[i]
            if (chunk == 0) continue
            val scaleIdx = groups.size - i - 2
            val isLast = (i == groups.indices.last { groups[it] != 0 })
            if (isLast) {
                val words = hundredsToOrdinal(chunk)
                parts.add(if (scaleIdx in SCALES.indices) "$words ${SCALES[scaleIdx]}" else words)
            } else {
                val words = hundredsToWords(chunk)
                parts.add(if (scaleIdx in SCALES.indices) "$words ${SCALES[scaleIdx]}" else words)
            }
        }
        return parts.joinToString(" ")
    }
    return hundredsToOrdinal(groups[0])
}

// Ordinal suffix pattern: digits followed by st/nd/rd/th
private val ORDINAL_REGEX = Regex("""\b(\d[\d,]*)(st|nd|rd|th)\b""", RegexOption.IGNORE_CASE)

// Integers (with optional comma separators) or decimals, with optional leading minus.
// Negative lookahead (?![a-zA-Z]) prevents matching ordinals like "1st", "2nd", "3rd".
private val NUMBER_REGEX = Regex("""-?\d[\d,]*(?:\.\d+)?(?![a-zA-Z])""")

// Whole-word match with optional trailing dot, before whitespace / punctuation / end-of-string.
private val ABBREV_REGEX = Regex("""\b([A-Za-z]+)\.?(?=[\s,;:!?]|$)""")

fun normalizeText(text: String): String {
    var result = text

    // 1. Dotted multi-character abbreviations (must run before word-level pass)
    result = result.replace(Regex("""\be\.g\.?""", RegexOption.IGNORE_CASE), "for example")
    result = result.replace(Regex("""\bi\.e\.?""", RegexOption.IGNORE_CASE), "that is")
    result = result.replace(Regex("""\betc\.?""",  RegexOption.IGNORE_CASE), "et cetera")

    // 2. Ordinal numbers → words (must run before plain number pass)
    result = ORDINAL_REGEX.replace(result) { match ->
        val raw = match.groupValues[1].replace(",", "")
        val n = raw.toLongOrNull() ?: return@replace match.value
        longToOrdinal(n)
    }

    // 3. Numbers → words
    result = NUMBER_REGEX.replace(result) { match ->
        val raw    = match.value.replace(",", "")
        val dotIdx = raw.indexOf('.')
        if (dotIdx >= 0) {
            // Decimal: "3.14" → "three point one four"
            val isNeg    = raw.startsWith("-")
            val absStr   = raw.substring(if (isNeg) 1 else 0, dotIdx)
            val absInt   = absStr.toLongOrNull() ?: return@replace match.value
            val intWords = if (isNeg) "minus ${longToWords(absInt)}" else longToWords(absInt)
            val decWords = raw.substring(dotIdx + 1).map { digitToWord(it) }.joinToString(" ")
            "$intWords point $decWords"
        } else {
            longToWords(raw.toLongOrNull() ?: return@replace match.value)
        }
    }

    // 4. Word-level abbreviations: "Mr." → "mister", "Sgt." → "sergeant", etc.
    result = ABBREV_REGEX.replace(result) { match ->
        ABBREVIATIONS[match.groupValues[1].lowercase()] ?: match.value
    }

    return result
}
