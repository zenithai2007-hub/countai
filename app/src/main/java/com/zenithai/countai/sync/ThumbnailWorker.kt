package com.zenithai.countai.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zenithai.countai.data.CountAiDatabase
import com.zenithai.countai.util.Prefs
import io.github.jan_tennert.supabase.postgrest.from
import io.github.jan_tennert.supabase.postgrest.query.filter.eq
import io.github.jan_tennert.supabase.storage.storage
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Uploads SMALL thumbnails (~100 KB) for already-synced sessions — and ONLY
 * when ALL of these are true:
 *  1. Supabase is configured,
 *  2. the user is signed in,
 *  3. the user enabled "Cloud backup" in Settings (default OFF),
 *  4. the device is on UNMETERED network (WiFi) — enforced by WorkManager.
 *
 * Full-resolution photos NEVER auto-upload. If every one of 50k users
 * uploaded full photos daily, the free tier would die in days; ~100 KB
 * thumbnails keep the math safe (see README → "Scaling to 50k users").
 *
 * Thumbnails go to the private `thumbnails` bucket (see supabase/schema.sql);
 * RLS policies there restrict access to the owning user.
 */
class ThumbnailWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!SupabaseConfig.isConfigured) return Result.success()
        if (!Prefs.cloudBackup(applicationContext)) return Result.success()
        val client = SupabaseClientProvider.get(applicationContext)
            ?: return Result.success()
        val userId = try {
            client.auth.currentUserOrNull()?.id
        } catch (e: Exception) {
            null
        } ?: return Result.success()

        return try {
            val db = CountAiDatabase.getInstance(applicationContext)
            val uploaded = Prefs.thumbsUploaded(applicationContext).toMutableSet()
            // Only sessions already synced (have a remoteId) and not yet uploaded.
            val candidates = db.historyDao().getAllNow()
                .filter { it.remoteId != null && it.imagePath != null && it.remoteId !in uploaded }
                .take(20) // bound per run: 20 × ~100 KB ≈ 2 MB max per cycle
            for (s in candidates) {
                val bytes = makeThumbnail(s.imagePath!!) ?: continue
                val path = "$userId/${s.remoteId}.jpg"
                try {
                    client.storage.from("thumbnails").upload(path, bytes, upsert = true)
                    client.from("sessions").update(
                        mapOf("thumbnail_url" to path)
                    ) {
                        filter { eq("id", s.remoteId!!) }
                    }
                    uploaded.add(s.remoteId!!)
                } catch (e: Exception) {
                    // Skip this one; next cycle retries.
                }
            }
            Prefs.setThumbsUploaded(applicationContext, uploaded)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount >= SyncManager.MAX_ATTEMPTS) Result.success()
            else Result.retry()
        }
    }

    /**
     * Downscales to max 512px and JPEG-compresses to ~100 KB.
     * Returns null when the source can't be read (file deleted etc.).
     */
    private fun makeThumbnail(imagePath: String): ByteArray? {
        return try {
            val file = File(imagePath)
            if (!file.exists()) return null
            // 1. Bounds only, then pick a sample size so the longest side ≤ 1024.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(imagePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (longest / sample > 1024) sample *= 2
            // 2. Decode sampled, then scale precisely to ≤ 512 on the long side.
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = BitmapFactory.decodeFile(imagePath, opts) ?: return null
            val scale = 512f / maxOf(bmp.width, bmp.height).coerceAtLeast(1)
            if (scale < 1f) {
                val w = (bmp.width * scale).toInt().coerceAtLeast(1)
                val h = (bmp.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
                if (scaled != bmp) bmp.recycle()
                bmp = scaled
            }
            // 3. Compress; drop quality until ≤ ~110 KB or quality hits 40.
            var quality = 70
            var bytes: ByteArray
            do {
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
                bytes = out.toByteArray()
                quality -= 10
            } while (bytes.size > 110 * 1024 && quality >= 40)
            bmp.recycle()
            bytes
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }
}
