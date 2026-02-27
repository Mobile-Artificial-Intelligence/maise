package com.danemadsen.maise

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.danemadsen.maise.asr.AsrFragment
import com.danemadsen.maise.tts.TtsFragment

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount() = 2
    override fun createFragment(position: Int): Fragment = when (position) {
        0    -> TtsFragment()
        else -> AsrFragment()
    }
}
