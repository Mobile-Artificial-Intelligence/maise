package com.danemadsen.maise

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer

private const val KITTEN_STYLE_DIM = 256
private const val KITTEN_TRIM_SAMPLES = 5000

/**
 * Kitten TTS synthesis pipeline.
 *
 * Uses the same Open Phonemizer and KokoroTokenizer as the Kokoro engine but
 * targets the KittenTTS ONNX model with slightly different input wrapping:
 *   input_ids = [0, ...phoneme_tokens..., 10, 0]
 *
 * Voice styles are stored as individual binary files extracted from voices.npz:
 *   assets/voices/kitten-{name}.bin  (little-endian float32, shape [N, 256])
 *
 * Voice IDs follow the convention "en-US-{name}-kitten".
 */
class KittenTTS(private val context: Context) {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val phonemizer: OpenPhonemizer
    private val session: OrtSession

    init {
        phonemizer = OpenPhonemizer(context, env)

        val modelFile = copyAssetToFile(context, "kitten-tts.onnx")
        val opts = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        session = env.createSession(modelFile.absolutePath, opts)
    }

    /**
     * Synthesize [text] using [voiceId] (e.g. "en-US-bella-kitten") at [speed].
     * Returns raw 16-bit PCM samples at [SAMPLE_RATE] Hz, mono.
     */
    fun synthesize(text: String, voiceId: String, speed: Float = 1.0f): ShortArray {
        // 1. Phonemize
        val phonemes = phonemizer.phonemize(text)

        // 2. Tokenize — reuse the Kokoro tokenizer (compatible vocab).
        //    KokoroTokenizer.encode() returns [0, ...tokens..., 0].
        //    KittenTTS needs [0, ...tokens..., 10, 0] so we insert the extra
        //    end marker (token 10) before the final padding zero.
        val kokoroTokens = OpenPhonemizerOutputTokenizer.encode(phonemes)
        val tokens = IntArray(kokoroTokens.size + 1).also { arr ->
            kokoroTokens.copyInto(arr, 0, 0, kokoroTokens.size - 1)
            arr[kokoroTokens.size - 1] = 10
            arr[kokoroTokens.size]     = 0
        }

        // 3. Load voice style — indexed by phoneme string length (matches the
        //    Python reference: ref_id = min(len(phonemes), shape[0] - 1)).
        val styleData = loadVoiceStyle(voiceId, phonemes.length)

        // 4. Build ONNX inputs
        val inputIdsBuf = LongBuffer.wrap(LongArray(tokens.size) { tokens[it].toLong() })
        val inputIds = OnnxTensor.createTensor(env, inputIdsBuf, longArrayOf(1, tokens.size.toLong()))

        val styleBuf = FloatBuffer.wrap(styleData)
        val style = OnnxTensor.createTensor(env, styleBuf, longArrayOf(1, KITTEN_STYLE_DIM.toLong()))

        val speedBuf = FloatBuffer.wrap(floatArrayOf(speed))
        val speedTensor = OnnxTensor.createTensor(env, speedBuf, longArrayOf(1))

        // 5. Run inference
        val inputs = mapOf(
            "input_ids" to inputIds,
            "style"     to style,
            "speed"     to speedTensor,
        )
        val results = session.run(inputs)

        // 6. Extract waveform and trim trailing artefacts (last 5000 samples)
        val rawWaveform: FloatArray = extractWaveform(results[0].value)
        val trimmed = if (rawWaveform.size > KITTEN_TRIM_SAMPLES)
            rawWaveform.copyOf(rawWaveform.size - KITTEN_TRIM_SAMPLES)
        else
            rawWaveform

        inputIds.close()
        style.close()
        speedTensor.close()
        results.close()

        // 7. Convert float32 [-1, 1] → int16 PCM
        return ShortArray(trimmed.size) { i ->
            (trimmed[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        }
    }

    private fun loadVoiceStyle(voiceId: String, phonemeLength: Int): FloatArray {
        val assetPath = "voices/$voiceId.bin"

        val bytes = context.assets.open(assetPath).use { it.readBytes() }
        val floatCount = bytes.size / 4
        val allFloats = FloatArray(floatCount)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(allFloats)

        val maxIdx = floatCount / KITTEN_STYLE_DIM - 1
        val refId = minOf(phonemeLength, maxIdx.coerceAtLeast(0))
        val offset = refId * KITTEN_STYLE_DIM

        return if (offset + KITTEN_STYLE_DIM <= floatCount) {
            allFloats.copyOfRange(offset, offset + KITTEN_STYLE_DIM)
        } else {
            val safeOffset = maxOf(0, floatCount - KITTEN_STYLE_DIM)
            allFloats.copyOfRange(safeOffset, safeOffset + KITTEN_STYLE_DIM)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractWaveform(value: Any?): FloatArray = when (value) {
        is FloatArray -> value
        is Array<*>  -> (value as Array<FloatArray>)[0]
        else         -> FloatArray(0)
    }

    fun close() {
        phonemizer.close()
        session.close()
    }
}
