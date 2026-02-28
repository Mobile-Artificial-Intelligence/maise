package com.danemadsen.maise.asr

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.danemadsen.maise.databinding.FragmentAsrBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val REC_SAMPLE_RATE    = 16000
private const val MAX_RECORD_SECONDS = 30

class AsrFragment : Fragment() {

    private var _binding: FragmentAsrBinding? = null
    private val binding get() = _binding!!

    private var asr: WhisperASR? = null
    private var recordJob: Job? = null

    @Volatile private var isRecording = false
    @Volatile private var pendingSamples: ShortArray? = null

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording() else setStatus("Microphone permission required.")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAsrBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load ASR engine in background
        setStatus("Loading model\u2026")
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                asr = WhisperASR(requireContext().applicationContext)
                withContext(Dispatchers.Main) { setStatus("Ready — tap to record") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { setStatus("Error loading model: ${e.message}") }
            }
        }

        binding.recordButton.setOnClickListener {
            if (isRecording) stopRecording() else checkPermissionAndRecord()
        }

        binding.openAsrSettingsButton.setOnClickListener {
            try {
                startActivity(Intent("android.settings.VOICE_INPUT_SETTINGS"))
            } catch (e: ActivityNotFoundException) {
                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            }
        }
    }

    // -------------------------------------------------------------------------
    // Permission
    // -------------------------------------------------------------------------

    private fun checkPermissionAndRecord() {
        if (asr == null) { setStatus("Model still loading, please wait."); return }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            requestPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // -------------------------------------------------------------------------
    // Recording
    // -------------------------------------------------------------------------

    private fun startRecording() {
        val minBuf = AudioRecord.getMinBufferSize(
            REC_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) { setStatus("Audio device unavailable."); return }

        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            REC_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, REC_SAMPLE_RATE * 2)
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            setStatus("Failed to open microphone.")
            return
        }

        isRecording = true
        binding.recordButton.text = "Stop Recording"
        setStatus("Recording\u2026")
        binding.transcriptionText.setText("")

        recordJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val maxSamples = REC_SAMPLE_RATE * MAX_RECORD_SECONDS
            val buffer     = ShortArray(maxSamples)
            val chunk      = ShortArray(1024)
            var total      = 0

            rec.startRecording()
            while (isActive && isRecording && total < maxSamples) {
                val read = rec.read(chunk, 0, chunk.size.coerceAtMost(maxSamples - total))
                if (read < 0) break
                chunk.copyInto(buffer, total, 0, read)
                total += read
            }
            rec.stop(); rec.release()

            pendingSamples = if (total > 0) buffer.copyOf(total) else null
            isRecording = false

            withContext(Dispatchers.Main) {
                binding.recordButton.text = "Start Recording"
                transcribePending()
            }
        }
    }

    private fun stopRecording() {
        isRecording = false
        // The recording coroutine will finish naturally and call transcribePending()
    }

    // -------------------------------------------------------------------------
    // Transcription
    // -------------------------------------------------------------------------

    private fun transcribePending() {
        val samples = pendingSamples ?: run { setStatus("No audio captured."); return }
        pendingSamples = null

        val engine = asr ?: run { setStatus("Model not ready."); return }

        setStatus("Transcribing\u2026")
        binding.recordButton.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val text = engine.transcribe(samples, REC_SAMPLE_RATE)
                withContext(Dispatchers.Main) {
                    binding.transcriptionText.setText(text.ifBlank { "(no speech detected)" })
                    setStatus("Done")
                    binding.recordButton.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setStatus("Transcription error: ${e.message}")
                    binding.recordButton.isEnabled = true
                }
            }
        }
    }

    private fun setStatus(msg: String) { binding.asrStatusText.text = msg }

    override fun onDestroyView() {
        super.onDestroyView()
        isRecording = false
        recordJob?.cancel()
        asr?.close()
        _binding = null
    }
}
