package com.zenithai.countai.util

import android.content.Context

/** SharedPreferences keys + typed accessors used by Camera and Settings. */
object Prefs {
    const val FILE = "countai_prefs"
    const val CONFIDENCE = "confidence_threshold"
    const val FPS = "fps_limit"
    const val GPU = "use_gpu"
    const val NNAPI = "use_nnapi"

    const val DEFAULT_CONFIDENCE = 0.5f
    const val DEFAULT_FPS = 10

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun confidence(context: Context): Float =
        prefs(context).getFloat(CONFIDENCE, DEFAULT_CONFIDENCE)

    fun fps(context: Context): Int =
        prefs(context).getInt(FPS, DEFAULT_FPS).coerceIn(5, 15)

    fun useGpu(context: Context): Boolean =
        prefs(context).getBoolean(GPU, true)

    fun useNnapi(context: Context): Boolean =
        prefs(context).getBoolean(NNAPI, false)
}
