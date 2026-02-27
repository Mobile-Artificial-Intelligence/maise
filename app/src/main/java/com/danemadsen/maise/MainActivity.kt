package com.danemadsen.maise

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.danemadsen.maise.databinding.ActivityMainBinding
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Request RECORD_AUDIO so the background RecognitionService can record.
    // The service cannot prompt for permissions itself, so this must happen
    // while the app is in the foreground at least once.
    private val requestRecordAudio = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored — service checks the grant state itself at call time */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        binding.viewPager.adapter = MainPagerAdapter(this)

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) { 0 -> "TTS"; else -> "ASR" }
        }.attach()

        // Prompt for microphone access on first launch so the background ASR
        // service is ready to use without the user opening the app again.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestRecordAudio.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
