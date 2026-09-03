package com.danemadsen.maise

import android.app.Application
import com.google.android.material.color.DynamicColors

/**
 * Applies Material You dynamic colors to every activity when the device supports
 * them (API 31+, subject to the library's device-support conditions — e.g. Samsung
 * requires OneUI 4.1+). Below that, activities keep the static M3 baseline palette.
 *
 * Must be an Application-level hook: MaiseRecognizeActivity can cold-start the
 * process via ACTION_RECOGNIZE_SPEECH without MainActivity ever running.
 */
class MaiseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}