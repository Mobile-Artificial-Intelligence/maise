// Vendored from GrapheneOS SpeechServices (https://github.com/danemadsen/SpeechServices), g2p @ d88f53f.
package com.danemadsen.maise.g2p

import android.util.Log
import com.danemadsen.maise.g2p.verboseLog
import com.ibm.icu.text.RuleBasedNumberFormat
import com.ibm.icu.util.ULocale
import java.util.Locale

private const val TAG: String = "NumToWords"

enum class NumToWordsRuleSet(val value: String) {
    Ordinal("%spellout-ordinal"),
    Cardinal("%spellout-cardinal"),
    Numbering("%spellout-numbering"),
    NumberingYear("%spellout-numbering-year"),
}

fun numToWords(
    num: String,
    ruleSet: NumToWordsRuleSet = NumToWordsRuleSet.Cardinal,
    locale: Locale,
): String {
    val numberFormat =
        RuleBasedNumberFormat(ULocale.forLocale(locale), RuleBasedNumberFormat.SPELLOUT)
    val numAsLong = num.toLongOrNull()
    if (numAsLong != null) {
        return numberFormat.format(numAsLong, ruleSet.value)
    }
    val numAsDouble = num.toDoubleOrNull()
    if (numAsDouble != null) {
        return numberFormat.format(numAsDouble, ruleSet.value)
    }
    Log.w(TAG, "Failed to convert number to words, returning empty String")
    verboseLog(TAG) {
        "Failed number to words parameters: num: $num, ruleSet: $ruleSet, locale: $locale"
    }
    return ""
}
