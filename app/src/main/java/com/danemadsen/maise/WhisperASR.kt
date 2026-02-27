package com.danemadsen.maise

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.exp
import kotlin.math.PI

private const val TAG = "WhisperASR"

// Audio / STFT constants matching OpenAI Whisper
private const val WHISPER_SAMPLE_RATE = 16000
private const val N_FFT             = 400       // window length (25 ms at 16 kHz)
private const val HOP_LENGTH        = 160       // hop size (10 ms at 16 kHz)
private const val N_MELS            = 80        // mel filter count
private const val CHUNK_SECONDS     = 30
private const val N_SAMPLES         = WHISPER_SAMPLE_RATE * CHUNK_SECONDS   // 480,000
private const val N_FRAMES          = N_SAMPLES / HOP_LENGTH                 // 3,000
private const val F_MIN             = 0.0
private const val F_MAX             = 8000.0

// We zero-pad each N_FFT-sample window to the next power of 2 for a fast FFT.
// Frequency resolution differs slightly from Whisper's exact 400-pt FFT, but
// the mel filterbank is built to match these frequencies in Hz.
private const val FFT_SIZE          = 512       // next power-of-2 >= N_FFT
private const val N_FREQ_BINS       = FFT_SIZE / 2 + 1   // 257

// Encoder output dimensions for distil-small.en
private const val ENCODER_SEQ_LEN  = 1500
private const val ENCODER_HIDDEN   = 384

// Decoding limits
private const val MAX_NEW_TOKENS    = 448

/**
 * Core Whisper ASR engine for distil-small.en.
 *
 * Required assets (place in whisper/src/main/assets/):
 *   - encoder_model_quantized.onnx
 *   - decoder_model_quantized.onnx
 *   - vocab.json
 *
 * Usage:
 *   val asr = WhisperASR(context)
 *   val text = asr.transcribe(pcmSamples, inputSampleRate)
 *   asr.close()
 */
class WhisperASR(context: Context) {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val encoderSession: OrtSession
    private val decoderSession: OrtSession
    private val tokenizer: WhisperTokenizer

    // Precomputed tables
    private val hannWindow: FloatArray = FloatArray(N_FFT) { i ->
        (0.5 * (1.0 - cos(2.0 * PI * i / (N_FFT - 1)))).toFloat()
    }
    private val melFilterbank: Array<FloatArray> = buildMelFilterbank()

    init {
        val opts = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        val encoderFile = copyAssetToFile(context, "encoder_model_quantized.onnx")
        val decoderFile = copyAssetToFile(context, "decoder_model_quantized.onnx")
        encoderSession = env.createSession(encoderFile.absolutePath, opts)
        decoderSession = env.createSession(decoderFile.absolutePath, opts)
        tokenizer      = WhisperTokenizer(context)
        Log.i(TAG, "WhisperASR initialized")
    }

    /**
     * Transcribe [audioSamples] (16-bit PCM, any sample rate) to text.
     * The audio is resampled linearly to 16 kHz and clamped to 30 seconds.
     *
     * Returns the transcribed text, or an empty string on failure.
     */
    fun transcribe(audioSamples: ShortArray, inputSampleRate: Int): String {
        val floatAudio = resampleToFloat(audioSamples, inputSampleRate)
        val mel        = computeLogMelSpectrogram(floatAudio)
        return runInference(mel)
    }

    // -------------------------------------------------------------------------
    // Resampling
    // -------------------------------------------------------------------------

    private fun resampleToFloat(samples: ShortArray, srcRate: Int): FloatArray {
        // Convert to float [-1, 1]
        val floatSrc = FloatArray(samples.size) { i -> samples[i] / 32768f }

        if (srcRate == WHISPER_SAMPLE_RATE) return floatSrc

        // Linear interpolation resampling
        val ratio   = srcRate.toDouble() / WHISPER_SAMPLE_RATE
        val outLen  = (floatSrc.size / ratio).toInt()
        val out     = FloatArray(outLen)
        for (i in 0 until outLen) {
            val src = i * ratio
            val lo  = src.toInt().coerceIn(0, floatSrc.size - 1)
            val hi  = (lo + 1).coerceIn(0, floatSrc.size - 1)
            val frac = (src - lo).toFloat()
            out[i]  = floatSrc[lo] * (1f - frac) + floatSrc[hi] * frac
        }
        return out
    }

