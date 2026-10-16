package com.zenithai.countai.util

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

/** Saves session photos to app-internal storage (private, no permissions needed). */
object ImageSaver {

    fun saveBitmap(context: Context, bitmap: Bitmap, fileName: String): String {
        val dir = File(context.filesDir, "sessions").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return file.absolutePath
    }
}
