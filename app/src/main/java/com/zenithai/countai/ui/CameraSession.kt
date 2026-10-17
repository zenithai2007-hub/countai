package com.zenithai.countai.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.zenithai.countai.util.FrameConverter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Reusable CameraX session: binds preview + an analyzer that delivers
 * center-cropped square bitmaps (the model's input area) on a background
 * thread, throttled to [fps].
 *
 * The [FrameListener] MUST recycle [square] when done with it.
 */
class CameraSession(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
) {

    interface FrameListener {
        fun onFrame(square: Bitmap)
        fun onCameraError(message: String)
    }

    var listener: FrameListener? = null
    var fps: Int = 10

    private var executor: ExecutorService? = null
    private var provider: ProcessCameraProvider? = null
    private var lastMs = 0L

    val isRunning: Boolean get() = provider != null

    fun start() {
        if (isRunning) return
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val p = future.get()
                provider = p
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(getExecutor(), analyzer)
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                Log.e(TAG, "camera start failed", e)
                listener?.onCameraError(e.message ?: "camera_error")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        try {
            provider?.unbindAll()
        } catch (e: Exception) {
            Log.w(TAG, "unbind failed", e)
        }
        provider = null
    }

    fun release() {
        stop()
        executor?.shutdown()
        executor = null
    }

    /** Last preview frame (for saving with a result). Caller recycles. */
    fun captureStill(): Bitmap? = try {
        previewView.bitmap
    } catch (e: Exception) {
        Log.w(TAG, "captureStill failed", e)
        null
    }

    private fun getExecutor(): ExecutorService {
        var e = executor
        if (e == null || e.isShutdown) {
            e = Executors.newSingleThreadExecutor()
            executor = e
        }
        return e
    }

    private val analyzer = ImageAnalysis.Analyzer { proxy ->
        try {
            val now = SystemClock.uptimeMillis()
            if (now - lastMs >= 1000L / fps.coerceIn(1, 30)) {
                lastMs = now
                val image = proxy.image
                if (image != null) {
                    var bmp = FrameConverter.yuvToBitmap(image)
                    if (bmp != null) {
                        bmp = FrameConverter.rotate(bmp, proxy.imageInfo.rotationDegrees)
                        val square = FrameConverter.centerSquare(bmp)
                        listener?.onFrame(square)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "analyze failed", e)
        } finally {
            proxy.close()
        }
    }

    companion object {
        private const val TAG = "CameraSession"
    }
}
