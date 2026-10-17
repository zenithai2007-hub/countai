package com.zenithai.countai.data

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import com.zenithai.countai.R
import com.zenithai.countai.util.CsvExporter
import com.zenithai.countai.util.ImageSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Single place the UI talks to for history: save / list / delete / export / share.
 * Cloud sync intentionally lives behind [com.zenithai.countai.repository.CloudSyncRepository]
 * (a stub for now) — this class only owns the LOCAL source of truth.
 */
class SessionRepository(private val context: Context) {

    private val dao = CountAiDatabase.getInstance(context).historyDao()

    fun sessions(): LiveData<List<SessionEntity>> = dao.getAll()

    /** Saves a session; stores the photo in app-internal storage. Returns the new row id. */
    suspend fun saveSession(
        counts: Map<String, Int>,
        photo: Bitmap?,
        mode: String,
        location: String? = null,
    ): Long =
        withContext(Dispatchers.IO) {
            val imagePath = photo?.let {
                ImageSaver.saveBitmap(context, it, "session_${System.currentTimeMillis()}.jpg")
            }
            dao.insert(
                SessionEntity(
                    countsJson = JSONObject(counts as Map<*, *>).toString(),
                    imagePath = imagePath,
                    mode = mode,
                    location = location,
                )
            )
        }

    suspend fun deleteSession(session: SessionEntity) = withContext(Dispatchers.IO) {
        // If this row also lives in the cloud, leave a tombstone so the sync
        // worker deletes it there too (keeps signed-in data consistent).
        val userId = com.zenithai.countai.di.ServiceLocator
            .authRepository(context).currentUserId
        if (session.remoteId != null && userId != null) {
            CountAiDatabase.getInstance(context).pendingDeleteDao().insert(
                com.zenithai.countai.data.PendingDelete(
                    tableName = "sessions",
                    remoteId = session.remoteId,
                    userId = userId,
                )
            )
        }
        session.imagePath?.let { File(it).delete() }
        dao.deleteById(session.id)
    }

    /** Exports every session to a CSV file in the cache dir. */
    suspend fun exportAllCsv(): File = withContext(Dispatchers.IO) {
        CsvExporter.export(context, dao.getAllNow())
    }

    /** Human-readable summary used by the Share button. */
    fun shareText(session: SessionEntity): String {
        val date = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
            .format(Date(session.createdAt))
        val sb = StringBuilder(context.getString(R.string.share_header, date)).append("\n")
        val counts = session.counts().toList().sortedByDescending { it.second }
        for ((label, count) in counts) sb.append("\n$label: $count")
        sb.append("\n").append(context.getString(R.string.share_total_line, session.totalCount()))
        return sb.toString()
    }

    /** Same as [shareText] but for the live (not yet saved) counts. */
    fun shareText(counts: Map<String, Int>): String {
        val date = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date())
        val sb = StringBuilder(context.getString(R.string.share_header, date)).append("\n")
        for ((label, count) in counts.toList().sortedByDescending { it.second }) {
            sb.append("\n$label: $count")
        }
        sb.append("\n").append(context.getString(R.string.share_total_line, counts.values.sum()))
        return sb.toString()
    }
}
