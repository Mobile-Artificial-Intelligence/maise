package com.danemadsen.maise.asr

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Floating activity that handles [RecognizerIntent.ACTION_RECOGNIZE_SPEECH].
 *
 * Apps that call startActivityForResult(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, ...)
 * land here instead of binding to [MaiseAsrService] directly. The activity delegates
 * to [MaiseAsrService] via [SpeechRecognizer] and returns
 * [RecognizerIntent.EXTRA_RESULTS] to the calling app.
 *
 * Equivalent to WhisperIMEplus's WhisperRecognizeActivity.
 */
class MaiseRecognizeActivity : AppCompatActivity() {

    private var recognizer: SpeechRecognizer? = null
    private var statusText: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Programmatic dialog-style layout
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
            setBackgroundColor(Color.parseColor("#CC1a1a2e"))
        }

        statusText = TextView(this).apply {
            text = "Listening\u2026"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
        }
        val spinner = ProgressBar(this)

        root.addView(spinner)
        root.addView(statusText)
        setContentView(root)

        recognizer = SpeechRecognizer.createSpeechRecognizer(
            this,
            ComponentName(this, MaiseAsrService::class.java)
        )
        recognizer?.setRecognitionListener(recognitionListener)

        val listenIntent = (intent?.takeIf { it.hasExtra(RecognizerIntent.EXTRA_LANGUAGE) }
            ?: Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH))
        recognizer?.startListening(listenIntent)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            statusText?.text = "Listening\u2026"
        }
        override fun onBeginningOfSpeech() {
            statusText?.text = "Listening\u2026"
        }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            statusText?.text = "Processing\u2026"
        }
        override fun onError(error: Int) {
            setResult(RESULT_CANCELED)
            finish()
        }
        override fun onResults(results: Bundle) {
            val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val data = Intent().apply {
                putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, matches)
            }
            setResult(RESULT_OK, data)
            finish()
        }
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        recognizer?.cancel()
        recognizer?.destroy()
    }
}
