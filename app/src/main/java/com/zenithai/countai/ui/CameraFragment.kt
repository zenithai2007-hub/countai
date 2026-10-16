package com.zenithai.countai.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zenithai.countai.R
import com.zenithai.countai.data.SessionRepository
import com.zenithai.countai.databinding.DialogDetectionResultBinding
import com.zenithai.countai.databinding.FragmentCameraBinding
import com.zenithai.countai.databinding.ItemCountRowBinding
import com.zenithai.countai.detection.Detector
import com.zenithai.countai.detection.ModelLoadException
import com.zenithai.countai.detection.ObjectTracker
import com.zenithai.countai.detection.TfliteObjectDetector
import com.zenithai.countai.util.PermissionHelper
import com.zenithai.countai.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * Main screen: live camera + real-time detection, per-type counts with manual
 * correction, label filter chips, pause/reset, save/share, and photo counting.
 *
 * Pipeline per analyzed frame (background thread):
 *   YUV_420_888 → Bitmap → rotate → center-crop square → Detector →
 *   label filter → ObjectTracker → UI (boxes + counts).
 */
class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private val detectorLock = Any()
    private var detector: Detector? = null
    private val tracker = ObjectTracker()

    private var cameraExecutor: ExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraRunning = false
    private var paused = false
    private var lastAnalyzeMs = 0L

    // Geometry of the last analyzed frame (for overlay mapping).
    private var frameW = 0
    private var frameH = 0
    private var cropX = 0
    private var cropY = 0
    private var cropSize = 0

    /** Manual +/- corrections per label; added on top of the tracker's totals. */
    private val manualAdjust = mutableMapOf<String, Int>()

    /** Empty = count everything; otherwise only these labels are counted. */
    private var selectedLabels: Set<String> = emptySet()

    private var lastRowsSignature = ""

    private lateinit var repository: SessionRepository

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        PermissionHelper.handleResult(
            this, granted,
            onGranted = { startCamera() },
            onDenied = { permanently ->
                if (permanently) PermissionHelper.showGoToSettingsDialog(this)
                else updateStatus(getString(R.string.permission_denied))
            },
        )
    }

    private val photoPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) detectOnPhoto(uri) }

    // ---------------- lifecycle ----------------

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = SessionRepository(requireContext())
        buildLabelChips()

        binding.btnStartCamera.setOnClickListener { toggleCamera() }
        binding.btnPause.setOnClickListener { togglePause() }
        binding.btnReset.setOnClickListener { resetSession() }
        binding.btnCapture.setOnClickListener { captureAndSave() }
        binding.btnPhoto.setOnClickListener { photoPicker.launch("image/*") }
        binding.btnShare.setOnClickListener { shareCurrentResult() }
        updateButtons()
        updateStatus(getString(R.string.tap_start_camera))
    }

    override fun onResume() {
        super.onResume()
        // (Re)build the detector on a background thread so Settings changes
        // (GPU/NNAPI toggles) take effect when coming back.
        lifecycleScope.launch(Dispatchers.Default) {
            val d = ensureDetector()
            withContext(Dispatchers.Main) {
                if (!isAdded || _binding == null) return@withContext
                if (d == null) updateStatus(getString(R.string.model_missing))
                else if (!cameraRunning) updateStatus(getString(R.string.tap_start_camera))
            }
        }
    }

    override fun onPause() {
        super.onPause()
        stopCamera()
        synchronized(detectorLock) {
            try {
                detector?.close()
            } catch (e: Exception) {
                Log.w(TAG, "detector.close failed", e)
            }
            detector = null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor?.shutdown()
        cameraExecutor = null
        _binding = null
    }

    // ---------------- detector ----------------

    private fun ensureDetector(): Detector? = synchronized(detectorLock) {
        var d = detector
        if (d == null) {
            d = try {
                TfliteObjectDetector(
                    requireContext(),
                    useGpu = Prefs.useGpu(requireContext()),
                    useNnapi = Prefs.useNnapi(requireContext()),
                )
            } catch (e: ModelLoadException) {
                Log.e(TAG, "model missing", e)
                null
            } catch (e: Exception) {
                Log.e(TAG, "detector init failed", e)
                null
            }
            detector = d
        }
        d
    }

    // ---------------- camera ----------------

    private fun toggleCamera() {
        if (cameraRunning) {
            stopCamera()
        } else {
            PermissionHelper.requestCamera(this, permissionLauncher, onGranted = { startCamera() })
        }
    }

    private fun startCamera() {
        if (!isAdded || cameraRunning) return
        val providerFuture = ProcessCameraProvider.getInstance(requireContext())
        providerFuture.addListener({
            try {
                cameraProvider = providerFuture.get()
                val b = _binding ?: return@addListener

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(b.previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(getCameraExecutor(), analyzer)

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    viewLifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
                cameraRunning = true
                updateButtons()
                updateStatus(getString(R.string.point_at_objects))
            } catch (e: Exception) {
                Log.e(TAG, "camera start failed", e)
                updateStatus("Camera error: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.w(TAG, "unbind failed", e)
        }
        cameraRunning = false
        updateButtons()
    }

    private fun getCameraExecutor(): ExecutorService {
        var e = cameraExecutor
        if (e == null || e.isShutdown) {
            e = Executors.newSingleThreadExecutor()
            cameraExecutor = e
        }
        return e
    }

    private val analyzer = ImageAnalysis.Analyzer { proxy ->
        try {
            val det = if (!paused) ensureDetector() else null
            if (det != null) {
                val now = SystemClock.uptimeMillis()
                val intervalMs = 1000L / Prefs.fps(requireContext())
                if (now - lastAnalyzeMs >= intervalMs) {
                    lastAnalyzeMs = now
                    analyzeFrame(proxy, det)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "analyze failed", e)
        } finally {
            proxy.close()
        }
    }

    private fun analyzeFrame(proxy: androidx.camera.core.ImageProxy, det: Detector) {
        val image = proxy.image ?: return
        val rotation = proxy.imageInfo.rotationDegrees

        var bmp = yuvToBitmap(image) ?: return
        if (rotation != 0) {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            bmp.recycle()
            bmp = rotated
        }

        frameW = bmp.width
        frameH = bmp.height
        cropSize = min(frameW, frameH)
        cropX = (frameW - cropSize) / 2
        cropY = (frameH - cropSize) / 2
        val square = Bitmap.createBitmap(bmp, cropX, cropY, cropSize, cropSize)
        bmp.recycle()

        val threshold = Prefs.confidence(requireContext())
        var detections = det.detect(square, threshold)
        square.recycle()

        if (selectedLabels.isNotEmpty()) {
            detections = detections.filter { it.label in selectedLabels }
        }

        val visible = tracker.update(detections)
        val live = tracker.liveCounts()
        val session = effectiveSessionCounts()
        val avgConf = if (visible.isNotEmpty()) {
            visible.sumOf { it.confidence.toDouble() } / visible.size
        } else {
            1.0
        }

        activity?.runOnUiThread {
            val b = _binding ?: return@runOnUiThread
            b.overlay.setDetections(visible, frameW, frameH, cropX, cropY, cropSize)
            b.tvLiveCount.text = live.values.sum().toString()
            b.tvTotalCount.text = session.values.sum().toString()
            renderCountRows(live, session)
            b.warningBadge.visibility =
                if (visible.isNotEmpty() && avgConf < LOW_CONF_THRESHOLD) View.VISIBLE else View.GONE
        }
    }

    // ---------------- YUV conversion ----------------

    private fun yuvToBitmap(image: Image): Bitmap? = try {
        val nv21 = yuv420ToNv21(image)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 80, out)
        val bytes = out.toByteArray()
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        Log.w(TAG, "YUV conversion failed", e)
        null
    }

    /**
     * Correct YUV_420_888 → NV21 handling arbitrary row/pixel strides
     * (the naive plane-concat breaks on many devices).
     */
    private fun yuv420ToNv21(image: Image): ByteArray {
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

    // ---------------- counts UI ----------------

    /** Tracker totals + manual corrections (never below zero). */
    private fun effectiveSessionCounts(): Map<String, Int> {
        val base = tracker.sessionCounts().toMutableMap()
        for ((label, delta) in manualAdjust) {
            base[label] = max(0, (base[label] ?: 0) + delta)
        }
        return base
    }

    private fun renderCountRows(live: Map<String, Int>, session: Map<String, Int>) {
        val b = _binding ?: return
        val labels = session.keys.sortedByDescending { session[it] ?: 0 }.take(MAX_ROWS)
        val sig = labels.joinToString("|") { "$it:${live[it] ?: 0}:${session[it] ?: 0}" }
        if (sig == lastRowsSignature) return
        lastRowsSignature = sig

        b.countRows.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        for (label in labels) {
            val row = ItemCountRowBinding.inflate(inflater, b.countRows, false)
            row.rowLabel.text = label.replaceFirstChar { it.uppercase() }
            row.rowLive.text = "live ${live[label] ?: 0}"
            row.rowTotal.text = (session[label] ?: 0).toString()
            row.rowMinus.setOnClickListener { adjustCount(label, -1) }
            row.rowPlus.setOnClickListener { adjustCount(label, 1) }
            b.countRows.addView(row.root)
        }
        if (session.size > MAX_ROWS) {
            val more = android.widget.TextView(requireContext()).apply {
                text = "+${session.size - MAX_ROWS} more"
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                textSize = 12f
            }
            b.countRows.addView(more)
        }
    }

    private fun adjustCount(label: String, delta: Int) {
        val trackerCount = tracker.sessionCounts()[label] ?: 0
        val newDelta = ((manualAdjust[label] ?: 0) + delta).coerceAtLeast(-trackerCount)
        manualAdjust[label] = newDelta
        lastRowsSignature = "" // force re-render
        val b = _binding ?: return
        val session = effectiveSessionCounts()
        b.tvTotalCount.text = session.values.sum().toString()
        renderCountRows(tracker.liveCounts(), session)
    }

    private fun togglePause() {
        paused = !paused
        updateButtons()
        updateStatus(
            getString(if (paused) R.string.status_paused else R.string.point_at_objects)
        )
    }

    private fun resetSession() {
        tracker.reset()
        manualAdjust.clear()
        lastRowsSignature = ""
        val b = _binding ?: return
        b.overlay.clear()
        b.tvLiveCount.text = "0"
        b.tvTotalCount.text = "0"
        b.countRows.removeAllViews()
        b.warningBadge.visibility = View.GONE
        updateStatus(getString(R.string.status_reset))
    }

    private fun buildLabelChips() {
        val b = _binding ?: return
        val group = b.labelChips
        group.removeAllViews()
        val all = listOf(null) + COMMON_LABELS // null = "All"
        for (raw in all) {
            val chip = Chip(requireContext()).apply {
                tag = raw // null means "count everything"
                text = if (raw == null) getString(R.string.chip_all)
                else raw.replaceFirstChar { it.uppercase() }
                isCheckable = true
                isChecked = raw == null
                id = View.generateViewId()
            }
            group.addView(chip)
        }
        group.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val checked = group.findViewById<Chip>(checkedIds.first()) ?: return@setOnCheckedStateChangeListener
            val raw = checked.tag as String?
            selectedLabels = if (raw == null) emptySet() else setOf(raw)
            resetSession()
            updateStatus(getString(R.string.mode_changed))
        }
    }

    // ---------------- save / share ----------------

    private fun captureAndSave() {
        val b = _binding ?: return
        val counts = effectiveSessionCounts()
        if (counts.values.sum() == 0) {
            toast(R.string.nothing_to_count)
            return
        }
        val bitmap = b.previewView.bitmap
        if (bitmap == null) {
            toast(R.string.photo_failed)
            return
        }
        lifecycleScope.launch {
            try {
                repository.saveSession(counts, bitmap, "live")
                toast(R.string.saved_toast)
            } catch (e: Exception) {
                Log.e(TAG, "save failed", e)
                toast("Save failed: ${e.message}")
            }
        }
    }

    private fun shareCurrentResult() {
        val counts = effectiveSessionCounts()
        if (counts.values.sum() == 0) {
            toast(R.string.nothing_to_count)
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject))
            putExtra(Intent.EXTRA_TEXT, repository.shareText(counts))
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    // ---------------- photo counting ----------------

    private fun detectOnPhoto(uri: Uri) {
        updateStatus(getString(R.string.detecting))
        lifecycleScope.launch(Dispatchers.Default) {
            val original = withContext(Dispatchers.IO) {
                try {
                    requireContext().contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "photo decode failed", e)
                    null
                }
            }
            if (original == null) {
                withContext(Dispatchers.Main) { toast(R.string.photo_failed) }
                return@launch
            }
            val det = ensureDetector()
            if (det == null) {
                original.recycle()
                withContext(Dispatchers.Main) { updateStatus(getString(R.string.model_missing)) }
                return@launch
            }

            // Same square center-crop the live pipeline uses.
            val side = min(original.width, original.height)
            val squareFull = Bitmap.createBitmap(
                original,
                (original.width - side) / 2,
                (original.height - side) / 2,
                side, side,
            )
            original.recycle()
            val input = Bitmap.createScaledBitmap(squareFull, det.inputSize, det.inputSize, true)
            val detections = det.detect(input, Prefs.confidence(requireContext()))
            input.recycle()

            // Annotated display copy (max 800px).
            val dispSize = min(side, 800)
            val display = Bitmap.createScaledBitmap(squareFull, dispSize, dispSize, true)
                .copy(Bitmap.Config.ARGB_8888, true)
            squareFull.recycle()
            drawDetectionsOnBitmap(display, detections)

            withContext(Dispatchers.Main) {
                if (!isAdded || _binding == null) return@withContext
                updateStatus(getString(R.string.tap_start_camera))
                showPhotoResultDialog(display, detections)
            }
        }
    }

    private fun drawDetectionsOnBitmap(bitmap: Bitmap, detections: List<com.zenithai.countai.detection.Detection>) {
        val canvas = Canvas(bitmap)
        val boxPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = max(3f, bitmap.width / 160f)
            isAntiAlias = true
            color = 0xFF00E676.toInt()
        }
        val textPaint = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            textSize = max(28f, bitmap.width / 22f)
            isAntiAlias = true
        }
        val bgPaint = Paint().apply { color = 0xFF00E676.toInt(); isAntiAlias = true }
        for (d in detections) {
            val l = d.box.left * bitmap.width
            val t = d.box.top * bitmap.height
            val r = d.box.right * bitmap.width
            val b = d.box.bottom * bitmap.height
            canvas.drawRect(l, t, r, b, boxPaint)
            val text = "${d.label} ${(d.confidence * 100).toInt()}%"
            val tw = textPaint.measureText(text)
            val bgTop = (t - textPaint.textSize - 16f).coerceAtLeast(0f)
            canvas.drawRect(l, bgTop, l + tw + 20f, t, bgPaint)
            canvas.drawText(text, l + 10f, t - 10f, textPaint)
        }
    }

    private fun showPhotoResultDialog(
        annotated: Bitmap,
        detections: List<com.zenithai.countai.detection.Detection>,
    ) {
        val counts = detections.groupingBy { it.label }.eachCount()
        val dlg = DialogDetectionResultBinding.inflate(layoutInflater)
        dlg.dlgImage.setImageBitmap(annotated)
        dlg.dlgCounts.text = if (counts.isEmpty()) {
            getString(R.string.photo_no_objects)
        } else {
            counts.toList().sortedByDescending { it.second }
                .joinToString("\n") { (label, c) -> "${label.replaceFirstChar { it.uppercase() }}: $c" }
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.photo_count)
            .setView(dlg.root)
            .create()
        dlg.dlgSave.isEnabled = counts.isNotEmpty()
        dlg.dlgSave.setOnClickListener {
            lifecycleScope.launch {
                try {
                    repository.saveSession(counts, annotated, "photo")
                    toast(R.string.saved_toast)
                } catch (e: Exception) {
                    Log.e(TAG, "photo save failed", e)
                    toast("Save failed: ${e.message}")
                }
            }
            dialog.dismiss()
        }
        dlg.dlgClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // ---------------- helpers ----------------

    private fun updateButtons() {
        val b = _binding ?: return
        b.btnStartCamera.text =
            getString(if (cameraRunning) R.string.stop_camera else R.string.start_camera)
        b.btnPause.text = getString(if (paused) R.string.resume else R.string.pause)
        b.btnPause.isEnabled = cameraRunning
    }

    private fun updateStatus(text: String) {
        _binding?.tvStatus?.text = text
    }

    private fun toast(resId: Int) {
        context?.let { Toast.makeText(it, resId, Toast.LENGTH_SHORT).show() }
    }

    private fun toast(text: String) {
        context?.let { Toast.makeText(it, text, Toast.LENGTH_LONG).show() }
    }

    companion object {
        private const val TAG = "CameraFragment"

        /** Below this average confidence we show the "improve lighting" badge. */
        private const val LOW_CONF_THRESHOLD = 0.45

        private const val MAX_ROWS = 6

        /** Quick-pick labels for the "count only" chips. */
        private val COMMON_LABELS = listOf(
            "person", "bicycle", "car", "motorcycle", "bus", "truck",
            "bottle", "cup", "chair", "couch", "bed", "dining table",
            "tv", "laptop", "cell phone", "book", "dog", "cat",
            "backpack", "handbag", "suitcase",
        )
    }
}
