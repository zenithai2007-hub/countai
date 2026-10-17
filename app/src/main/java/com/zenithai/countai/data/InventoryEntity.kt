package com.zenithai.countai.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One named product in Smart Inventory Mode.
 * [label] is the canonical detection label it maps to ("bottle", "box"…);
 * [lastCount] is the most recent counted quantity.
 */
@Entity(tableName = "inventory")
data class InventoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productName: String,
    val label: String,
    val lastCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)
