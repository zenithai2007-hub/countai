package com.zenithai.countai.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.zenithai.countai.util.Prefs
import java.util.concurrent.TimeUnit

/**
 * Owns all background sync scheduling.
 *
 * Design (why 50k users don't break anything):
 * - Room is the source of truth; sync only ever PUSHES local rows up.
 *   There is no "download everything" path, so one user's data can never
 *   flood another user's phone or our bandwidth.
 * - Metadata sync runs every 6h and needs only "battery not low" + any
 *   network. Thumbnails additionally need UNMETERED (WiFi) + the user's
 *   explicit opt-in (default OFF).
 * - Retries use WorkManager's exponential backoff; after 5 failed attempts
 *   the worker parks itself and waits for the next 6h cycle instead of
 *   hammering the server.
 * - Manual "Sync now" is debounced to at most one run per 60s.
 */
object SyncManager {

    const val TAG_PERIODIC = "countai-sync-periodic"
    const val TAG_THUMBS = "countai-thumbs-periodic"
    const val TAG_NOW = "countai-sync-now"
    const val TAG_THUMBS_NOW = "countai-thumbs-now"

    const val BATCH_SIZE = 100
    const val MAX_ATTEMPTS = 5
    const val MANUAL_DEBOUNCE_MS = 60_000L

    /** Start (or keep) the periodic workers. Safe to call on every app start. */
    fun schedulePeriodic(context: Context) {
        if (!SupabaseConfig.isConfigured) return
        val wm = WorkManager.getInstance(context)

        val metaConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val metaWork = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(metaConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG_PERIODIC)
            .build()
        wm.enqueueUniquePeriodicWork(TAG_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, metaWork)

        val thumbConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()
        val thumbWork = PeriodicWorkRequestBuilder<ThumbnailWorker>(6, TimeUnit.HOURS)
            .setConstraints(thumbConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG_THUMBS)
            .build()
        wm.enqueueUniquePeriodicWork(TAG_THUMBS, ExistingPeriodicWorkPolicy.KEEP, thumbWork)
    }

    /**
     * User-tapped "Sync now". Returns false when debounced (tapped too soon
     * after the previous manual sync) so the UI can say "just synced".
     */
    fun syncNow(context: Context): Boolean {
        if (!SupabaseConfig.isConfigured) return false
        val now = System.currentTimeMillis()
        val last = Prefs.lastManualSync(context)
        if (now - last < MANUAL_DEBOUNCE_MS) return false
        Prefs.setLastManualSync(context, now)

        val wm = WorkManager.getInstance(context)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val work = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(TAG_NOW)
            .build()
        wm.enqueueUniqueWork(TAG_NOW, ExistingWorkPolicy.KEEP, work)

        // Thumbnails only on WiFi + opt-in; the worker itself re-checks both.
        val thumbWork = OneTimeWorkRequestBuilder<ThumbnailWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()
            )
            .addTag(TAG_THUMBS_NOW)
            .build()
        wm.enqueueUniqueWork(TAG_THUMBS_NOW, ExistingWorkPolicy.KEEP, thumbWork)
        return true
    }

    /** Stop everything (called on sign-out). Local data is kept. */
    fun cancelAll(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(TAG_PERIODIC)
        wm.cancelUniqueWork(TAG_THUMBS)
        wm.cancelUniqueWork(TAG_NOW)
        wm.cancelUniqueWork(TAG_THUMBS_NOW)
    }
}
