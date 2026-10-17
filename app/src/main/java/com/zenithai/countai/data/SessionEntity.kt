package com.zenithai.countai.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One saved counting session.
 * [countsJson] is a simple {"bottle":12,"chair":4} object — easy to read,
 * easy to export, no extra tables needed for v1.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val countsJson: String = "{}",
    /** Absolute path of the saved photo in app-internal storage (null = no photo). */
    val imagePath: String? = null,
    /** "live" for camera sessions, "photo" for still-image counting. */
    val mode: String = "live",
    /**
     * Coarse "lat,lng" label — only set when the user enabled location in
     * Settings AND granted the permission. Null = not recorded (default).
     */
    val location: String? = null,
    /**
     * Cloud sync (v3): the row's UUID in Supabase once uploaded, null until then.
     * [pendingSync] = true means "needs upload" — set on every local insert.
     * Room stays the source of truth; Supabase is a sync target only.
     */
    val remoteId: String? = null,
    val pendingSync: Boolean = true,
) {
    fun counts(): Map<String, Int> = try {
        val obj = org.json.JSONObject(countsJson)
        val keys = obj.keys()
        val map = LinkedHashMap<String, Int>()
        while (keys.hasNext()) {
            val k = keys.next()
            map[k] = obj.optInt(k, 0)
        }
        map
    } catch (e: Exception) {
        emptyMap()
    }

    fun totalCount(): Int = counts().values.sum()
}
