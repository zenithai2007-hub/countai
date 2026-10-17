package com.zenithai.countai

import android.app.Application
import com.zenithai.countai.util.LanguageManager

class CountAiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Restore the user's language choice before any UI is created.
        LanguageManager.applyFromPrefs(this)
    }
}
