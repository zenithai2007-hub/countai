package com.zenithai.countai.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.zenithai.countai.detection.TrackedObject
import kotlin.math.min

/**
 * Draws detection boxes + labels on top of the camera preview.
 *
 * Boxes arrive in NORMALIZED coordinates of the square model input. The
 * fragment tells us how that square was cropped from the camera frame
 * ([cropX], [cropY], [cropSize]) and the frame size, so we can map boxes
 * back onto the preview with the same letterboxing the PreviewView uses.
 */
class DetectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var objects: List<TrackedObject> = emptyList()
    /** Live per-class counts for the current frame ("bottle ×3"). */
    private var classCounts: Map<String, Int> = emptyMap()
    private var frameW = 1
    private var frameH = 1
    private var cropX = 0
    private var cropY = 0
    private var cropSize = 1

    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        isAntiAlias = true
    }
    private val labelBgPaint = Paint().apply { isAntiAlias = true }

    private val palette = intArrayOf(
        0xFF00E676.toInt(), 0xFF40C4FF.toInt(), 0xFFFFC107.toInt(),
        0xFFFF5252.toInt(), 0xFFE040FB.toInt(), 0xFF69F0AE.toInt(),
        0xFFFFAB40.toInt(), 0xFF7C4DFF.toInt(),
    )

    fun setDetections(
        objects: List<TrackedObject>,
        frameWidth: Int,
        frameHeight: Int,
        cropX: Int,
        cropY: Int,
        cropSize: Int,
    ) {
        this.objects = objects
        this.classCounts = objects.groupingBy { it.label }.eachCount()
        this.frameW = frameWidth.coerceAtLeast(1)
        this.frameH = frameHeight.coerceAtLeast(1)
        this.cropX = cropX
        this.cropY = cropY
        this.cropSize = cropSize.coerceAtLeast(1)
        invalidate()
    }

    fun clear() {
        objects = emptyList()
        classCounts = emptyMap()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (objects.isEmpty() || width == 0 || height == 0) return

        // Letterbox: fit the whole camera frame into this view.
        val scale = min(width.toFloat() / frameW, height.toFloat() / frameH)
        val dx = (width - frameW * scale) / 2f
        val dy = (height - frameH * scale) / 2f

        for (o in objects) {
            val color = palette[(o.label.hashCode() and Int.MAX_VALUE) % palette.size]
            val l = dx + (cropX + o.box.left * cropSize) * scale
            val t = dy + (cropY + o.box.top * cropSize) * scale
            val r = dx + (cropX + o.box.right * cropSize) * scale
            val b = dy + (cropY + o.box.bottom * cropSize) * scale

            boxPaint.color = color
            canvas.drawRoundRect(l, t, r, b, 12f, 12f, boxPaint)

            val text = "${o.label} ×${classCounts[o.label] ?: 1}"
            val tw = textPaint.measureText(text)
            labelBgPaint.color = color
            val bgTop = (t - 54f).coerceAtLeast(0f)
            canvas.drawRoundRect(l, bgTop, l + tw + 24f, t, 10f, 10f, labelBgPaint)
            canvas.drawText(text, l + 12f, t - 14f, textPaint)
        }
    }
}
