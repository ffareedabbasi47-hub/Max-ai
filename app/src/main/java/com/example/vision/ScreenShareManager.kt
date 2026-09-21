package com.example.vision

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.ByteArrayOutputStream

/**
 * Captures periodic screenshots (roughly every 3 seconds) via MediaProjection — a lightweight
 * approximation of "screen sharing" using discrete image frames rather than continuous video
 * (which the Live session's realtime input isn't built to carry). Ported from the Iris project.
 */
class ScreenShareManager(
    private val context: Context,
    private val onFrame: (ByteArray) -> Unit
) {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private var captureRunnable: Runnable? = null
    private val intervalMs = 3000L

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() { stop() }
    }

    fun start(resultCode: Int, data: Intent, projectionManager: MediaProjectionManager) {
        stop()

        val projection = projectionManager.getMediaProjection(resultCode, data) ?: return
        mediaProjection = projection
        projection.registerCallback(projectionCallback, handler)

        val metrics = DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)
        val width = (metrics.widthPixels / 2).coerceAtLeast(1)
        val height = (metrics.heightPixels / 2).coerceAtLeast(1)
        val density = metrics.densityDpi

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader

        virtualDisplay = runCatching {
            projection.createVirtualDisplay(
                "MaxScreenShare", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface, null, handler
            )
        }.getOrNull()

        if (virtualDisplay == null) {
            stop()
            return
        }

        val runnable = object : Runnable {
            override fun run() {
                captureFrame(reader, width, height)
                if (isActive) handler.postDelayed(this, intervalMs)
            }
        }
        captureRunnable = runnable
        handler.postDelayed(runnable, 500)
    }

    private fun captureFrame(reader: ImageReader, width: Int, height: Int) {
        val image = runCatching { reader.acquireLatestImage() }.getOrNull() ?: return
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width

            val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)

            val cropped = if (rowPadding == 0) bitmap else Bitmap.createBitmap(bitmap, 0, 0, width, height)
            val out = ByteArrayOutputStream()
            cropped.compress(Bitmap.CompressFormat.JPEG, 70, out)
            onFrame(out.toByteArray())
        } catch (_: Exception) {
            // Dropped frame — not worth tearing down the session over one bad capture.
        } finally {
            image.close()
        }
    }

    fun stop() {
        captureRunnable?.let { handler.removeCallbacks(it) }
        captureRunnable = null
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        runCatching { mediaProjection?.unregisterCallback(projectionCallback) }
        mediaProjection?.stop()
        mediaProjection = null
    }

    val isActive: Boolean get() = mediaProjection != null
}
