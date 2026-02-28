package com.danemadsen.maise

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.danemadsen.maise.asr.MaiseAsrService
import com.danemadsen.maise.asr.MaiseKeepAliveService
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

        // Start the keep-alive service first so it enters DATA_SYNC foreground before any
        // recognition session begins. MaiseAsrService upgrades to MICROPHONE foreground during
        // recognition; the "another non-shortService FGS is active" eligible state condition
        // is satisfied by KeepAliveService being a separate component running as DATA_SYNC.
        startService(Intent(this, MaiseKeepAliveService::class.java))

        // startForegroundService (not startService) so the service's onStartCommand()
        // can call startForeground(MICROPHONE) with eligible state — visible activity
        // satisfies the PROCESS_STATE_TOP requirement. This sets
        // mAllowWhileInUsePermissionInFgs = true on the ServiceRecord, which means all
        // subsequent startForeground(MICROPHONE) calls (notification updates between
        // sessions) skip the eligible state check entirely.
        startForegroundService(Intent(this, MaiseAsrService::class.java))
    }
}
