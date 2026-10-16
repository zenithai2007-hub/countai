package com.zenithai.countai.util

import android.content.Context
import com.zenithai.countai.data.SessionEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exports history to CSV. One row per (session, label) — opens cleanly in
 * Excel / Google Sheets. File goes to the cache dir and is shared through
 * the FileProvider (see res/xml/filepaths.xml).
 */
object CsvExporter {

    fun export(context: Context, sessions: List<SessionEntity>): File {
        val file = File(context.cacheDir, "countai_history.csv")
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        file.bufferedWriter().use { w ->
            w.write("id,date,mode,label,count\n")
            for (s in sessions) {
                val date = dateFmt.format(Date(s.createdAt))
                val counts = s.counts()
                if (counts.isEmpty()) {
                    w.write("${s.id},$date,${s.mode},(none),0\n")
                } else {
                    for ((label, count) in counts.toList().sortedByDescending { it.second }) {
                        w.write("${s.id},$date,${s.mode},\"$label\",$count\n")
                    }
                }
            }
        }
        return file
    }
}
