package com.zenithai.countai.util

import com.zenithai.countai.data.SessionEntity
import java.util.Calendar

/**
 * Natural-language search over counting history.
 *
 * Understands queries like (English / Urdu / Roman Urdu):
 *   "show my box counts from last week"
 *   "pichle hafte ke dabba count dikhao" / "پچھلے ہفتے کے ڈبے"
 *   "what was my highest bottle count?" / "sab se zyada botalen"
 *   "show today's inventory" / "aaj ki ginti"
 */
data class SearchRequest(
    val label: String?, // canonical label, or null = any
    val fromMs: Long,
    val toMs: Long,
    val wantMax: Boolean,
)

object SmartSearch {

    fun parse(rawQuery: String): SearchRequest {
        val q = " $rawQuery ".lowercase()
        val cmd = CommandParser.parse(rawQuery)
        val label = cmd.labels.firstOrNull()

        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        var from = 0L
        var to = now

        fun startOfDay(c: Calendar): Long {
            val d = c.clone() as Calendar
            d.set(Calendar.HOUR_OF_DAY, 0); d.set(Calendar.MINUTE, 0)
            d.set(Calendar.SECOND, 0); d.set(Calendar.MILLISECOND, 0)
            return d.timeInMillis
        }

        when {
            "today" in q || " aaj " in q || " آج " in q -> {
                from = startOfDay(cal)
            }
            "yesterday" in q || " kal " in q || " کل " in q -> {
                cal.add(Calendar.DAY_OF_YEAR, -1)
                from = startOfDay(cal)
                cal.add(Calendar.DAY_OF_YEAR, 1)
                to = startOfDay(cal) - 1
            }
            "last week" in q || "pichle hafte" in q || "pichla hafta" in q ||
                "پچھلے ہفتے" in q || "پچھلا ہفتہ" in q -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                from = startOfDay(cal)
            }
            "this month" in q || "is mahine" in q || "اس مہینے" in q -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                from = startOfDay(cal)
            }
            "last month" in q || "pichle mahine" in q || "پچھلے مہینے" in q -> {
                cal.add(Calendar.MONTH, -1)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                from = startOfDay(cal)
                cal.add(Calendar.MONTH, 1)
                to = startOfDay(cal) - 1
            }
        }

        val wantMax = listOf(
            "highest", "maximum", "max", "sab se zyada", "سب سے زیادہ", "most",
        ).any { it in q }

        return SearchRequest(label, from, to, wantMax)
    }

    fun filter(sessions: List<SessionEntity>, req: SearchRequest): List<SessionEntity> =
        sessions.filter { s ->
            s.createdAt in req.fromMs..req.toMs &&
                (req.label == null || labelMatches(s, req.label))
        }

    private fun labelMatches(s: SessionEntity, label: String): Boolean {
        val keys = s.counts().keys
        if (label == "box") return keys.any { it in CommandParser.expandBoxLabel() }
        return label in keys
    }

    /** Highest single-session count of [label] (or of any object when null). */
    fun highest(sessions: List<SessionEntity>, label: String?): Pair<SessionEntity?, Int> {
        var best: SessionEntity? = null
        var bestCount = 0
        for (s in sessions) {
            val c = if (label == null) s.totalCount()
            else if (label == "box") s.counts().filterKeys { it in CommandParser.expandBoxLabel() }.values.sum()
            else s.counts()[label] ?: 0
            if (c > bestCount) { bestCount = c; best = s }
        }
        return best to bestCount
    }
}
