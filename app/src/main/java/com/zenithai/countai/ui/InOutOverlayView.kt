package com.zenithai.countai.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.zenithai.countai.detection.TrackedObject
import kotlin.math.abs
import kotlin.math.min

/**
 * Detection boxes plus a draggable horizontal counting line.
 *
 * Boxes live in normalized square-model coordinates; the line position is
 * stored the same way ([lineYNorm], 0..1) so the LineCounter and the drawing
 * always agree. Drag near the line (within 48dp) to move it.
 */
class InOutOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var objects: List<TrackedObject> = emptyList()
    private var frameW = 1
    private var frameH = 1
    private var cropX = 0
    private var cropY = 0
    private var cropSize = 1

    /** Line position in normalized square coordinates (0..1). */
    var lineYNorm: Float = 0.5f
        private set

    var onLineMoved: ((Float) -> Unit)? = null

    private var dragging = false

    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE; strokeWidth = 5f; isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE; textSize = 36f; isAntiAlias = true
    }
    private val labelBgPaint = Paint().apply { isAntiAlias = true }
    private val linePaint = Paint().apply {
        color = 0xFFFFC107.toInt(); strokeWidth = 6f; isAntiAlias = true
    }
    private val lineLabelPaint = Paint().apply {
        color = 0xFFFFC107.toInt(); textSize = 32f; isAntiAlias = true
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private val palette = intArrayOf(
        0xFF00E676.toInt(), 0xFF40C4FF.toInt(), 0xFFFF5252.toInt(), 0xFFE040FB.toInt(),
    )

    fun setDetections(
        objects: List<TrackedObject>,
        frameWidth: Int, frameHeight: Int,
        cropX: Int, cropY: Int, cropSize: Int,
    ) {
        this.objects = objects
        this.frameW = frameWidth.coerceAtLeast(1)
        this.frameH = frameHeight.coerceAtLeast(1)
        this.cropX = cropX
        this.cropY = cropY
        this.cropSize = cropSize.coerceAtLeast(1)
        invalidate()
    }

    fun clear() {
        objects = emptyList()
        invalidate()
    }

    /** View geometry helpers (same letterbox math as DetectionOverlayView). */
    private fun scale(): Float = min(width.toFloat() / frameW, height.toFloat() / frameH)
    private fun dx(): Float = (width - frameW * scale()) / 2f
    private fun dy(): Float = (height - frameH * scale()) / 2f

    /** Normalized square Y -> view Y. */
    fun normToViewY(yn: Float): Float = dy() + (cropY + yn * cropSize) * scale()

    /** View Y -> normalized square Y (clamped). */
    private fun viewToNormY(vy: Float): Float =
        ((vy - dy()) / scale() - cropY) / cropSize.toFloat().coerceAtLeast(0.01f)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val lineViewY = normToViewY(lineYNorm)
        val touchSlop = 48f * resources.displayMetrics.density
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (abs(event.y - lineViewY) < touchSlop) {
                    dragging = true
                    parent.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    lineYNorm = viewToNormY(event.y).coerceIn(0.05f, 0.95f)
                    onLineMoved?.invoke(lineYNorm)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
            }
        }
        return dragging || super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        // Counting line across the full width.
        val ly = normToViewY(lineYNorm)
        canvas.drawLine(0f, ly, width.toFloat(), ly, linePaint)
        canvas.drawText("IN ↓", 24f, ly - 16f, lineLabelPaint)
        canvas.drawText("↑ OUT", 24f, ly + 48f, lineLabelPaint)

        val s = scale()
        val dx = dx()
        val dy = dy()
        for (o in objects) {
            val color = palette[(o.label.hashCode() and Int.MAX_VALUE) % palette.size]
            val l = dx + (cropX + o.box.left * cropSize) * s
            val t = dy + (cropY + o.box.top * cropSize) * s
            val r = dx + (cropX + o.box.right * cropSize) * s
            val b = dx + (cropY + o.box.bottom * cropSize) * s
            boxPaint.color = color
            canvas.drawRoundRect(l, t, r, b, 12f, 12f, boxPaint)
            // Centroid dot — makes line crossings visible.
            val cx = (l + r) / 2f
            val cy = (t + b) / 2f
            labelBgPaint.color = color
            canvas.drawCircle(cx, cy, 8f, labelBgPaint)
            val text = "${o.label} #${o.id}"
            val tw = textPaint.measureText(text)
            labelBgPaint.color = color
            val bgTop = (t - 50f).coerceAtLeast(0f)
            canvas.drawRoundRect(l, bgTop, l + tw + 24f, t, 10f, 10f, labelBgPaint)
            canvas.drawText(text, l + 12f, t - 12f, textPaint)
        }
    }

    /** Normalized square box -> view rect (for hit-testing etc.). */
    fun toViewRect(box: RectF): RectF {
        val s = scale()
        return RectF(
            dx() + (cropX + box.left * cropSize) * s,
            dy() + (cropY + box.top * cropSize) * s,
            dx() + (cropX + box.right * cropSize) * s,
            dy() + (cropY + box.bottom * cropSize) * s,
        )
    }
}
