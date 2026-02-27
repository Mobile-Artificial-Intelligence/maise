package com.danemadsen.maise.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.JsonReader
import java.io.File
import java.io.InputStreamReader
import java.nio.LongBuffer

private val PUNCTUATION_BEFORE = setOf(".", ",", "!", "?", ";", ":", ")", "]", "}", "\u00BB", "\u201D")
private val PUNCTUATION_AFTER  = setOf("(", "[", "{", "\u00AB", "\u201C")
private val WORD_REGEX = Regex("""[\w']+|[^\w\s]""")

// ID 0 = blank/pad ("_"), ID 1 = <en_us>, ID 2 = <end>, IDs 3-63 = phoneme symbols
private const val BLANK_ID = 0
private const val END_ID   = 2

class OpenPhonemizer(context: Context, private val env: OrtEnvironment) {

    private val session: OrtSession
    private val dictionary: Map<String, String>

    init {
        val modelFile = copyAssetToFile(context, "open-phonemizer.onnx")
        val opts = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        session = env.createSession(modelFile.absolutePath, opts)
        dictionary = loadDictionary(context)
    }

    /**
     * Load the flat en_us word→phonemes map from dictionary.json using a streaming
     * JSON reader so the 10 MB file doesn't need to be in memory all at once.
     */
    private fun loadDictionary(context: Context): Map<String, String> {
        return try {
            val map = HashMap<String, String>(130_000)
            context.assets.open("dictionary.json").use { stream ->
                JsonReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    reader.beginObject()                       // {
                    while (reader.hasNext()) {
                        val lang = reader.nextName()           // "en_us"
                        if (lang == "en_us") {
                            reader.beginObject()               // {
                            while (reader.hasNext()) {
                                val word     = reader.nextName()
                                val phonemes = reader.nextString()
                                map[word] = phonemes
                            }
                            reader.endObject()
                        } else {
                            reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Convert a full sentence to IPA phonemes.
     * Words are looked up in the dictionary first; unknown words fall back to the model.
     */
    fun phonemize(text: String): String {
        val tokens = WORD_REGEX.findAll(normalizeText(text)).map { it.value }.toList()
        val sb = StringBuilder()

        for (token in tokens) {
            val isPunct = token.matches(Regex("""[^\w']+"""))

            if (isPunct) {
                when {
                    token in PUNCTUATION_BEFORE -> {
                        if (sb.endsWith(" ")) sb.deleteCharAt(sb.length - 1)
                        sb.append(token)
                    }
                    token in PUNCTUATION_AFTER -> sb.append(token)
                    else -> {
                        if (sb.isNotEmpty() && !sb.endsWith(" ")) sb.append(" ")
                        sb.append(token)
                    }
                }
            } else {
                if (sb.isNotEmpty() && !sb.endsWith(" ") &&
                    !sb.last().toString().let { it in PUNCTUATION_AFTER }
                ) {
                    sb.append(" ")
                }
                val word = token.lowercase()
                sb.append(dictionary[word] ?: phonemizeWord(word))
            }
        }

        return sb.toString().trim()
    }

    private fun phonemizeWord(word: String): String {
        val encoded = encode(word)
        val padded  = LongArray(64) { i -> if (i < encoded.size) encoded[i].toLong() else 0L }

        val inputTensor = OnnxTensor.createTensor(
            env,
            LongBuffer.wrap(padded),
            longArrayOf(1, 64)
        )

        val results = session.run(mapOf("text" to inputTensor))
        // Output shape: [1, 64, 64]  (batch=1, seq_len=64, vocab=64)
        @Suppress("UNCHECKED_CAST")
        val logits = (results[0].value as Array<Array<FloatArray>>)[0]

        inputTensor.close()
        results.close()

        // 1. Argmax across vocab dimension for each position
        val argmax = IntArray(logits.size) { pos ->
            var best = 0
            for (i in 1 until logits[pos].size) {
                if (logits[pos][i] > logits[pos][best]) best = i
            }
            best
        }

        // 2. CTC decode: collapse consecutive identical tokens, then remove blanks (ID 0)
        val sb = StringBuilder()
        var prev = -1
        for (id in argmax) {
            if (id == prev) continue          // remove consecutive duplicate
            prev = id
            if (id == BLANK_ID) continue      // remove blank/pad
            if (id == END_ID)   break         // stop at <end>
            val sym = tokenizer.phonemeSymbols[id] ?: continue
            if (sym.startsWith("<")) continue // skip any remaining special tokens
            sb.append(sym)
        }
        return sb.toString()
    }

    fun close() {
        session.close()
    }
}

/** Copy an asset to the app's files directory (cached; not re-copied on subsequent runs). */
fun copyAssetToFile(context: Context, assetPath: String): File {
    val dest = File(context.filesDir, assetPath)
    if (!dest.exists()) {
        dest.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            dest.outputStream().use { input.copyTo(it) }
        }
    }
    return dest
}
