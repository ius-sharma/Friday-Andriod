package com.personal.friday

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

class ScreenCaptureManager(
    private val context: Context,
    private val mediaProjection: MediaProjection
) {
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var screenWidth = 720
    private var screenHeight = 1280
    private var screenDensity = 320

    init {
        setupDimensions()
        setupVirtualDisplay()
    }

    private fun setupDimensions() {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
    }

    @SuppressLint("WrongConstant")
    private fun setupVirtualDisplay() {
        try {
            mediaProjection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    android.util.Log.i("ScreenCaptureManager", "MediaProjection session ended")
                }
            }, android.os.Handler(android.os.Looper.getMainLooper()))
        } catch (e: Exception) {
            android.util.Log.w("ScreenCaptureManager", "MediaProjection callback note", e)
        }

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "FridayScreenCapture",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
    }

    fun drainBuffer() {
        try {
            var image: Image?
            do {
                image = imageReader?.acquireLatestImage()
                image?.close()
            } while (image != null)
        } catch (_: Exception) {}
    }

    suspend fun captureScreen(): Bitmap? = withContext(Dispatchers.IO) {
        // Wait brief frame time to ensure VirtualDisplay rendered current frame
        delay(120)

        var image: Image? = null
        try {
            image = imageReader?.acquireLatestImage()
            if (image == null) {
                // Try one more quick retry
                delay(100)
                image = imageReader?.acquireLatestImage()
            }
            if (image == null) return@withContext null

            val planes = image.planes
            val buffer: ByteBuffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            // Crop out padding if present
            if (rowPadding != 0) {
                val cleanBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                bitmap.recycle()
                return@withContext cleanBitmap
            }

            return@withContext bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        } finally {
            image?.close()
        }
    }

    fun release() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            mediaProjection.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
