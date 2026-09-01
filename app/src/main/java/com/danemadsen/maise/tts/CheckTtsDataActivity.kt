package com.danemadsen.maise.tts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.MissingResourceException

private const val TAG = "CheckTtsDataActivity"

/**
 * Headless activity handling android.speech.tts.engine.CHECK_TTS_DATA.
 *
 * AOSP Settings still sends this (request 1977) after engine selection and
 * populates its language picker solely from the returned
 * Engine.EXTRA_AVAILABLE_VOICES; the TTS framework itself never sends it.
 * All voice data ships in the APK, so the check always passes — there is no
 * INSTALL_TTS_DATA flow to offer.
 *
 * Voice entries use ISO3 codes ("eng-USA"): Settings compares against
 * locale.getISO3Language()-getISO3Country() case-insensitively, and its
 * parseLocaleString() accepts 2- and 3-letter codes.
 */
class CheckTtsDataActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val available = ArrayList<String>()
        val seen = HashSet<String>()
        for (voice in ALL_VOICES) {
            if (!seen.add("${voice.locale.language}-${voice.locale.country}")) continue
            available.add(localeTag(voice))
        }

        Log.d(TAG, "Reporting ${available.size} available locales: $available")
        setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, Intent()
            .putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, available)
            .putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, ArrayList()))
        finish()
    }

    private fun localeTag(voice: VoiceInfo): String = try {
        "${voice.locale.isO3Language}-${voice.locale.isO3Country.uppercase()}"
    } catch (_: MissingResourceException) {
        "${voice.locale.language}-${voice.locale.country.uppercase()}"
    }
}