package com.zenithai.countai.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds a professional, shareable result card as a bitmap (dark theme,
 * subtle CountAI branding, per-type counts, total, date) and shares it
 * through the system chooser — WhatsApp, Facebook, email, anything.
 */
object ResultCard {

    fun build(context: Context, title: String, counts: Map<String, Int>): Bitmap {
        val density = context.resources.displayMetrics.density
        val w = (360 * density).toInt()
        val rows = counts.toList().sortedByDescending { it.second }
        val h = (200 * density).toInt() + rows.size * (34 * density).toInt()

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFFFFFFF.toInt())

        val accent = Paint().apply { color = 0xFFE6A800.toInt() }
        c.drawRect(0f, 0f, w.toFloat(), 8 * density, accent)

        val titlePaint = Paint().apply {
            color = 0xFF212121.toInt(); textSize = 22 * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
        }
        val rowPaint = Paint().apply {
            color = 0xFF424242.toInt(); textSize = 17 * density; isAntiAlias = true
        }
        val countPaint = Paint().apply {
            color = 0xFFE6A800.toInt(); textSize = 20 * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
        }
        val smallPaint = Paint().apply {
            color = 0xFF757575.toInt(); textSize = 12 * density; isAntiAlias = true
        }

        var y = 52 * density
        c.drawText(title, 24 * density, y, titlePaint); y += 34 * density
        for ((label, count) in rows) {
            val name = label.replaceFirstChar { it.uppercase() }
            c.drawText(name, 24 * density, y, rowPaint)
            val cs = count.toString()
            c.drawText(cs, w - 24 * density - countPaint.measureText(cs), y, countPaint)
            y += 34 * density
        }
        y += 8 * density
        c.drawRect(24 * density, y - 24 * density, w - 24 * density, y - 22 * density, smallPaint)
        val total = "Total: ${counts.values.sum()}"
        c.drawText(total, 24 * density, y, countPaint); y += 28 * density
        val date = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date())
        c.drawText(date, 24 * density, y, smallPaint); y += 22 * density
        c.drawText("CountAI — See it. Count it. Done.", 24 * density, y, smallPaint)
        return bmp
    }

    /** Saves the card to cache and fires a share chooser for the image. */
    fun share(context: Context, title: String, counts: Map<String, Int>) {
        val bmp = build(context, title, counts)
        val file = File(context.cacheDir, "countai_card_${System.currentTimeMillis()}.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }
}
