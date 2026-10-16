package com.zenithai.countai.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

/**
 * [Detector] backed by a TensorFlow Lite SSD-style object-detection model
 * (default: quantized SSD-MobileNetV1, 300x300, uint8, COCO classes).
 *
 * The model is inspected at load time: input size and input type (uint8 vs
 * float32) are read from the tensors, so a custom-trained replacement model
 * with a different input size keeps working as long as it follows the
 * standard 4-output detection format:
 * outputs = boxes [1,N,4] (ymin,xmin,ymax,xmax normalized),
 *           classes [1,N], scores [1,N], num_detections [1].
 *
 * Acceleration: tries the GPU delegate first (when the device supports it),
 * then NNAPI if enabled, otherwise multi-threaded CPU. Every delegate is
 * wrapped in try/catch — a failure silently falls back instead of crashing.
 */
class TfliteObjectDetector(
    context: Context,
    modelAsset: String = "detect.tflite",
    labelsAsset: String = "labels.txt",
    useGpu: Boolean = true,
    useNnapi: Boolean = false,
    numThreads: Int = 4,
) : Detector {

    private val interpreter: Interpreter
    private val labels: List<String>
    private val maxDetections: Int
    private val isQuantized: Boolean
    private var gpuDelegate: GpuDelegate? = null
    private var nnApiDelegate: NnApiDelegate? = null

    override val inputSize: Int
    override val isReady: Boolean = true

    init {
        labels = try {
            context.assets.open(labelsAsset).bufferedReader().readLines()
        } catch (e: Exception) {
            throw ModelLoadException("Missing asset: $labelsAsset", e)
        }

        val options = Interpreter.Options().apply {
            setNumThreads(numThreads)
            if (useGpu) {
                try {
                    if (CompatibilityList().isDelegateSupportedOnThisDevice) {
                        gpuDelegate = GpuDelegate()
                        addDelegate(gpuDelegate)
                        Log.i(TAG, "GPU delegate enabled")
                    } else {
                        Log.i(TAG, "GPU delegate not supported on this device, using CPU/NNAPI")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "GPU delegate failed, falling back", e)
                    gpuDelegate = null
                }
            }
            if (useNnapi && gpuDelegate == null) {
                try {
                    nnApiDelegate = NnApiDelegate()
                    addDelegate(nnApiDelegate)
                    Log.i(TAG, "NNAPI delegate enabled")
                } catch (e: Exception) {
                    Log.w(TAG, "NNAPI delegate failed, falling back to CPU", e)
                    nnApiDelegate = null
                }
            }
        }

        interpreter = try {
            Interpreter(loadModelFile(context, modelAsset), options)
        } catch (e: Exception) {
            throw ModelLoadException(
                "Could not load $modelAsset — run scripts/download_model.sh and rebuild.", e
            )
        }

        val inShape = interpreter.getInputTensor(0).shape() // [1, H, W, 3]
        inputSize = inShape[1]
        isQuantized = interpreter.getInputTensor(0).dataType() == DataType.UINT8

        // Sanity-check the classic 4-output detection signature.
        maxDetections = interpreter.getOutputTensor(0).shape()[1]
        val sigOk = interpreter.outputTensorCount == 4 &&
            interpreter.getOutputTensor(0).shape().let { it.size == 3 && it[2] == 4 } &&
            interpreter.getOutputTensor(3).shape().let { it.size == 2 && it[1] == 1 }
        if (!sigOk) {
            Log.w(TAG, "Unexpected output signature — detection results may be wrong. " +
                "Expected [1,N,4],[1,N],[1,N],[1,1].")
        }
        Log.i(TAG, "model ready: input=${inShape.toList()} quantized=$isQuantized " +
            "maxDetections=$maxDetections labels=${labels.size}")
    }

    override fun detect(bitmap: Bitmap, threshold: Float): List<Detection> {
        val input = if (isQuantized) toQuantizedBuffer(bitmap) else toFloatBuffer(bitmap)

        val boxes = Array(1) { Array(maxDetections) { FloatArray(4) } }
        val classes = Array(1) { FloatArray(maxDetections) }
        val scores = Array(1) { FloatArray(maxDetections) }
        val numDetections = FloatArray(1)
        val outputs: Map<Int, Any> =
            mapOf(0 to boxes, 1 to classes, 2 to scores, 3 to numDetections)

        interpreter.runForMultipleInputsOutputs(arrayOf(input), outputs)

        val n = min(numDetections[0].toInt(), maxDetections)
        val found = ArrayList<Detection>(n)
        for (i in 0 until n) {
            val score = scores[0][i]
            if (score < threshold) continue
            val classId = classes[0][i].toInt()
            val label = labels.getOrNull(classId) ?: continue
            if (label == "???" || label.equals("background", ignoreCase = true)) continue
            val b = boxes[0][i] // ymin, xmin, ymax, xmax — normalized
            val rect = RectF(
                b[1].coerceIn(0f, 1f),
                b[0].coerceIn(0f, 1f),
                b[3].coerceIn(0f, 1f),
                b[2].coerceIn(0f, 1f),
            )
            if (rect.width() <= 0f || rect.height() <= 0f) continue
            found.add(Detection(label, rect, score))
        }
        // The model already runs NMS internally, but a second light pass makes
        // overlapping/partially-hidden objects behave more sanely.
        return nonMaxSuppression(found)
    }

    override fun close() {
        try {
            interpreter.close()
        } catch (e: Exception) {
            Log.w(TAG, "interpreter.close failed", e)
        }
        gpuDelegate?.close()
        nnApiDelegate?.close()
    }

    // ---------- input conversion ----------

    private fun toQuantizedBuffer(bitmap: Bitmap): ByteBuffer {
        val scaled = scaledInput(bitmap)
        val buffer = ByteBuffer
            .allocateDirect(inputSize * inputSize * 3)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputSize * inputSize)
        scaled.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (p in pixels) {
            buffer.put(((p shr 16) and 0xFF).toByte()) // R
            buffer.put(((p shr 8) and 0xFF).toByte())  // G
            buffer.put((p and 0xFF).toByte())          // B
        }
        if (scaled !== bitmap) scaled.recycle()
        return buffer
    }

    private fun toFloatBuffer(bitmap: Bitmap): ByteBuffer {
        val scaled = scaledInput(bitmap)
        val byteBuffer = ByteBuffer
            .allocateDirect(inputSize * inputSize * 3 * 4)
            .order(ByteOrder.nativeOrder())
        val buffer = byteBuffer.asFloatBuffer()
        val pixels = IntArray(inputSize * inputSize)
        scaled.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (p in pixels) {
            // Standard SSD float normalization.
            buffer.put((((p shr 16) and 0xFF) - 127.5f) / 127.5f)
            buffer.put((((p shr 8) and 0xFF) - 127.5f) / 127.5f)
            buffer.put(((p and 0xFF) - 127.5f) / 127.5f)
        }
        if (scaled !== bitmap) scaled.recycle()
        return byteBuffer
    }

    private fun scaledInput(bitmap: Bitmap): Bitmap =
        if (bitmap.width == inputSize && bitmap.height == inputSize) bitmap
        else Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

    // ---------- helpers ----------

    private fun nonMaxSuppression(detections: List<Detection>, iouThreshold: Float = 0.5f): List<Detection> {
        val result = ArrayList<Detection>()
        for ((_, group) in detections.groupBy { it.label }) {
            val sorted = group.sortedByDescending { it.confidence }.toMutableList()
            while (sorted.isNotEmpty()) {
                val best = sorted.removeAt(0)
                result.add(best)
                val it = sorted.iterator()
                while (it.hasNext()) {
                    if (iou(it.next().box, best.box) > iouThreshold) it.remove()
                }
            }
        }
        return result.sortedByDescending { it.confidence }
    }

    private fun loadModelFile(context: Context, assetName: String): ByteBuffer {
        val fd = context.assets.openFd(assetName)
        val input = FileInputStream(fd.fileDescriptor)
        val channel = input.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    companion object {
        private const val TAG = "TfliteObjectDetector"
    }
}

/** Thrown when the model file cannot be loaded (missing/corrupt). */
class ModelLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
