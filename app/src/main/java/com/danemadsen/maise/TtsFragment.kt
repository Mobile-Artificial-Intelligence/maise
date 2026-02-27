package com.danemadsen.maise

import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.danemadsen.maise.databinding.FragmentTtsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "maise_tts_prefs"
private const val PREF_VOICE = "selected_voice"

class TtsFragment : Fragment() {

    private var _binding: FragmentTtsBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: SharedPreferences
    private var tts: TtsEngine? = null
    private var audioTrack: AudioTrack? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTtsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

        val voiceLabels = ALL_VOICES.map { it.toString() }
        binding.voiceSpinner.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, voiceLabels)

        val savedVoiceId = prefs.getString(PREF_VOICE, DEFAULT_VOICE_ID) ?: DEFAULT_VOICE_ID
        val savedIndex = ALL_VOICES.indexOfFirst { it.id == savedVoiceId }.takeIf { it >= 0 } ?: 0
        binding.voiceSpinner.setSelection(savedIndex)

        setStatus("Loading models\u2026")
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                tts = TtsEngine(requireContext().applicationContext)
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
        if (text.isNullOrEmpty()) { setStatus("Please enter some text."); return }

        val engine = tts ?: run { setStatus("Engine not ready yet."); return }
        val voiceInfo = ALL_VOICES[binding.voiceSpinner.selectedItemPosition]
        prefs.edit().putString(PREF_VOICE, voiceInfo.id).apply()

        binding.speakButton.isEnabled = false
        setStatus("Synthesizing\u2026")

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sentences = splitSentences(text)
                val minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
                )

                audioTrack?.stop(); audioTrack?.release()
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

                coroutineScope {
                    val channel = Channel<ShortArray>(capacity = 1)
                    launch {
                        try {
                            for (sentence in sentences) channel.send(engine.synthesize(sentence, voiceInfo.id))
                        } finally { channel.close() }
                    }
                    var first = true
                    for (pcm in channel) {
                        if (first) { withContext(Dispatchers.Main) { setStatus("Playing\u2026") }; first = false }
                        audioTrack?.write(pcm, 0, pcm.size)
                    }
                }

                audioTrack?.stop()
                withContext(Dispatchers.Main) { setStatus("Done"); binding.speakButton.isEnabled = true }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { setStatus("Error: ${e.message}"); binding.speakButton.isEnabled = true }
            }
        }
    }

    private fun setStatus(msg: String) { binding.statusText.text = msg }

    override fun onDestroyView() {
        super.onDestroyView()
        audioTrack?.stop(); audioTrack?.release()
        tts?.close()
        _binding = null
    }
}
