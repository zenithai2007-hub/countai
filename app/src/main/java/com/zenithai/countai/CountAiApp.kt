package com.zenithai.countai

import android.app.Application
import com.zenithai.countai.sync.SyncManager
import com.zenithai.countai.util.CrashHandler
import com.zenithai.countai.util.LanguageManager

class CountAiApp : Application() {
    override fun onCreate() {
        // FIRST: catch any startup crash and show what broke.
        CrashHandler.install(this)
        super.onCreate()
        // Restore the user's language choice before any UI is created.
        LanguageManager.applyFromPrefs(this)
        // (Re)register the 6-hourly sync workers. KEEP policy = no duplicates.
        // No-op when Supabase isn't configured or the user is signed out.
        SyncManager.schedulePeriodic(this)
    }
}
