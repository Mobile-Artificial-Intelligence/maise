// Vendored from GrapheneOS SpeechServices (https://github.com/danemadsen/SpeechServices), g2p @ d88f53f.
/**
 * Port of Misaki token.py: https://github.com/hexgrad/misaki
 */

package com.danemadsen.maise.g2p

data class MToken(
    val text: String,
    var tag: String,
    var whitespace: String,
    var phonemes: String? = null,
    val startTs: Float? = null,
    val endTs: Float? = null,
    // originally named _
    val more: MutableMap<String, Any?>? = null,
)
