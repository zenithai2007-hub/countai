package com.zenithai.countai.util

/**
 * Compares two counting sessions (e.g. a photo taken today vs last week).
 * One row per label seen in either session; rows with a change are flagged
 * so the UI can highlight them.
 */
data class DiffRow(
    val label: String,
    val before: Int,
    val after: Int,
) {
    val diff: Int get() = after - before
    val changed: Boolean get() = diff != 0
}

object DifferenceUtil {

    fun compare(
        before: Map<String, Int>,
        after: Map<String, Int>,
    ): List<DiffRow> {
        val labels = (before.keys + after.keys).toSortedSet()
        return labels.map { label ->
            DiffRow(label, before[label] ?: 0, after[label] ?: 0)
        }.sortedWith(
            compareByDescending<DiffRow> { it.changed }
                .thenByDescending { kotlin.math.abs(it.diff) }
        )
    }
}
