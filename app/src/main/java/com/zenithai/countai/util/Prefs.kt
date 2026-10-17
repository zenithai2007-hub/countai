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
    // Cloud sync (v3)
    const val CLOUD_BACKUP = "cloud_backup"
    const val LAST_SYNC = "last_sync_at"
    const val LAST_MANUAL_SYNC = "last_manual_sync_at"
    const val THUMBS_UPLOADED = "thumbs_uploaded"

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

    // ---- Cloud sync (v3) ----

    /** "Cloud backup" master toggle. Default OFF — nothing leaves the phone. */
    fun cloudBackup(context: Context): Boolean =
        prefs(context).getBoolean(CLOUD_BACKUP, false)

    fun setCloudBackup(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(CLOUD_BACKUP, enabled).apply()
    }

    /** Last successful metadata sync, 0 = never. */
    fun lastSync(context: Context): Long =
        prefs(context).getLong(LAST_SYNC, 0L)

    fun setLastSync(context: Context, at: Long) {
        prefs(context).edit().putLong(LAST_SYNC, at).apply()
    }

    fun lastManualSync(context: Context): Long =
        prefs(context).getLong(LAST_MANUAL_SYNC, 0L)

    fun setLastManualSync(context: Context, at: Long) {
        prefs(context).edit().putLong(LAST_MANUAL_SYNC, at).apply()
    }

    /** Remote IDs whose thumbnails are already in cloud storage. */
    fun thumbsUploaded(context: Context): Set<String> =
        prefs(context).getStringSet(THUMBS_UPLOADED, emptySet()) ?: emptySet()

    fun setThumbsUploaded(context: Context, ids: Set<String>) {
        prefs(context).edit().putStringSet(THUMBS_UPLOADED, ids.toSet()).apply()
    }
}
