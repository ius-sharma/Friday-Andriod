package com.personal.friday

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class NanotechParticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class Particle(
        val startRadius: Float,
        val startAngle: Float,
        val targetRadius: Float,
        val targetAngle: Float,
        val spiralRotations: Float,
        val size: Float,
        val color: Int
    )

    private val particles = mutableListOf<Particle>()
    private var progress = 0f
    private var isAssembled = false
    private var shockwaveRadius = 0f
    private var shockwaveAlpha = 0

    private var targetCenterX = 0f
    private var targetCenterY = 0f
    private var targetRadius = 0f

    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        color = Color.parseColor("#FFA000")
    }

    private val shockwavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#FF6D00")
    }

    fun startAssembleAnimation(
        targetX: Float? = null,
        targetY: Float? = null,
        targetRadiusPx: Float? = null,
        onAssembled: () -> Unit
    ) {
        val density = resources.displayMetrics.density
        targetCenterX = targetX ?: (width / 2f)
        targetCenterY = targetY ?: (height * 0.22f)
        targetRadius = targetRadiusPx ?: (48f * density)

        initParticles()

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 850
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { va ->
                progress = va.animatedFraction
                if (progress >= 0.90f && !isAssembled) {
                    isAssembled = true
                    onAssembled()
                }
                if (progress > 0.88f) {
                    val shockwaveProgress = (progress - 0.88f) / 0.12f
                    shockwaveRadius = targetRadius * 0.8f + shockwaveProgress * (targetRadius * 2.8f)
                    shockwaveAlpha = ((1f - shockwaveProgress) * 220).toInt()
                }
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animate().alpha(0f).setDuration(200).withEndAction {
                        visibility = GONE
                    }.start()
                }
            })
        }
        animator.start()
    }

    private fun initParticles() {
        particles.clear()
        val density = resources.displayMetrics.density

        // Only White and Orange colors
        val colors = intArrayOf(
            Color.parseColor("#FFFFFF"), // Pure Diamond White
            Color.parseColor("#FFF3E0"), // Warm White Core
            Color.parseColor("#FF6D00"), // Stark Repulsor Orange
            Color.parseColor("#FF9100")  // Vibrant Amber Orange
        )

        val particleCount = 130
        val cx = targetCenterX
        val cy = targetCenterY

        for (i in 0 until particleCount) {
            // Fullscreen start positions:
            // 50% distributed across display, 50% incoming from outer borders
            val (sx, sy) = if (Random.nextBoolean()) {
                Pair(
                    Random.nextFloat() * width,
                    Random.nextFloat() * height
                )
            } else {
                val edge = Random.nextInt(4)
                when (edge) {
                    0 -> Pair(Random.nextFloat() * width, -30f * density)
                    1 -> Pair(Random.nextFloat() * width, height + 30f * density)
                    2 -> Pair(-30f * density, Random.nextFloat() * height)
                    else -> Pair(width + 30f * density, Random.nextFloat() * height)
                }
            }

            val startR = hypot(sx - cx, sy - cy)
            val startA = atan2(sy - cy, sx - cx)

            // Target positions assemble precisely into Arc Reactor concentric rings and center core
            val ringSelector = Random.nextFloat()
            val (targetR, pColor) = when {
                ringSelector < 0.28f -> {
                    // Center core: pure white
                    Pair(Random.nextFloat() * (targetRadius * 0.25f), colors[0])
                }
                ringSelector < 0.65f -> {
                    // Inner ring: white & orange
                    Pair(
                        targetRadius * 0.6f + (Random.nextFloat() - 0.5f) * 4f * density,
                        if (Random.nextBoolean()) colors[2] else colors[1]
                    )
                }
                else -> {
                    // Outer ring: repulsor orange
                    Pair(
                        targetRadius + (Random.nextFloat() - 0.5f) * 6f * density,
                        if (Random.nextFloat() > 0.35f) colors[2] else colors[3]
                    )
                }
            }

            val targetA = (i.toFloat() / particleCount) * 2f * Math.PI.toFloat() + (Random.nextFloat() * 0.15f)
            val spiralRot = (Random.nextFloat() - 0.5f) * 1.6f

            val pSize = (0.7f + Random.nextFloat() * 1.1f) * density

            particles.add(
                Particle(
                    startRadius = startR,
                    startAngle = startA,
                    targetRadius = targetR,
                    targetAngle = targetA,
                    spiralRotations = spiralRot,
                    size = pSize,
                    color = pColor
                )
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (particles.isEmpty()) return

        val currentPoints = ArrayList<Pair<Float, Float>>(particles.size)
        val t = progress
        val cx = targetCenterX
        val cy = targetCenterY

        for (p in particles) {
            // Smoothly interpolate polar radius and angle to converge directly on Arc Reactor center
            val currR = p.startRadius + (p.targetRadius - p.startRadius) * t
            val currAngle = p.startAngle + (p.targetAngle - p.startAngle) * t + (1f - t) * p.spiralRotations

            val currX = cx + currR * cos(currAngle)
            val currY = cy + currR * sin(currAngle)
            currentPoints.add(Pair(currX, currY))

            particlePaint.color = p.color
            particlePaint.alpha = (70 + t * 185).toInt().coerceIn(0, 255)
            canvas.drawCircle(currX, currY, p.size, particlePaint)
        }

        // Connective nanotech energy lines between converging particles
        val maxConnectDist = 34f * resources.displayMetrics.density
        linePaint.color = Color.parseColor("#FFA000")
        val sampleStep = 2
        for (i in 0 until currentPoints.size step sampleStep) {
            val (x1, y1) = currentPoints[i]
            for (j in (i + 1) until currentPoints.size step sampleStep) {
                val (x2, y2) = currentPoints[j]
                val d = hypot(x1 - x2, y1 - y2)
                if (d < maxConnectDist) {
                    val lineAlpha = ((1f - d / maxConnectDist) * (1f - progress * 0.4f) * 85).toInt()
                    if (lineAlpha > 0) {
                        linePaint.alpha = lineAlpha
                        canvas.drawLine(x1, y1, x2, y2, linePaint)
                    }
                }
            }
        }

        // Shockwave expansion exactly from Arc Reactor center
        if (shockwaveAlpha > 0) {
            shockwavePaint.alpha = shockwaveAlpha
            canvas.drawCircle(cx, cy, shockwaveRadius, shockwavePaint)
        }
    }
}
