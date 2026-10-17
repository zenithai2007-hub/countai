package com.zenithai.countai.detection

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF

/**
 * Cheap color classification inside a detection box.
 *
 * Samples a grid of pixels from the center of [box] (in the same normalized
 * 0..1 coordinates the detector uses), converts to HSV and majority-votes
 * into red / blue / green / yellow / black / white. Returns null when no
 * color wins clearly — the UI then treats it as "unknown", never as a
 * confident wrong answer.
 *
 * Honest limits: this is dominant-hue sampling, not real color
 * understanding. Mixed lighting, shadows and multicolored objects will
 * confuse it; the UI marks such cases instead of guessing.
 */
object ColorFilter {

    private const val GRID = 7

    fun classify(bitmap: Bitmap, box: RectF): String? {
        val l = (box.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val t = (box.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val r = (box.right * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val b = (box.bottom * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        if (r - l < 4 || b - t < 4) return null

        // Sample the middle 60% to dodge box edges/background.
        val il = l + ((r - l) * 0.2f).toInt()
        val it = t + ((b - t) * 0.2f).toInt()
        val ir = l + ((r - l) * 0.8f).toInt()
        val ib = t + ((b - t) * 0.8f).toInt()

        val votes = mutableMapOf<String, Int>()
        val hsv = FloatArray(3)
        var total = 0
        for (gy in 0 until GRID) {
            for (gx in 0 until GRID) {
                val x = il + (gx * (ir - il) / (GRID - 1)).coerceIn(0, bitmap.width - 1)
                val y = it + (gy * (ib - it) / (GRID - 1)).coerceIn(0, bitmap.height - 1)
                Color.colorToHSV(bitmap.getPixel(x, y), hsv)
                classifyPixel(hsv)?.let { votes[it] = (votes[it] ?: 0) + 1 }
                total++
            }
        }
        if (total == 0) return null
        val (winner, count) = votes.maxByOrNull { it.value } ?: return null
        // Needs a real majority, otherwise admit we don't know.
        return if (count * 2 >= total) winner else null
    }

    private fun classifyPixel(hsv: FloatArray): String? {
        val (h, s, v) = hsv
        if (v < 0.22f) return "black"
        if (s < 0.25f) return if (v > 0.72f) "white" else null
        return when {
            h < 12f || h >= 348f -> "red"
            h < 38f -> null // orange/brown — don't guess
            h < 78f -> "yellow"
            h < 165f -> "green"
            h < 265f -> "blue"
            h < 300f -> null // purple — don't guess
            else -> "red" // magenta-red
        }
    }
}
