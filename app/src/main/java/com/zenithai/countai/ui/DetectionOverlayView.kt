package com.zenithai.countai.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.zenithai.countai.detection.TrackedObject
import kotlin.math.min

/**
 * Draws detection boxes + labels on top of the camera preview.
 *
 * Every detected object gets three things so the user can tell exactly
 * which box is which:
 * 1. a tight rounded box (3.5dp stroke, high-contrast class color),
 * 2. a "label ×count" pill above the box,
 * 3. a big number badge (1, 2, 3…) on a solid dark pill tucked inside the
 *    box's top-left corner — readable on any background.
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

    private val dp = context.resources.displayMetrics.density

    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * dp
        isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 14f * dp
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }
    private val labelBgPaint = Paint().apply { isAntiAlias = true }

    /** Solid dark pill behind the number badge — readable on any background. */
    private val badgeBgPaint = Paint().apply {
        color = Color.parseColor("#DD1A1A1A")
        isAntiAlias = true
    }
    private val badgeTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 18f * dp
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

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

        // Stable numbering: top-to-bottom, then left-to-right.
        val ordered = objects.sortedWith(compareBy({ it.box.top }, { it.box.left }))

        for ((index, o) in ordered.withIndex()) {
            val color = palette[(o.label.hashCode() and Int.MAX_VALUE) % palette.size]
            val l = dx + (cropX + o.box.left * cropSize) * scale
            val t = dy + (cropY + o.box.top * cropSize) * scale
            val r = dx + (cropX + o.box.right * cropSize) * scale
            val b = dy + (cropY + o.box.bottom * cropSize) * scale

            // 1. Tight rounded box, 3.5dp stroke, high-contrast color.
            boxPaint.color = color
            val corner = 4f * dp
            canvas.drawRoundRect(l, t, r, b, corner, corner, boxPaint)

            // 2. "label ×count" pill above the box (kept inside the view).
            val text = "${o.label} ×${classCounts[o.label] ?: 1}"
            val tw = textPaint.measureText(text)
            val labelH = 26f * dp
            val labelPad = 10f * dp
            val labelW = tw + labelPad * 2
            val labelL = l.coerceIn(0f, (width - labelW).coerceAtLeast(0f))
            val bgTop = (t - labelH - 4f * dp).coerceAtLeast(0f)
            labelBgPaint.color = color
            canvas.drawRoundRect(
                labelL, bgTop, labelL + labelW, bgTop + labelH,
                labelH / 2, labelH / 2, labelBgPaint,
            )
            val fm = textPaint.fontMetrics
            val textY = bgTop + labelH / 2 - (fm.ascent + fm.descent) / 2
            val oldAlign = textPaint.textAlign
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(text, labelL + labelPad, textY, textPaint)
            textPaint.textAlign = oldAlign

            // 3. Number badge: big white number on a solid dark pill,
            //    tucked inside the box's top-left corner.
            val num = (index + 1).toString()
            val nw = badgeTextPaint.measureText(num)
            val badgePadH = 9f * dp
            val badgeH = 30f * dp
            val badgeW = nw + badgePadH * 2
            val inset = 6f * dp
            val bx = (l + inset).coerceIn(l, (r - badgeW).coerceAtLeast(l))
            val by = (t + inset).coerceIn(t, (b - badgeH).coerceAtLeast(t))
            canvas.drawRoundRect(
                bx, by, bx + badgeW, by + badgeH,
                badgeH / 2, badgeH / 2, badgeBgPaint,
            )
            val bfm = badgeTextPaint.fontMetrics
            val numY = by + badgeH / 2 - (bfm.ascent + bfm.descent) / 2
            canvas.drawText(num, bx + badgeW / 2, numY, badgeTextPaint)
        }
    }
}
