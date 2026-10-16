package com.zenithai.countai.detection

import android.graphics.Bitmap

/**
 * Abstraction over the object-detection model.
 *
 * The whole UI talks to this interface only, so a custom-trained model
 * (products, fruits, inventory…) can be swapped in later by writing a new
 * [Detector] implementation — no UI code changes required.
 */
interface Detector {

    /** Width/height of the square bitmap the model expects. */
    val inputSize: Int

    /** False when the model failed to load (UI shows an error instead of crashing). */
    val isReady: Boolean

    /**
     * Runs detection on [bitmap] (any size; will be scaled to [inputSize]).
     * Returns detections with normalized boxes, sorted by confidence.
     */
    fun detect(bitmap: Bitmap, threshold: Float): List<Detection>

    fun close()
}
