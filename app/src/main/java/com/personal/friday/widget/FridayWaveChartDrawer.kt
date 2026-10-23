package com.personal.friday.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

object FridayWaveChartDrawer {

    fun drawWaveChart(
        hourlyList: List<HourlyForecastItem>,
        width: Int = 540,
        height: Int = 230
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (hourlyList.isEmpty()) {
            return bitmap
        }

        val topPadding = 38f
        val bottomPadding = 28f
        val leftPadding = 20f
        val rightPadding = 20f

        val chartWidth = width - leftPadding - rightPadding
        val chartHeight = height - topPadding - bottomPadding
        val bottomY = topPadding + chartHeight

        // 1. Temperature Range Bounds
        val minTemp = hourlyList.minOf { it.tempValue }
        val maxTemp = hourlyList.maxOf { it.tempValue }
        val tempSpan = if (maxTemp - minTemp < 2f) 2f else (maxTemp - minTemp)
        val adjustedMin = minTemp - tempSpan * 0.18f
        val adjustedMax = maxTemp + tempSpan * 0.32f
        val adjustedRange = adjustedMax - adjustedMin

        // 2. Secondary Range Bounds (Rain / Humidity curve)
        val minSec = hourlyList.minOf { it.secondaryValue }
        val maxSec = hourlyList.maxOf { it.secondaryValue }
        val secSpan = if (maxSec - minSec < 5f) 10f else (maxSec - minSec)
        val adjSecMin = minSec - secSpan * 0.15f
        val adjSecMax = maxSec + secSpan * 0.30f
        val adjSecRange = adjSecMax - adjSecMin

        val count = hourlyList.size
        val points = mutableListOf<PointF>()
        val secPoints = mutableListOf<PointF>()

        for (i in 0 until count) {
            val px = leftPadding + (i.toFloat() / (count - 1).coerceAtLeast(1)) * chartWidth
            val py = topPadding + chartHeight * (1f - ((hourlyList[i].tempValue - adjustedMin) / adjustedRange)).coerceIn(0.08f, 0.92f)
            points.add(PointF(px, py))

            val spy = topPadding + chartHeight * (1f - ((hourlyList[i].secondaryValue - adjSecMin) / adjSecRange)).coerceIn(0.18f, 0.88f)
            secPoints.add(PointF(px, spy))
        }

        // 3. Subtle Background Grid Lines
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#10FFFFFF")
            strokeWidth = 1.2f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(leftPadding, topPadding + chartHeight * 0.35f, width - rightPadding, topPadding + chartHeight * 0.35f, gridPaint)
        canvas.drawLine(leftPadding, topPadding + chartHeight * 0.70f, width - rightPadding, topPadding + chartHeight * 0.70f, gridPaint)

        // 4. Secondary Dotted Wave Curve (Image 2 Coral / Red Dotted Curve)
        if (secPoints.size >= 2) {
            val secPath = Path().apply {
                moveTo(secPoints[0].x, secPoints[0].y)
                for (i in 0 until secPoints.size - 1) {
                    val p1 = secPoints[i]
                    val p2 = secPoints[i + 1]
                    val midX = (p1.x + p2.x) / 2f
                    cubicTo(midX, p1.y, midX, p2.y, p2.x, p2.y)
                }
            }

            val secPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#D9FF5252") // Coral red from Image 2
                strokeWidth = 2.5f
                style = Paint.Style.STROKE
                pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
            }
            canvas.drawPath(secPath, secPaint)
        }

        // 5. Primary Smooth Spline Wave (Cyber-Orange)
        if (points.size >= 2) {
            val strokePath = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 0 until points.size - 1) {
                    val p1 = points[i]
                    val p2 = points[i + 1]
                    val midX = (p1.x + p2.x) / 2f
                    cubicTo(midX, p1.y, midX, p2.y, p2.x, p2.y)
                }
            }

            // A: Shaded Gradient Fill Underneath
            val fillPath = Path(strokePath).apply {
                lineTo(points.last().x, bottomY)
                lineTo(points.first().x, bottomY)
                close()
            }

            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = LinearGradient(
                    0f, topPadding,
                    0f, bottomY,
                    Color.parseColor("#38FF6D00"), // Soft Cyber Orange glow
                    Color.parseColor("#00FF6D00"), // Fading into AMOLED black
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawPath(fillPath, fillPaint)

            // B: Vibrant Stroke
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FF6D00")
                strokeWidth = 4.2f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            canvas.drawPath(strokePath, strokePaint)
        }

        // 6. Peak Marker & Floating Tooltip Pill (Image 2 "$4,509" Style)
        var peakIndex = 0
        var highestTemp = -999f
        for (i in hourlyList.indices) {
            if (hourlyList[i].tempValue > highestTemp) {
                highestTemp = hourlyList[i].tempValue
                peakIndex = i
            }
        }

        if (peakIndex in points.indices) {
            val peakX = points[peakIndex].x
            val peakY = points[peakIndex].y
            val peakTempInt = hourlyList[peakIndex].tempValue.toInt()

            // Vertical Dashed Guideline
            val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#50FFFFFF")
                strokeWidth = 2f
                style = Paint.Style.STROKE
                pathEffect = DashPathEffect(floatArrayOf(5f, 5f), 0f)
            }
            canvas.drawLine(peakX, peakY + 7f, peakX, bottomY, guidePaint)

            // Peak Dot Marker (Outer Orange Ring + Inner White Center)
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FF6D00")
                style = Paint.Style.FILL
            }
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawCircle(peakX, peakY, 6f, ringPaint)
            canvas.drawCircle(peakX, peakY, 3f, dotPaint)

            // Floating Tooltip Badge (Dynamic text-measured pill)
            val pillText = "▲ $peakTempInt°"
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 19f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }

            val textWidth = textPaint.measureText(pillText)
            val pillW = textWidth + 20f
            val pillH = 26f
            val pillCenterX = peakX.coerceIn(leftPadding + pillW / 2, width - rightPadding - pillW / 2)
            val pillTop = (peakY - 33f).coerceAtLeast(4f)
            val pillRect = RectF(pillCenterX - pillW / 2, pillTop, pillCenterX + pillW / 2, pillTop + pillH)

            // Pill Background
            val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#EE161620")
                style = Paint.Style.FILL
            }
            // Pill Border
            val pillBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#99FF6D00")
                strokeWidth = 1.6f
                style = Paint.Style.STROKE
            }

            canvas.drawRoundRect(pillRect, 6f, 6f, pillBgPaint)
            canvas.drawRoundRect(pillRect, 6f, 6f, pillBorderPaint)
            canvas.drawText(pillText, pillCenterX, pillTop + 18.5f, textPaint)
        }

        // 7. Time Axis Labels (along bottom)
        val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8E8E93")
            textSize = 16f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }

        // Draw labels for 3 or 4 representative timestamps
        val labelIndices = when {
            count >= 6 -> listOf(0, count / 3, (2 * count) / 3, count - 1)
            count >= 3 -> listOf(0, count / 2, count - 1)
            else -> hourlyList.indices.toList()
        }

        for (idx in labelIndices) {
            if (idx in points.indices) {
                val labelX = points[idx].x.coerceIn(leftPadding + 18f, width - rightPadding - 18f)
                val label = hourlyList[idx].timeLabel
                canvas.drawText(label, labelX, height - 5f, axisPaint)
            }
        }

        return bitmap
    }

    private data class PointF(val x: Float, val y: Float)
}
