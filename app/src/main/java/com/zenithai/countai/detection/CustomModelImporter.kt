package com.zenithai.countai.detection

import android.content.Context
import android.net.Uri
import android.util.Log
import com.zenithai.countai.util.Prefs
import java.io.File

/**
 * Custom (user-trained) model support.
 *
 * Honest scope: training does NOT happen on the phone — that needs a PC
 * (Teachable Machine, Colab + YOLO, etc.). This importer only takes a
 * finished `.tflite` + labels `.txt` the user already trained, copies them
 * into app-private storage, and [ModelManager] loads them instead of the
 * bundled COCO model. The [Detector] interface means zero UI changes.
 */
object CustomModelImporter {

    private const val DIR = "custom_model"

    /**
     * Copies [modelUri] (.tflite) and [labelsUri] (.txt) into app storage.
     * @return Pair(modelPath, labelsPath) on success, null on failure.
     */
    fun import(context: Context, modelUri: Uri, labelsUri: Uri): Pair<String, String>? {
        return try {
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            val modelFile = File(dir, "custom.tflite")
            val labelsFile = File(dir, "custom_labels.txt")
            context.contentResolver.openInputStream(modelUri)?.use { input ->
                modelFile.outputStream().use { input.copyTo(it) }
            } ?: return null
            context.contentResolver.openInputStream(labelsUri)?.use { input ->
                labelsFile.outputStream().use { input.copyTo(it) }
            } ?: return null
            if (modelFile.length() < 1024) {
                Log.w(TAG, "imported model suspiciously small")
                return null
            }
            Prefs.setCustomModel(context, modelFile.absolutePath, labelsFile.absolutePath)
            modelFile.absolutePath to labelsFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "custom model import failed", e)
            null
        }
    }

    fun clear(context: Context) {
        try {
            File(context.filesDir, DIR).deleteRecursively()
        } catch (e: Exception) {
            Log.w(TAG, "clear failed", e)
        }
        Prefs.setCustomModel(context, null, null)
    }

    fun isCustomActive(context: Context): Boolean =
        Prefs.customModelPath(context)?.let { File(it).exists() } == true

    private const val TAG = "CustomModelImporter"
}

/**
 * Single place that builds the right [Detector]: the user's custom model
 * when one is imported and valid, otherwise the bundled COCO model.
 */
object ModelManager {

    fun createDetector(context: Context): Detector {
        val modelPath = Prefs.customModelPath(context)
        val labelsPath = Prefs.customLabelsPath(context)
        if (modelPath != null && labelsPath != null &&
            java.io.File(modelPath).exists() && java.io.File(labelsPath).exists()
        ) {
            try {
                return TfliteObjectDetector(
                    context,
                    modelFilePath = modelPath,
                    labelsFilePath = labelsPath,
                    useGpu = Prefs.useGpu(context),
                    useNnapi = Prefs.useNnapi(context),
                )
            } catch (e: Exception) {
                android.util.Log.w("ModelManager", "custom model failed, using bundled", e)
            }
        }
        return TfliteObjectDetector(
            context,
            useGpu = Prefs.useGpu(context),
            useNnapi = Prefs.useNnapi(context),
        )
    }
}
