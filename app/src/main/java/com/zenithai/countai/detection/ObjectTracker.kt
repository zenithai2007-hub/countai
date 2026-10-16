package com.zenithai.countai.detection

import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/** Intersection-over-Union of two normalized boxes, in [0, 1]. */
internal fun iou(a: RectF, b: RectF): Float {
    val interLeft = max(a.left, b.left)
    val interTop = max(a.top, b.top)
    val interRight = min(a.right, b.right)
    val interBottom = min(a.bottom, b.bottom)
    val inter = max(0f, interRight - interLeft) * max(0f, interBottom - interTop)
    if (inter <= 0f) return 0f
    val union = a.width() * a.height() + b.width() * b.height() - inter
    return if (union <= 0f) 0f else inter / union
}

/** One physical object being followed across frames. */
data class TrackedObject(
    val id: Long,
    val label: String,
    /** Normalized box (0..1) in the model's square input. */
    var box: RectF,
    var confidence: Float,
    var missedFrames: Int = 0,
)

/**
 * IOU-based multi-object tracker.
 *
 * Each frame's detections are matched to already-tracked objects of the same
 * label when their boxes overlap enough. Matched objects keep their ID, so a
 * physical object is counted exactly once no matter how long the camera looks
 * at it. A detection that matches nothing becomes a NEW object (new ID) — this
 * is how "an old object left, a new one entered" is handled.
 *
 * Honest limitation: if the camera moves fast, boxes stop overlapping and the
 * same physical object may get a fresh ID (session total over-counts). Slow,
 * steady movement gives the best totals.
 */
class ObjectTracker(
    private val iouThreshold: Float = 0.45f,
    /** Frames an object may disappear (occlusion) before it is forgotten. */
    private val maxMissedFrames: Int = 10,
) {
    private val tracked = mutableListOf<TrackedObject>()
    private var nextId = 1L

    /** label -> IDs of every unique object seen this session (never shrinks). */
    private val sessionIds = mutableMapOf<String, MutableSet<Long>>()

    /**
     * Matches [detections] against tracked objects.
     * @return all currently-tracked objects (including briefly occluded ones,
     * so boxes don't flicker).
     */
    fun update(detections: List<Detection>): List<TrackedObject> {
        val matched = HashSet<TrackedObject>()
        for (d in detections.sortedByDescending { it.confidence }) {
            var best: TrackedObject? = null
            var bestIou = iouThreshold
            for (t in tracked) {
                if (t in matched || t.label != d.label) continue
                val v = iou(t.box, d.box)
                if (v > bestIou) {
                    bestIou = v
                    best = t
                }
            }
            if (best != null) {
                best.box = d.box
                best.confidence = d.confidence
                best.missedFrames = 0
                matched.add(best)
            } else {
                val fresh = TrackedObject(nextId++, d.label, d.box, d.confidence)
                tracked.add(fresh)
                matched.add(fresh)
                sessionIds.getOrPut(d.label) { mutableSetOf() }.add(fresh.id)
            }
        }
        val it = tracked.iterator()
        while (it.hasNext()) {
            val t = it.next()
            if (t !in matched) {
                t.missedFrames++
                if (t.missedFrames > maxMissedFrames) it.remove()
            }
        }
        return tracked.toList()
    }

    /** Objects currently in view, per label. */
    fun liveCounts(): Map<String, Int> = tracked.groupingBy { it.label }.eachCount()

    /** Unique objects seen this whole session, per label. Never decreases. */
    fun sessionCounts(): Map<String, Int> = sessionIds.mapValues { it.value.size }

    fun reset() {
        tracked.clear()
        sessionIds.clear()
        nextId = 1L
    }
}
