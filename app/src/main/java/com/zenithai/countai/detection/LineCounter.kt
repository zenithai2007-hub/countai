package com.zenithai.countai.detection

/**
 * Virtual IN/OUT counting line.
 *
 * Each tracked object keeps the last known side of the line (above/below).
 * When its centroid crosses [lineY] (normalized 0..1 in the model's square
 * input), it counts once:
 *   - moving top → bottom = IN
 *   - moving bottom → top = OUT
 *
 * An object only ever counts one crossing per pass: after crossing it must
 * leave and re-enter the other side before it can count again. IDs come from
 * [ObjectTracker], so the same physical object is never double-counted.
 */
class LineCounter(var lineY: Float = 0.5f) {

    var inCount = 0
        private set
    var outCount = 0
        private set
    val netCount: Int get() = inCount - outCount

    /** Tracked ID -> side it was last seen on (true = above the line). */
    private val lastSide = mutableMapOf<Long, Boolean>()
    /** Tracked ID -> side it crossed to (blocks immediate re-count). */
    private val crossedTo = mutableMapOf<Long, Boolean>()

    fun update(tracked: List<TrackedObject>) {
        val seen = HashSet<Long>()
        for (t in tracked) {
            seen.add(t.id)
            val cy = t.box.centerY()
            val above = cy < lineY
            val prev = lastSide[t.id]
            if (prev != null && prev != above) {
                // A real crossing — but only count if it didn't just cross.
                if (crossedTo[t.id] != above) {
                    if (above) outCount++ else inCount++
                    crossedTo[t.id] = above
                }
            } else if (prev == null) {
                crossedTo.remove(t.id)
            }
            lastSide[t.id] = above
        }
        // Forget objects that disappeared so re-entries count fresh.
        val dead = lastSide.keys - seen
        for (id in dead) {
            lastSide.remove(id)
            crossedTo.remove(id)
        }
    }

    fun reset() {
        inCount = 0
        outCount = 0
        lastSide.clear()
        crossedTo.clear()
    }
}
