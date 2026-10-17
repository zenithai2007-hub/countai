package com.zenithai.countai.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format for the Supabase tables (see supabase/schema.sql).
 *
 * Kept deliberately SMALL: id, owner, timestamps, counts. No photos, no
 * blobs — metadata-only sync is what keeps 50k users inside Supabase's free
 * tier (see README → "Scaling to 50k users" for the math). A session row is
 * roughly 200 bytes.
 */
@Serializable
data class RemoteSession(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("mode") val mode: String,
    @SerialName("counts_json") val countsJson: String,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
)

@Serializable
data class RemoteInventoryItem(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("name") val name: String,
    @SerialName("label") val label: String,
    @SerialName("last_count") val lastCount: Int,
    @SerialName("updated_at") val updatedAt: String,
)
