package com.zenithai.countai.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * Camera frame conversion helpers, shared by every camera-based screen.
 * Extracted so CountCam / IN-OUT / Video modes don't duplicate the
 * stride-aware YUV_420_888 → Bitmap logic.
 */
object FrameConverter {

    fun yuvToBitmap(image: Image): Bitmap? = try {
        val nv21 = yuv420ToNv21(image)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 80, out)
        val bytes = out.toByteArray()
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        Log.w("FrameConverter", "YUV conversion failed", e)
        null
    }

    /**
     * Correct YUV_420_888 → NV21 handling arbitrary row/pixel strides
     * (the naive plane-concat breaks on many devices).
     */
    fun yuv420ToNv21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        val nv21 = ByteArray(width * height * 3 / 2)
        var pos = 0
        if (yRowStride == width) {
            yBuf.get(nv21, 0, width * height)
            pos = width * height
        } else {
            val row = ByteArray(yRowStride)
            for (r in 0 until height) {
                yBuf.get(row)
                System.arraycopy(row, 0, nv21, pos, width)
                pos += width
            }
        }
        for (r in 0 until height / 2) {
            for (c in 0 until width / 2) {
                val dst = pos + r * width + c * 2
                nv21[dst] = vBuf.get(r * uvRowStride + c * uvPixelStride)
                nv21[dst + 1] = uBuf.get(r * uvRowStride + c * uvPixelStride)
            }
        }
        return nv21
    }

    /** Rotates [bitmap] by [degrees] (recycles the original when changed). */
    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        bitmap.recycle()
        return rotated
    }

    /** Center-crops [bitmap] to a square (recycles the original). */
    fun centerSquare(bitmap: Bitmap): Bitmap {
        val side = minOf(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(
            bitmap,
            (bitmap.width - side) / 2,
            (bitmap.height - side) / 2,
            side, side,
        )
        bitmap.recycle()
        return square
    }
}