    // -------------------------------------------------------------------------
    // Log-mel spectrogram — matches Whisper's preprocessing exactly in Hz-space
    // -------------------------------------------------------------------------

    private fun computeLogMelSpectrogram(audio: FloatArray): FloatArray {
        // Mel accumulator [n_mels][n_frames]
        val mel = Array(N_MELS) { FloatArray(N_FRAMES) }

        val re = FloatArray(FFT_SIZE)
        val im = FloatArray(FFT_SIZE)

        for (frame in 0 until N_FRAMES) {
            val start = frame * HOP_LENGTH

            // Fill FFT input: Hann-windowed audio, zero-pad to FFT_SIZE
            re.fill(0f); im.fill(0f)
            for (j in 0 until N_FFT) {
                val s = start + j
                re[j] = (if (s < audio.size) audio[s] else 0f) * hannWindow[j]
            }

            fftInPlace(re, im)

            // Accumulate power spectrum into mel bins
            for (m in 0 until N_MELS) {
                var sum = 0f
                val row = melFilterbank[m]
                for (k in 0 until N_FREQ_BINS) {
                    sum += (re[k] * re[k] + im[k] * im[k]) * row[k]
                }
                mel[m][frame] = sum
            }
        }

        // Log-compress: log10(max(x, 1e-10))
        var maxLog = Float.NEGATIVE_INFINITY
        for (m in 0 until N_MELS) {
            for (f in 0 until N_FRAMES) {
                val v = log10(mel[m][f].coerceAtLeast(1e-10f))
                mel[m][f] = v
                if (v > maxLog) maxLog = v
            }
        }

        // Whisper normalisation: clamp to [max-8, max], then (x + 4) / 4
        val result = FloatArray(N_MELS * N_FRAMES)
        val floor  = maxLog - 8f
        for (m in 0 until N_MELS) {
            val base = m * N_FRAMES
            for (f in 0 until N_FRAMES) {
                result[base + f] = (mel[m][f].coerceAtLeast(floor) + 4f) / 4f
            }
        }
        return result
    }

    // -------------------------------------------------------------------------
    // Radix-2 Cooley-Tukey FFT (in-place, size must be a power of 2)
    // -------------------------------------------------------------------------

    private fun fftInPlace(re: FloatArray, im: FloatArray) {
        val n = re.size

        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }

