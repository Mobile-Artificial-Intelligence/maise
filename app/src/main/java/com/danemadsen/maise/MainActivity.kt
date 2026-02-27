package com.danemadsen.maise

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.danemadsen.maise.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "maise_tts_prefs"
private const val PREF_VOICE = "selected_voice"

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    private var tts: TtsEngine? = null
    private var audioTrack: AudioTrack? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val pad = (24 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left + pad, bars.top + pad, bars.right + pad, bars.bottom + pad)
            WindowInsetsCompat.CONSUMED
        }

        // Populate voice spinner
        val voiceLabels: List<String> = ALL_VOICES.map { it.toString() }
        binding.voiceSpinner.adapter =
            ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, voiceLabels)

        // Restore previously selected voice
        val savedVoiceId = prefs.getString(PREF_VOICE, DEFAULT_VOICE_ID) ?: DEFAULT_VOICE_ID
        val savedIndex = ALL_VOICES.indexOfFirst { it.id == savedVoiceId }.takeIf { it >= 0 } ?: 0
        binding.voiceSpinner.setSelection(savedIndex)

        // Load TTS engine in background
        setStatus("Loading models\u2026")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                tts = TtsEngine(applicationContext)
                withContext(Dispatchers.Main) { setStatus("Ready") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { setStatus("Error loading models: ${e.message}") }
            }
        }

        binding.speakButton.setOnClickListener { onSpeakClicked() }
        binding.openSettingsButton.setOnClickListener {
            startActivity(Intent("com.android.settings.TTS_SETTINGS"))
        }
        binding.reportButton.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Mobile-Artificial-Intelligence/maise/issues")))
        }
    }

    private fun onSpeakClicked() {
        val text = binding.inputText.text?.toString()?.trim()
        if (text.isNullOrEmpty()) {
            setStatus("Please enter some text.")
            return
        }

        val engine = tts ?: run { setStatus("Engine not ready yet."); return }
        val voiceInfo = ALL_VOICES[binding.voiceSpinner.selectedItemPosition]

        // Persist selected voice for the TTS service to use
        prefs.edit().putString(PREF_VOICE, voiceInfo.id).apply()

        binding.speakButton.isEnabled = false
        setStatus("Synthesizing\u2026")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sentences = splitSentences(text)
                val minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                audioTrack?.stop()
                audioTrack?.release()
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf * 4)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                audioTrack?.play()

                // Producer-consumer: synthesis and playback run concurrently.
                // Channel capacity 1 lets the producer stay one sentence ahead of playback.
                coroutineScope {
                    val channel = Channel<ShortArray>(capacity = 1)

                    // Producer: synthesize sentences and send PCM to channel.
                    launch {
                        try {
                            for (sentence in sentences) {
                                channel.send(engine.synthesize(sentence, voiceInfo.id))
                            }
                        } finally {
                            channel.close()
                        }
                    }

                    // Consumer (this coroutine): receive PCM and write to AudioTrack.
                    var first = true
                    for (pcm in channel) {
                        if (first) {
                            withContext(Dispatchers.Main) { setStatus("Playing\u2026") }
                            first = false
                        }
                        audioTrack?.write(pcm, 0, pcm.size)
                    }
                }

                audioTrack?.stop()

                withContext(Dispatchers.Main) {
                    setStatus("Done")
                    binding.speakButton.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setStatus("Error: ${e.message}")
                    binding.speakButton.isEnabled = true
                }
            }
        }
    }

    private fun setStatus(msg: String) {
        binding.statusText.text = msg
    }

    override fun onDestroy() {
        super.onDestroy()
        audioTrack?.stop()
        audioTrack?.release()
        tts?.close()
    }
}
