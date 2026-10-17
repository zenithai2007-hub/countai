package com.zenithai.countai.util

import android.app.ActivityManager
import android.content.Context

/**
 * Low-end phone support.
 *
 * Three performance presets adjust detection FPS (the model input size is
 * fixed by the .tflite file, so FPS is the main lever). On devices with
 * little RAM the app suggests Battery Saver once.
 */
object PerformanceManager {

    const val MODE_BATTERY_SAVER = "battery"
    const val MODE_BALANCED = "balanced"
    const val MODE_HIGH_ACCURACY = "high"

    /** FPS each preset analyzes. */
    fun fpsFor(mode: String): Int = when (mode) {
        MODE_BATTERY_SAVER -> 5
        MODE_HIGH_ACCURACY -> 15
        else -> 10
    }

    fun displayName(mode: String): String = when (mode) {
        MODE_BATTERY_SAVER -> "Battery Saver"
        MODE_HIGH_ACCURACY -> "High Accuracy"
        else -> "Balanced"
    }

    /** True when the device looks too weak for Balanced mode. */
    fun shouldSuggestBatterySaver(context: Context): Boolean = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        mem.totalMem < 3L * 1024 * 1024 * 1024 // < 3 GB RAM
    } catch (e: Exception) {
        false
    }
}
