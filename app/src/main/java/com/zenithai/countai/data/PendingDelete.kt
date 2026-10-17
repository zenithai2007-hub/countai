package com.zenithai.countai.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A tombstone for a row the user deleted locally that ALSO exists in Supabase.
 *
 * The sync worker uploads the delete to the cloud so a signed-in user's
 * other view of the data stays consistent. Processed tombstones are removed
 * after the remote delete succeeds (or after 5 failed attempts — the next
 * full sync cycle will reconcile anyway).
 */
@Entity(tableName = "pending_deletes")
data class PendingDelete(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "sessions" or "inventory_items" — the Supabase table name. */
    val tableName: String,
    /** The Supabase row UUID to delete. */
    val remoteId: String,
    /** Owner, so we never delete another user's rows by mistake. */
    val userId: String,
    val createdAt: Long = System.currentTimeMillis(),
)
