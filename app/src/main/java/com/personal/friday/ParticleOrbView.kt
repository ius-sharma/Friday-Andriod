package com.personal.friday

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

class ParticleOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class Point3D(
        val x: Float,
        val y: Float,
        val z: Float,
        val color: Int,
        val baseSize: Float,
        val isCore: Boolean
    )

    private val spherePoints = mutableListOf<Point3D>()
    private var rotY = 0f
    private var rotX = 0.3f
    private var breathePhase = 0f
    private var pulseScale = 1.0f

    private var animator: ValueAnimator? = null
    var onOrbClickListener: (() -> Unit)? = null

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.0f
        color = Color.parseColor("#FFA000")
    }

    private val coreGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#33FF6D00")
    }

    init {
        initSphere()
    }

    private fun initSphere() {
        spherePoints.clear()
        val density = resources.displayMetrics.density

        val outerOrange = Color.parseColor("#FF6D00")
        val amberOrange = Color.parseColor("#FFA000")
        val pureWhite = Color.parseColor("#FFFFFF")
        val warmWhite = Color.parseColor("#FFF3E0")

        // 1. Outer Shell: 80 points distributed on spherical surface (Fibonacci sphere)
        val outerCount = 80
        val phi = Math.PI * (3.0 - sqrt(5.0)) // golden ratio angle

        for (i in 0 until outerCount) {
            val y = 1f - (i.toFloat() / (outerCount - 1f)) * 2f
            val radiusAtY = sqrt(1f - y * y)
            val theta = (phi * i).toFloat()
            val x = cos(theta) * radiusAtY
            val z = sin(theta) * radiusAtY

            val color = if (i % 3 == 0) pureWhite else if (i % 2 == 0) outerOrange else amberOrange
            val size = (1.6f + (i % 3) * 0.5f) * density

            spherePoints.add(Point3D(x, y, z, color, size, false))
        }

        // 2. Inner Core: 40 points condensed in inner core (White & Orange)
        val innerCount = 40
        for (i in 0 until innerCount) {
            val y = 1f - (i.toFloat() / (innerCount - 1f)) * 2f
            val radiusAtY = sqrt(1f - y * y)
            val theta = (phi * i * 1.5f).toFloat()
            val rFactor = 0.48f + (i % 4) * 0.05f
            val x = cos(theta) * radiusAtY * rFactor
            val z = sin(theta) * radiusAtY * rFactor
            val innerY = y * rFactor

            val color = if (i % 2 == 0) pureWhite else warmWhite
            val size = (1.2f + (i % 2) * 0.6f) * density

            spherePoints.add(Point3D(x, innerY, z, color, size, true))
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startLoop()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopLoop()
    }

    fun startLoop() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                rotY += 0.007f
                rotX += 0.003f
                breathePhase += 0.020f
                invalidate()
            }
        }
        animator?.start()
    }

    fun stopLoop() {
        animator?.cancel()
        animator = null
    }

    fun triggerPulse() {
        val pulseAnim = ValueAnimator.ofFloat(1.22f, 1.0f).apply {
            duration = 450
            interpolator = OvershootInterpolator(2.5f)
            addUpdateListener { va ->
                pulseScale = va.animatedValue as Float
                invalidate()
            }
        }
        pulseAnim.start()
        triggerHaptic()
    }

    private fun triggerHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {}
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val cx = width / 2f
            val cy = height / 2f
            val dist = hypot(event.x - cx, event.y - cy)
            val orbRadius = width * 0.38f
            if (dist <= orbRadius * 1.3f) {
                triggerPulse()
                onOrbClickListener?.invoke()
                return true
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val density = resources.displayMetrics.density

        val breathe = 1f + 0.05f * sin(breathePhase)
        val baseRadius = (width.coerceAtMost(height) * 0.36f) * breathe * pulseScale

        // Draw soft ambient core glow aura
        coreGlowPaint.alpha = (35 + 20 * sin(breathePhase)).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, baseRadius * 0.85f, coreGlowPaint)

        val sinY = sin(rotY)
        val cosY = cos(rotY)
        val sinX = sin(rotX)
        val cosX = cos(rotX)

        val focalLength = 320f * density

        data class ProjectedPoint(
            val px: Float,
            val py: Float,
            val pz: Float,
            val size: Float,
            val color: Int,
            val isCore: Boolean
        )

        val projectedList = ArrayList<ProjectedPoint>(spherePoints.size)

        for (pt in spherePoints) {
            // Y rotation
            val x1 = pt.x * cosY + pt.z * sinY
            val z1 = -pt.x * sinY + pt.z * cosY

            // X rotation
            val y2 = pt.y * cosX - z1 * sinX
            val z2 = pt.y * sinX + z1 * cosX

            val currentR = if (pt.isCore) baseRadius * 0.45f else baseRadius

            val worldX = x1 * currentR
            val worldY = y2 * currentR
            val worldZ = z2 * currentR

            val scale = focalLength / (focalLength + worldZ + baseRadius)
            val projX = cx + worldX * scale
            val projY = cy + worldY * scale
            val projSize = pt.baseSize * scale

            projectedList.add(ProjectedPoint(projX, projY, worldZ, projSize, pt.color, pt.isCore))
        }

        // Sort by Z for realistic depth occlusion (back points drawn first)
        projectedList.sortBy { it.pz }

        // Draw orbital lines between nearby outer surface points
        val maxLineDist = 32f * density
        val outerProjected = projectedList.filter { !it.isCore }
        for (i in 0 until outerProjected.size step 2) {
            val p1 = outerProjected[i]
            if (p1.pz < -baseRadius * 0.2f) continue // Skip back hemisphere lines to keep uncluttered
            for (j in (i + 1) until outerProjected.size step 2) {
                val p2 = outerProjected[j]
                val d = hypot(p1.px - p2.px, p1.py - p2.py)
                if (d < maxLineDist) {
                    val alpha = ((1f - d / maxLineDist) * 75).toInt().coerceIn(0, 255)
                    linePaint.alpha = alpha
                    canvas.drawLine(p1.px, p1.py, p2.px, p2.py, linePaint)
                }
            }
        }

        // Draw dots
        for (p in projectedList) {
            dotPaint.color = p.color
            // Front-facing dots are full opacity, rear dots are dimmer
            val depthRatio = (p.pz + baseRadius) / (2f * baseRadius)
            val alpha = if (p.isCore) {
                (180 + 75 * depthRatio).toInt().coerceIn(0, 255)
            } else {
                (80 + 175 * depthRatio).toInt().coerceIn(0, 255)
            }
            dotPaint.alpha = alpha
            canvas.drawCircle(p.px, p.py, p.size, dotPaint)
        }
    }
}
