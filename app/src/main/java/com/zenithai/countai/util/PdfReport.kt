package com.zenithai.countai.util

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates a clean one-page PDF report after a counting session using only
 * the framework PdfDocument — no new dependencies.
 */
object PdfReport {

    fun generate(
        context: Context,
        title: String,
        counts: Map<String, Int>,
        extraLines: List<String> = emptyList(),
    ): File {
        val doc = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 @72dpi
            val page = doc.startPage(pageInfo)
            val c = page.canvas
            val margin = 48f
            var y = 72f

            val titlePaint = Paint().apply {
                textSize = 26f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = 0xFF000000.toInt()
            }
            val headPaint = Paint().apply {
                textSize = 14f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = 0xFF000000.toInt()
            }
            val bodyPaint = Paint().apply { textSize = 14f; color = 0xFF333333.toInt() }
            val smallPaint = Paint().apply { textSize = 11f; color = 0xFF777777.toInt() }
            val linePaint = Paint().apply {
                color = 0xFF00C853.toInt(); strokeWidth = 3f
            }

            c.drawText("CountAI", margin, y, titlePaint); y += 10f
            c.drawLine(margin, y, 595 - margin, y, linePaint); y += 30f
            c.drawText(title, margin, y, headPaint); y += 24f
            val date = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date())
            c.drawText(date, margin, y, smallPaint); y += 30f

            val rows = counts.toList().sortedByDescending { it.second }
            if (rows.isEmpty()) {
                c.drawText("No objects counted.", margin, y, bodyPaint); y += 24f
            } else {
                for ((label, count) in rows) {
                    if (y > 780) break
                    c.drawText(label.replaceFirstChar { it.uppercase() }, margin, y, bodyPaint)
                    val countText = count.toString()
                    c.drawText(countText, 595 - margin - bodyPaint.measureText(countText), y, headPaint)
                    y += 24f
                }
                y += 8f
                c.drawLine(margin, y, 595 - margin, y, linePaint); y += 26f
                c.drawText("Total objects: ${counts.values.sum()}", margin, y, headPaint); y += 30f
            }
            for (line in extraLines) {
                if (y > 780) break
                c.drawText(line, margin, y, smallPaint); y += 20f
            }
            y += 10f
            c.drawText(
                "AI counts are estimates — verify important counts. Generated on-device by CountAI.",
                margin, 810f, smallPaint,
            )
            doc.finishPage(page)
            val file = File(context.cacheDir, "countai_report_${System.currentTimeMillis()}.pdf")
            file.outputStream().use { doc.writeTo(it) }
            return file
        } finally {
            doc.close()
        }
    }
}
