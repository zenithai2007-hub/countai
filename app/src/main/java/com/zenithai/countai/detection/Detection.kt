package com.zenithai.countai.detection

import android.graphics.RectF

/**
 * One detected object. [box] uses NORMALIZED coordinates (0..1)
 * relative to the square image that was fed to the model.
 */
data class Detection(
    val label: String,
    val box: RectF,
    val confidence: Float,
)
