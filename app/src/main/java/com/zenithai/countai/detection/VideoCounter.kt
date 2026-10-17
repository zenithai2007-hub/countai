package com.zenithai.countai.detection

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.zenithai.countai.util.FrameConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Counts unique objects across a whole video.
 *
 * Samples one frame every [frameIntervalMs], runs the detector on each, and
 * feeds everything through a single [ObjectTracker] — so a box that appears
 * in 40 frames is still counted exactly once. Returns per-label unique
 * totals. Progress (0..100) is reported on the calling coroutine.
 *
 * Honest limits: frame sampling (not every frame) + IOU tracking means very
 * fast-moving objects can be missed or double-counted; results are estimates.
 */
object VideoCounter {

    suspend fun count(
        context: Context,
        videoUri: Uri,
        detector: Detector,
        threshold: Float,
        frameIntervalMs: Long = 500,
        onProgress: (Int) -> Unit,
    ): Map<String, Int> = withContext(Dispatchers.Default) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val durationUs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull()?.times(1000) ?: 0L
            if (durationUs <= 0L) return@withContext emptyMap()

            val tracker = ObjectTracker()
            var t = 0L
            while (t < durationUs && isActive) {
                val frame: Bitmap? = try {
                    retriever.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } catch (e: Exception) {
                    Log.w(TAG, "frame grab failed at $t", e)
                    null
                }
                if (frame != null) {
                    val square = FrameConverter.centerSquare(frame)
                    val detections = try {
                        detector.detect(square, threshold)
                    } catch (e: Exception) {
                        Log.w(TAG, "detect failed", e)
                        emptyList()
                    }
                    square.recycle()
                    tracker.update(detections)
                }
                t += frameIntervalMs * 1000
                onProgress(((t * 100) / durationUs).toInt().coerceIn(0, 100))
            }
            tracker.sessionCounts()
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.w(TAG, "retriever.release failed", e)
            }
        }
    }

    private const val TAG = "VideoCounter"
}
