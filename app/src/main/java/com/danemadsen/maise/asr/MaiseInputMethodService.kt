package com.danemadsen.maise.asr

import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.danemadsen.maise.R

/**
 * Minimal InputMethodService whose primary purpose is to run in the same process as
 * [MaiseAsrService] and be bindable by the system as the active input method.
 *
 * Why this matters for RECORD_AUDIO on Android 14+:
 *
 * InputMethodService is bound by the InputMethodManagerService with
 * BIND_INPUT_METHOD | BIND_FOREGROUND_SERVICE | BIND_SHOWING_UI (when visible).
 * BIND_SHOWING_UI elevates the UID's process state to PROCESS_STATE_TOP — the same
 * state as a visible foreground Activity. At PROCESS_STATE_TOP the while-in-use
 * RECORD_AUDIO AppOps check passes unconditionally, so [MaiseAsrService] can start
 * AudioRecord without needing FOREGROUND_SERVICE_TYPE_MICROPHONE eligible state.
 *
 * Usage: user enables "Maise" in Settings → System → Language & Input → On-screen keyboard,
 * then switches to it when a text field is focused. The keyboard will show this view.
 * Once enabled, the process state is elevated whenever the keyboard is showing.
 */
class MaiseInputMethodService : InputMethodService() {

    override fun onCreateInputView(): View =
        TextView(this).apply {
            text = getString(R.string.ime_description)
            setTextColor(ContextCompat.getColor(this@MaiseInputMethodService, R.color.on_surface))
            setBackgroundColor(ContextCompat.getColor(this@MaiseInputMethodService, R.color.surface))
            textSize = 14f
            gravity = Gravity.CENTER
            val dp = resources.displayMetrics.density.toInt()
            setPadding(16 * dp, 20 * dp, 16 * dp, 20 * dp)
        }
}
