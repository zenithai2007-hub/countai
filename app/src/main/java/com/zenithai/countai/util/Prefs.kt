package com.zenithai.countai.util

import android.content.Context

/** SharedPreferences keys + typed accessors used by Camera and Settings. */
object Prefs {
    const val FILE = "countai_prefs"
    const val CONFIDENCE = "confidence_threshold"
    const val FPS = "fps_limit"
    const val GPU = "use_gpu"
    const val NNAPI = "use_nnapi"
    const val LANG = "app_language"
    const val PERF_MODE = "perf_mode"
    const val LOCATION_ENABLED = "location_enabled"
    const val COACH_DONE = "coach_done"
    const val PERF_SUGGESTED = "perf_suggested"
    const val CUSTOM_MODEL_PATH = "custom_model_path"
    const val CUSTOM_LABELS_PATH = "custom_labels_path"

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

    fun language(context: Context): String =
        prefs(context).getString(LANG, LanguageManager.ENGLISH) ?: LanguageManager.ENGLISH

    fun setLanguage(context: Context, code: String) {
        prefs(context).edit().putString(LANG, code).apply()
    }

    fun perfMode(context: Context): String =
        prefs(context).getString(PERF_MODE, "balanced") ?: "balanced"

    /** FPS comes from the performance preset; the old FPS slider is retired. */
    fun effectiveFps(context: Context): Int =
        PerformanceManager.fpsFor(perfMode(context))

    fun setPerfMode(context: Context, mode: String) {
        prefs(context).edit().putString(PERF_MODE, mode).apply()
    }

    fun locationEnabled(context: Context): Boolean =
        prefs(context).getBoolean(LOCATION_ENABLED, false)

    fun setLocationEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(LOCATION_ENABLED, enabled).apply()
    }

    fun coachDone(context: Context): Boolean =
        prefs(context).getBoolean(COACH_DONE, false)

    fun setCoachDone(context: Context) {
        prefs(context).edit().putBoolean(COACH_DONE, true).apply()
    }

    fun perfSuggested(context: Context): Boolean =
        prefs(context).getBoolean(PERF_SUGGESTED, false)

    fun setPerfSuggested(context: Context) {
        prefs(context).edit().putBoolean(PERF_SUGGESTED, true).apply()
    }

    fun customModelPath(context: Context): String? =
        prefs(context).getString(CUSTOM_MODEL_PATH, null)

    fun customLabelsPath(context: Context): String? =
        prefs(context).getString(CUSTOM_LABELS_PATH, null)

    fun setCustomModel(context: Context, modelPath: String?, labelsPath: String?) {
        prefs(context).edit()
            .putString(CUSTOM_MODEL_PATH, modelPath)
            .putString(CUSTOM_LABELS_PATH, labelsPath)
            .apply()
    }
}
