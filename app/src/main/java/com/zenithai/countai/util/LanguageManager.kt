package com.zenithai.countai.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * In-app language switcher: English / اردو / Roman Urdu.
 *
 * Roman Urdu uses the BCP-47 tag "ur-Latn" (Urdu in Latin script) with
 * resources in `res/values-b+ur+Latn/`. Android matches that qualifier
 * automatically once the locale is applied here.
 */
object LanguageManager {
    const val ENGLISH = "en"
    const val URDU = "ur"
    const val ROMAN_URDU = "ur-Latn"

    fun current(context: Context): String = Prefs.language(context)

    fun setLanguage(context: Context, code: String) {
        Prefs.setLanguage(context, code)
        apply(code)
    }

    /** Call from Application.onCreate so the choice survives restarts. */
    fun applyFromPrefs(context: Context) = apply(Prefs.language(context))

    private fun apply(code: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
    }

    fun displayName(code: String): String = when (code) {
        URDU -> "اردو"
        ROMAN_URDU -> "Roman Urdu"
        else -> "English"
    }
}
