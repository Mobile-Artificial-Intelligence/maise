package com.danemadsen.maise

import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.danemadsen.maise.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "maise_tts_prefs"
private const val PREF_VOICE = "selected_voice"

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    private var tts: KokoroTTS? = null
    private var audioTrack: AudioTrack? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

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
                tts = KokoroTTS(applicationContext)
                withContext(Dispatchers.Main) { setStatus("Ready") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { setStatus("Error loading models: ${e.message}") }
            }
        }

        binding.speakButton.setOnClickListener { onSpeakClicked() }
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
                val pcm = engine.synthesize(text, voiceInfo.id)
                withContext(Dispatchers.Main) { setStatus("Playing\u2026") }
                playPcm(pcm)
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

    private fun playPcm(pcm: ShortArray) {
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
            .setBufferSizeInBytes(maxOf(minBuf, pcm.size * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack?.write(pcm, 0, pcm.size)
        audioTrack?.play()
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
