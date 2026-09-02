package com.danemadsen.maise.asr

import android.animation.AnimatorSet
import android.util.Log
import android.animation.ObjectAnimator
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import com.danemadsen.maise.R
import com.danemadsen.maise.databinding.ActivityRecognizeBinding

/**
 * Floating activity that handles [RecognizerIntent.ACTION_RECOGNIZE_SPEECH].
 *
 * Apps that call startActivityForResult(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, ...)
 * land here instead of binding to [MaiseAsrService] directly. The activity delegates
 * to [MaiseAsrService] via [SpeechRecognizer] and returns
 * [RecognizerIntent.EXTRA_RESULTS] to the calling app.
 *
 * Shown as a compact rounded card over a dimmed view of the calling app, with a
 * waveform reacting to the caller's voice level and the partial transcript
 * streamed as Whisper decodes.
 *
 * Equivalent to WhisperIMEplus's WhisperRecognizeActivity.
 */
class MaiseRecognizeActivity : AppCompatActivity() {

    private companion object {
        const val TAG = "MaiseRecognizeActivity"
    }

    private var recognizer: SpeechRecognizer? = null
    private var binding: ActivityRecognizeBinding? = null
    private var rmsLinkLogged = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = ActivityRecognizeBinding.inflate(layoutInflater)
        binding = view
        setContentView(view.root)

        // Card pop-in: fade + slight scale-up. Done in code rather than relying on
        // window animations, which some OEM builds ignore for floating windows.
        view.recognizeCard.apply {
            alpha = 0f
            scaleY = 0.92f
            scaleX = 0.92f
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(view.recognizeCard, "alpha", 0f, 1f),
                    ObjectAnimator.ofFloat(view.recognizeCard, "scaleX", 0.92f, 1f),
                    ObjectAnimator.ofFloat(view.recognizeCard, "scaleY", 0.92f, 1f)
                )
                duration = 200
                interpolator = DecelerateInterpolator()
                start()
            }
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(
            this,
            ComponentName(this, MaiseAsrService::class.java)
        )
        recognizer?.setRecognitionListener(recognitionListener)

        // Forward the caller's language extra, but force partials on — the
        // popover's live text is our UI decision, not the caller's.
        val listenIntent = Intent(
            intent?.takeIf { it.hasExtra(RecognizerIntent.EXTRA_LANGUAGE) }
                ?: Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        ).apply { putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true) }
        recognizer?.startListening(listenIntent)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            binding?.recognizeStatus?.text = getString(R.string.recognize_listening)
        }
        override fun onBeginningOfSpeech() {
            binding?.recognizeStatus?.text = getString(R.string.recognize_listening)
        }
        override fun onRmsChanged(rmsdB: Float) {
            if (!rmsLinkLogged) {
                rmsLinkLogged = true
                Log.d(TAG, "onRmsChanged link established: $rmsdB dB")
            }
            // Service emits dBFS (roughly -60..0); normalize into the view's 0..1
            binding?.recognizeWaveform?.setLevel(((rmsdB + 45f) / 35f).coerceIn(0f, 1f))
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            binding?.recognizeStatus?.text = getString(R.string.recognize_processing)
            binding?.recognizeWaveform?.setProcessing(true)
        }
        override fun onError(error: Int) {
            setResult(RESULT_CANCELED)
            finish()
        }
        override fun onResults(results: Bundle) {
            val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val scores = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
            val data = Intent().apply {
                putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, matches)
                // Chrome's omnibox voice search (VoiceRecognitionIntentHandler) drops
                // the entire result when EXTRA_CONFIDENCE_SCORES is absent or its
                // length doesn't match EXTRA_RESULTS — always return one per match.
                putExtra(
                    RecognizerIntent.EXTRA_CONFIDENCE_SCORES,
                    FloatArray(matches?.size ?: 0) { i -> scores?.getOrNull(i) ?: 1.0f }
                )
            }
            setResult(RESULT_OK, data)
            finish()
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!partial.isNullOrBlank()) {
                val view = binding ?: return
                // First partial: reveal the transcript area with a smooth bounds
                // change instead of the card snapping taller.
                if (view.recognizeText.visibility == View.GONE) {
                    TransitionManager.beginDelayedTransition(
                        view.root as ViewGroup,
                        ChangeBounds().apply { duration = 200 }
                    )
                    view.recognizeText.visibility = View.VISIBLE
                }
                view.recognizeText.text = partial
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        recognizer?.cancel()
        recognizer?.destroy()
    }
}