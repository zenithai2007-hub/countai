package com.zenithai.countai.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/**
 * Minimal bar chart drawn with plain Canvas — no chart library needed.
 * Shows label -> value bars with the CountAI accent color.
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var entries: List<Pair<String, Int>> = emptyList()

    private val barPaint = Paint().apply {
        color = 0xFFFFC107.toInt(); isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = 0xFF616161.toInt(); textSize = 30f; isAntiAlias = true
    }
    private val valuePaint = Paint().apply {
        color = 0xFF212121.toInt(); textSize = 30f; isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }

    fun setData(data: List<Pair<String, Int>>) {
        entries = data.take(8)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (entries.isEmpty() || width == 0) return
        val maxVal = max(1, entries.maxOf { it.second })
        val rowH = height.toFloat() / entries.size
        val labelW = width * 0.38f
        for ((i, entry) in entries.withIndex()) {
            val top = i * rowH
            val centerY = top + rowH / 2f
            val label = entry.first.replaceFirstChar { it.uppercase() }.take(12)
            canvas.drawText(label, 0f, centerY + 10f, textPaint)
            val barW = (width - labelW - 90f) * (entry.second.toFloat() / maxVal)
            canvas.drawRoundRect(
                labelW, top + rowH * 0.25f, labelW + max(barW, 8f), top + rowH * 0.75f,
                8f, 8f, barPaint,
            )
            canvas.drawText(
                entry.second.toString(), labelW + max(barW, 8f) + 12f, centerY + 10f, valuePaint
            )
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Fixed row height so the chart sizes itself.
        val h = (entries.size.coerceAtLeast(1) * 64 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            resolveSize(h, heightMeasureSpec),
        )
    }
}
