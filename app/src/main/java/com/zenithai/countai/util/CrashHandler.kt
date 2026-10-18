package com.zenithai.countai.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.zenithai.countai.ui.CrashReportActivity
import java.io.File

/**
 * Catches any uncaught crash, saves the stack trace to internal storage,
 * and opens [CrashReportActivity] so the user can see / share what broke.
 *
 * Installed as the VERY FIRST line of CountAiApp.onCreate(), so even an
 * early startup crash is captured instead of silently killing the app.
 */
object CrashHandler {

    private const val TAG = "CrashHandler"
    const val CRASH_FILE = "crash-latest.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val dir = File(appContext.filesDir, "crashes").apply { mkdirs() }
                val trace = Log.getStackTraceString(throwable)
                    .ifEmpty { throwable.toString() }
                File(dir, CRASH_FILE).writeText(trace)
            } catch (e: Exception) {
                Log.e(TAG, "failed to save crash log", e)
            }
            // Show the report screen in a fresh task, then let the old
            // handler finish killing this process.
            try {
                val intent = Intent(appContext, CrashReportActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                appContext.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "failed to open crash report", e)
            }
            previous?.uncaughtException(thread, throwable)
            // If there was no previous handler, make sure the process dies
            // instead of hanging in a broken state.
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    /** The saved crash trace, or null when the app never crashed. */
    fun readLatest(context: Context): String? {
        return try {
            val f = File(File(context.filesDir, "crashes"), CRASH_FILE)
            if (f.exists()) f.readText().ifBlank { null } else null
        } catch (_: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        try {
            File(File(context.filesDir, "crashes"), CRASH_FILE).delete()
        } catch (_: Exception) {
        }
    }
}