        // Butterfly passes
        var len = 2
        while (len <= n) {
            val half = len shr 1
            val ang  = -PI / half
            val wRe  = cos(ang).toFloat()
            val wIm  = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var curRe = 1f; var curIm = 0f
                for (k in 0 until half) {
                    val uRe = re[i + k];           val uIm = im[i + k]
                    val vRe = re[i + k + half] * curRe - im[i + k + half] * curIm
                    val vIm = re[i + k + half] * curIm + im[i + k + half] * curRe
                    re[i + k]        = uRe + vRe;  im[i + k]        = uIm + vIm
                    re[i + k + half] = uRe - vRe;  im[i + k + half] = uIm - vIm
                    val newRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = newRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    // -------------------------------------------------------------------------
    // Mel filterbank (Slaney / librosa style, matches Whisper's mel_filters.npz)
    // -------------------------------------------------------------------------

    private fun buildMelFilterbank(): Array<FloatArray> {
        val filterbank = Array(N_MELS) { FloatArray(N_FREQ_BINS) }

        // N_FREQ_BINS evenly-spaced frequencies in Hz (FFT bin centres)
        val fftFreqs = DoubleArray(N_FREQ_BINS) { k -> k.toDouble() * WHISPER_SAMPLE_RATE / FFT_SIZE }

        // N_MELS + 2 mel-scale points spanning [F_MIN, F_MAX], converted back to Hz
        val melMin = hzToMelSlaney(F_MIN)
        val melMax = hzToMelSlaney(F_MAX)
        val hzPts  = DoubleArray(N_MELS + 2) { i ->
            melToHzSlaney(melMin + i * (melMax - melMin) / (N_MELS + 1))
        }

        for (m in 0 until N_MELS) {
            val fLo = hzPts[m]; val fCtr = hzPts[m + 1]; val fHi = hzPts[m + 2]
            // Slaney normalisation: scale each filter so its area in Hz equals 1
            val enorm = (2.0 / (fHi - fLo)).toFloat()
            for (k in 0 until N_FREQ_BINS) {
                val f = fftFreqs[k]
                val w = when {
                    f < fLo || f > fHi -> 0.0
                    f < fCtr           -> (f - fLo) / (fCtr - fLo)
                    else               -> (fHi - f) / (fHi - fCtr)
                }
                filterbank[m][k] = (w * enorm).toFloat()
            }
        }
        return filterbank
    }

    // Slaney mel scale (librosa default, htk=False)
    private fun hzToMelSlaney(hz: Double): Double {
        val fSp       = 200.0 / 3.0
        val minLogHz  = 1000.0
        val minLogMel = minLogHz / fSp
        val logStep   = ln(6.4) / 27.0
        return if (hz < minLogHz) hz / fSp else minLogMel + ln(hz / minLogHz) / logStep
    }

    private fun melToHzSlaney(mel: Double): Double {
        val fSp       = 200.0 / 3.0
        val minLogHz  = 1000.0
        val minLogMel = minLogHz / fSp
        val logStep   = ln(6.4) / 27.0
        return if (mel < minLogMel) mel * fSp else minLogHz * exp((mel - minLogMel) * logStep)
    }

    // -------------------------------------------------------------------------
    // ONNX inference: encoder → decoder loop
    // -------------------------------------------------------------------------

    private fun runInference(melData: FloatArray): String {
        // --- Encoder ---
        val melBuf    = FloatBuffer.wrap(melData)
        val melTensor = OnnxTensor.createTensor(
            env, melBuf, longArrayOf(1, N_MELS.toLong(), N_FRAMES.toLong())
        )

        val encoderResult = encoderSession.run(mapOf("input_features" to melTensor))
        melTensor.close()

        val encoderHiddenTensor = encoderResult[0] as OnnxTensor

        // --- Greedy decoder loop ---
        // SOT sequence for English-only transcription without timestamps
        val allTokens  = mutableListOf(
            WHISPER_TOKEN_SOT, WHISPER_TOKEN_EN,
            WHISPER_TOKEN_TRANSCRIBE, WHISPER_TOKEN_NOTIMESTAMPS
        )
        val textTokens = mutableListOf<Int>()

        try {
            repeat(MAX_NEW_TOKENS) {
                val inputIdsBuf = LongBuffer.wrap(LongArray(allTokens.size) { i -> allTokens[i].toLong() })
                val inputIdsTensor = OnnxTensor.createTensor(
                    env, inputIdsBuf, longArrayOf(1, allTokens.size.toLong())
                )

                val decoderInputs = mapOf(
                    "input_ids"              to inputIdsTensor,
                    "encoder_hidden_states"  to encoderHiddenTensor
                )
                val decoderResult = decoderSession.run(decoderInputs)
                inputIdsTensor.close()

                val nextToken = argmaxLastPosition(decoderResult[0])
                decoderResult.close()

                allTokens.add(nextToken)
                if (nextToken == WHISPER_TOKEN_EOT) return@repeat
                if (nextToken < WHISPER_TOKEN_EOT) textTokens.add(nextToken)
                // Timestamp or other special token: skip from text but keep in sequence
            }
        } finally {
            encoderResult.close()  // also closes encoderHiddenTensor
        }

        val text = tokenizer.decode(textTokens).trim()
        Log.d(TAG, "Transcribed: \"$text\"")
        return text
    }

    /**
     * Extract the argmax over the vocabulary from the last token position of [logitsTensor].
     * Logits shape: [1, seq_len, vocab_size].
     */
    @Suppress("UNCHECKED_CAST")
    private fun argmaxLastPosition(logitsTensor: Any?): Int {
        // ORT returns float arrays; shape [1, seq_len, vocab_size]
        val logits: Array<Array<FloatArray>> = when (val v = (logitsTensor as? OnnxTensor)?.value) {
            is Array<*> -> v as Array<Array<FloatArray>>
            else        -> return WHISPER_TOKEN_EOT
        }
        val lastRow = logits[0][logits[0].size - 1]
        var best = 0
        var bestVal = lastRow[0]
        for (i in 1 until lastRow.size) {
            if (lastRow[i] > bestVal) { bestVal = lastRow[i]; best = i }
        }
        return best
    }

    fun close() {
        encoderSession.close()
        decoderSession.close()
    }
}
