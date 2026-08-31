package com.danemadsen.maise.g2p

import android.content.Context
import android.content.res.Resources
import android.util.Log
import com.danemadsen.maise.R
import com.danemadsen.maise.g2p.fallback_network.FallbackNetwork
import com.danemadsen.maise.g2p.fallback_network.G2PTokenizer
import com.danemadsen.maise.g2p.fallback_network.G2PTokenizerConfig
import java.nio.charset.StandardCharsets.UTF_8
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import opennlp.tools.postag.POSModel
import opennlp.tools.postag.POSTaggerME
import opennlp.tools.tokenize.TokenizerME
import opennlp.tools.tokenize.TokenizerModel
import org.json.JSONObject

private const val TAG = "EnglishG2p"

/**
 * English (en-US) grapheme-to-phoneme engine, vendored from GrapheneOS SpeechServices.
 *
 * Misaki-based: gold lexicon with morphology, OpenNLP POS-aware disambiguation,
 * ICU number-to-words, and a BART seq2seq fallback network for OOV words.
 * Output is an IPA/Misaki phoneme string directly consumable by the Kokoro tokenizer.
 *
 * Usage:
 *   val g2p = EnglishG2p(context)
 *   val phonemes = g2p.phonemize("Hello world")   // "həlˈO wˈɜɹld"
 *   g2p.close()
 */
class EnglishG2p(context: Context) : AutoCloseable {

    private val phonemizer: EnglishPhonemizer

    init {
        val resources = context.resources

        val lexicon = verboseLogTime(TAG, "lexicon loading") {
            val dict = resources.openRawResource(R.raw.us_gold).buffered().use { inputStream ->
                @OptIn(ExperimentalSerializationApi::class)
                Json.decodeFromStream<Map<String, DictionaryValue>>(inputStream)
            }
            Lexicon(false, dict)
        }

        val fallback = verboseLogTime(TAG, "fallback network loading") {
            val g2PTokenizer = G2PTokenizer(loadG2PTokenizerConfig(resources))
            resources.openRawResourceFd(R.raw.en_us__g2p).use {
                FallbackNetwork(it, g2PTokenizer)
            }
        }

        val tokenizer = verboseLogTime(TAG, "opennlp tokenizer loading") {
            resources.openRawResource(R.raw.opennlp_en_ud_ewt_tokens__1_3__2_5_4).buffered().use {
                TokenizerME(TokenizerModel(it))
            }
        }

        val posTagger = verboseLogTime(TAG, "opennlp pos tagger loading") {
            resources.openRawResource(R.raw.opennlp_en_ud_ewt_pos__1_3__2_5_4).buffered().use {
                POSTaggerME(POSModel(it))
            }
        }

        phonemizer = EnglishPhonemizer(lexicon, "ˌʌnnˈOn", tokenizer, posTagger, fallback)
    }

    /**
     * Phonemize [text]. Throws InterruptedException (via [cancellationCheck]) if cancelled.
     */
    fun phonemize(text: String, cancellationCheck: CancellationCheck = {}): String {
        val phonemes = phonemizer
            .main(text, cancellationCheck)
            .first
            // main() preserves raw input whitespace (\n, \t, runs of spaces) — collapse for Kokoro
            .replace(WHITESPACE, " ")
            .trim()
        Log.d(TAG, "phonemize: \"$text\" -> \"$phonemes\"")
        return phonemes
    }

    override fun close() {
        phonemizer.close()
    }

    private companion object {
        private val WHITESPACE = Regex("\\s+")
    }
}

private fun loadG2PTokenizerConfig(resources: Resources): G2PTokenizerConfig {
    resources.openRawResource(R.raw.en_us__g2p__config).use { inputStream ->
        val config = JSONObject(inputStream.readAllBytes().toString(UTF_8))
        return G2PTokenizerConfig(
            config.getString("grapheme_chars"),
            config.getString("phoneme_chars"),
        )
    }
}