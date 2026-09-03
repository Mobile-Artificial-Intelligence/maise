package com.danemadsen.maise.asr

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.google.android.material.color.MaterialColors
import kotlin.math.max
import kotlin.math.sin

/**
 * Animated audio-level bars shown while the recognition popover listens.
 *
 * A row of round-capped vertical bars painted a single solid theme accent color
 * (Material You dynamic primary on API 31+, M3 baseline on API 26-30), tallest
 * toward the center with dot-bars at each end.
 *
 * [setLevel] receives a normalized 0..1 voice level (driven by RMS from
 * [MaiseAsrService]); bars smoothly grow toward it, scaled by a center-weighted
 * profile, with a per-bar phase offset so they ripple like a waveform. When idle
 * the bars settle to a small height with a gentle sway. [setProcessing] switches
 * to a slow "breathing" animation at reduced opacity while Whisper decodes.
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private companion object {
        const val BAR_COUNT = 9
        const val BAR_FRACTION = 0.6f          // bar width as a fraction of its slot
        const val IDLE_FRACTION = 0.08f        // bar height when silent (logo's dot bars)
        const val IDLE_ENVELOPE = 0.25f        // center-tall silhouette hint at idle
        const val SMOOTHING = 0.35f            // per-frame height easing
        const val PROCESSING_BASE = 0.30f      // breath midpoint (fraction of height)
        const val PROCESSING_AMPLITUDE = 0.10f
        const val SPEED_LISTENING = 6.0f       // rad/s ripple while listening
        const val SPEED_PROCESSING = 1.2f      // rad/s breath
        const val PROCESSING_ALPHA = 160       // 0..255, dimmed while decoding

        /** Center-weighted height profile, mirroring the icon's bar heights. */
        val PROFILE = floatArrayOf(0.3f, 0.5f, 0.75f, 0.95f, 1.0f, 0.95f, 0.75f, 0.5f, 0.3f)
    }

    // Single solid accent color, resolved from the theme. Resolving at
    // construction is correct: on API 31+ the dynamic-color overlay is applied
    // in onActivityPreCreated (before setContentView inflates this view), and
    // below 31 the static baseline palette needs no overlay. A uiMode change
    // recreates MaiseRecognizeActivity (its configChanges list excludes
    // uiMode), giving a freshly-constructed view with the new palette.
    private val accentColor = MaterialColors.getColor(
        this, com.google.android.material.R.attr.colorPrimary, Color.GRAY
    )

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        color = accentColor
    }

    /** Per-bar phase offsets so bars move out of step. */
    private val phases = FloatArray(BAR_COUNT) { it * 1.1f }
    /** Current smoothed bar heights, as a fraction of view height. */
    private val heights = FloatArray(BAR_COUNT) { IDLE_FRACTION }

    private val barRect = RectF()

    @Volatile private var level = 0f
    private var processing = false
    private var animator: ValueAnimator? = null
    private var timeSeconds = 0f

    /** Voice level, 0..1. */
    fun setLevel(value: Float) {
        level = value.coerceIn(0f, 1f)
    }

    /** Switch between listening (level-driven) and processing (breathing) animation. */
    fun setProcessing(value: Boolean) {
        if (processing == value) return
        processing = value
        barPaint.alpha = if (processing) PROCESSING_ALPHA else 255
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 16L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                // 16 ms nominal frame — smoothness comes from the per-bar easing,
                // so frame drops degrade gracefully rather than jumping.
                timeSeconds += 0.016f
                tick()
            }
        }.also { it.start() }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private fun tick() {
        if (width == 0 || height == 0) return
        for (i in 0 until BAR_COUNT) {
            val target = if (processing) {
                PROCESSING_BASE +
                    PROCESSING_AMPLITUDE * sin(timeSeconds * SPEED_PROCESSING + phases[i])
            } else {
                val wave = 0.55f + 0.45f * sin(timeSeconds * SPEED_LISTENING + phases[i])
                // Even at idle, the center-tall profile is visible so the bars
                // read as the logo's waveform before any audio arrives.
                (IDLE_FRACTION + (IDLE_ENVELOPE + level * 0.75f) * PROFILE[i]) * wave
            }
            heights[i] += (target.coerceIn(0f, 1f) - heights[i]) * SMOOTHING
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val slot = width.toFloat() / BAR_COUNT
        val barWidth = slot * BAR_FRACTION
        val corner = barWidth / 2f
        var left = (slot - barWidth) / 2f
        for (i in 0 until BAR_COUNT) {
            val h = max(heights[i] * height, barWidth)  // never shorter than a dot
            val top = (height - h) / 2f
            barRect.set(left, top, left + barWidth, top + h)
            canvas.drawRoundRect(barRect, corner, corner, barPaint)
            left += slot
        }
    }
}