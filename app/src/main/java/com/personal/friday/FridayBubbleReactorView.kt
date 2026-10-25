package com.personal.friday

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Living Kinetic Arc Reactor for the Floating Bubble.
 * - Slower, calm, hyper-smooth idle rotation & breathing.
 * - Dynamic audio wave modulation & golden energy surge when F.R.I.D.A.Y. speaks.
 * - Bubble's screen position remains completely fixed and stationary.
 */
class FridayBubbleReactorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var rotOuter = 0f
    private var rotInner = 0f
    private var breathePhase = 0f

    // Speaking state
    private var isSpeaking = false
    private var speechPhase = 0f
    private var speechWave1 = 0f
    private var speechWave2 = 0.5f

    // Alert pulse shockwave
    private var pulseShockwaveRadius = 0f
    private var pulseShockwaveAlpha = 0f

    private var animator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null

    // Paints
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#F508080C")
    }

    private val outerRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
        color = Color.parseColor("#FFFF6D00")
    }

    private val outerArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
        color = Color.parseColor("#FFFF6D00")
    }

    private val innerArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f
        color = Color.parseColor("#FFA000")
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        color = Color.parseColor("#B3FF6D00")
    }

    private val coreBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#4DFF6D00")
    }

    private val speechWavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f
        color = Color.parseColor("#FFD54F")
    }

    private val coreCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val shockwavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#FFFF6D00")
    }

    private val outerArcRect = RectF()
    private val innerArcRect = RectF()

    init {
        startAnimationLoop()
    }

    fun setSpeaking(speaking: Boolean) {
        if (isSpeaking != speaking) {
            isSpeaking = speaking
            if (speaking) {
                speechWave1 = 0f
                speechWave2 = 0.5f
            }
            invalidate()
        }
    }

    fun startAnimationLoop() {
        if (animator?.isRunning == true) return
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                if (isSpeaking) {
                    // Lively voice modulation: faster rotation and audio cadence
                    rotOuter = (rotOuter + 0.85f) % 360f
                    rotInner = (rotInner - 0.55f + 360f) % 360f
                    speechPhase = (speechPhase + 0.16f) % (2f * Math.PI.toFloat())
                    speechWave1 = (speechWave1 + 0.038f) % 1.0f
                    speechWave2 = (speechWave2 + 0.038f) % 1.0f
                } else {
                    // Calm, slow, majestic idle rotation (~33s full turn)
                    rotOuter = (rotOuter + 0.18f) % 360f
                    rotInner = (rotInner - 0.12f + 360f) % 360f
                    breathePhase = (breathePhase + 0.020f) % (2f * Math.PI.toFloat())
                }
                invalidate()
            }
            start()
        }
    }

    fun stopAnimationLoop() {
        animator?.cancel()
        animator = null
    }

    fun pauseAnimationLoop() {
        animator?.pause()
    }

    fun resumeAnimationLoop() {
        if (animator?.isPaused == true) {
            animator?.resume()
        } else if (animator == null || !animator!!.isRunning) {
            startAnimationLoop()
        }
    }

    fun triggerPulse() {
        pulseAnimator?.cancel()
        val maxR = (min(width, height) / 2f) * 0.9f
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450L
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                val fraction = va.animatedFraction
                pulseShockwaveRadius = maxR * fraction
                pulseShockwaveAlpha = 1f - fraction
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f
        val maxR = min(cx, cy) - 2f

        // 1. Base dark background disc
        canvas.drawCircle(cx, cy, maxR, bgPaint)

        // 2. Outer tactical rim
        if (isSpeaking) {
            outerRimPaint.color = Color.parseColor("#FFA000")
            outerRimPaint.strokeWidth = 2.4f
        } else {
            outerRimPaint.color = Color.parseColor("#FFFF6D00")
            outerRimPaint.strokeWidth = 2.0f
        }
        canvas.drawCircle(cx, cy, maxR, outerRimPaint)

        // 3. Shockwave flare (if triggered by pulse)
        if (pulseShockwaveAlpha > 0.01f && pulseShockwaveRadius > 0f) {
            shockwavePaint.alpha = (pulseShockwaveAlpha * 255).toInt()
            canvas.drawCircle(cx, cy, pulseShockwaveRadius, shockwavePaint)
        }

        // 4. Outer rotating arc segments
        val rOuter = maxR * 0.78f
        outerArcRect.set(cx - rOuter, cy - rOuter, cx + rOuter, cy + rOuter)
        canvas.save()
        canvas.rotate(rotOuter, cx, cy)
        for (i in 0 until 4) {
            val startAngle = i * 90f + 12f
            canvas.drawArc(outerArcRect, startAngle, 66f, false, outerArcPaint)
        }
        // Small radial tick notches at the 4 quadrants
        val tickOuter = maxR * 0.88f
        val tickInner = maxR * 0.74f
        for (i in 0 until 4) {
            val angleRad = Math.toRadians((i * 90f).toDouble())
            val cosA = cos(angleRad).toFloat()
            val sinA = sin(angleRad).toFloat()
            canvas.drawLine(
                cx + tickInner * cosA, cy + tickInner * sinA,
                cx + tickOuter * cosA, cy + tickOuter * sinA,
                tickPaint
            )
        }
        canvas.restore()

        // 5. Inner counter-rotating ring
        val rInner = maxR * 0.52f
        innerArcRect.set(cx - rInner, cy - rInner, cx + rInner, cy + rInner)
        canvas.save()
        canvas.rotate(rotInner, cx, cy)
        for (i in 0 until 3) {
            val startAngle = i * 120f + 15f
            canvas.drawArc(innerArcRect, startAngle, 90f, false, innerArcPaint)
        }
        canvas.restore()

        // 6. Speaking Audio Ripple Waves (emitted outward when F.R.I.D.A.Y. speaks)
        if (isSpeaking) {
            val waveMaxDistance = maxR * 0.76f - maxR * 0.28f
            // Wave 1
            val waveR1 = maxR * 0.28f + waveMaxDistance * speechWave1
            val alpha1 = ((1f - speechWave1) * 220).toInt().coerceIn(0, 255)
            speechWavePaint.alpha = alpha1
            canvas.drawCircle(cx, cy, waveR1, speechWavePaint)

            // Wave 2
            val waveR2 = maxR * 0.28f + waveMaxDistance * speechWave2
            val alpha2 = ((1f - speechWave2) * 220).toInt().coerceIn(0, 255)
            speechWavePaint.alpha = alpha2
            canvas.drawCircle(cx, cy, waveR2, speechWavePaint)
        }

        // 7. Arc Reactor Core
        if (isSpeaking) {
            // Voice Cadence Modulation: dynamic pulse with speech rhythm
            val speechModulation = 1.0f + 0.35f * abs(sin(speechPhase * 1.4f))
            val rCore = (maxR * 0.30f) * speechModulation

            // High-intensity golden voice aura
            coreBodyPaint.color = Color.parseColor("#FFA000")
            coreBodyPaint.alpha = (90 + 70 * abs(sin(speechPhase))).toInt().coerceIn(80, 180)
            canvas.drawCircle(cx, cy, rCore * 1.4f, coreBodyPaint)

            // Bright core body
            coreBodyPaint.color = Color.parseColor("#FFD54F")
            coreBodyPaint.alpha = 240
            canvas.drawCircle(cx, cy, rCore, coreBodyPaint)

            // Dynamic white center spark
            val rSpark = (maxR * 0.14f) * (1.0f + 0.20f * abs(sin(speechPhase * 2f)))
            canvas.drawCircle(cx, cy, rSpark, coreCenterPaint)
        } else {
            // Calm, slow, rhythmic breathing in idle
            val breatheScale = 1.0f + 0.12f * sin(breathePhase)
            val rCore = (maxR * 0.30f) * breatheScale

            // Soft glow aura around core
            coreBodyPaint.color = Color.parseColor("#FF6D00")
            coreBodyPaint.alpha = (40 + 25 * sin(breathePhase)).toInt().coerceIn(30, 90)
            canvas.drawCircle(cx, cy, rCore * 1.35f, coreBodyPaint)

            // Core main disc
            coreBodyPaint.alpha = (140 + 50 * sin(breathePhase)).toInt().coerceIn(100, 220)
            canvas.drawCircle(cx, cy, rCore, coreBodyPaint)

            // Center bright white energy fusion spark
            val rSpark = (maxR * 0.11f) * (1.0f + 0.08f * sin(breathePhase))
            canvas.drawCircle(cx, cy, rSpark, coreCenterPaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimationLoop()
    }
}