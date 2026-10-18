package com.zenithai.countai.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
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
import com.zenithai.countai.detection.ColorFilter
import com.zenithai.countai.detection.Detector
import com.zenithai.countai.detection.ModelLoadException
import com.zenithai.countai.detection.ModelManager
import com.zenithai.countai.detection.ObjectTracker
import com.zenithai.countai.detection.TrackedObject
import com.zenithai.countai.util.FrameConverter
import com.zenithai.countai.util.CommandParser
import com.zenithai.countai.util.LocationHelper
import com.zenithai.countai.util.NetworkMonitor
import com.zenithai.countai.util.PermissionHelper
import com.zenithai.countai.util.Prefs
import com.zenithai.countai.util.VoiceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    /** Natural-language command filter (null = none). Overrides chips. */
    private var commandLabels: Set<String>? = null
    private var commandColor: String? = null
    private var commandText: String? = null

    /** Last visible tracked objects (for Verify Count). */
    private var lastVisible: List<TrackedObject> = emptyList()

    private var voiceHelper: VoiceHelper? = null
    private var listeningToast: Toast? = null

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

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startVoiceListening()
        else toast(R.string.mic_permission_needed)
    }

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
        binding.btnVerify.setOnClickListener { showVerifyDialog() }
        binding.btnMic.setOnClickListener { onMicTapped() }
        binding.btnSendCommand.setOnClickListener {
            processCommand(binding.etCommand.text.toString())
        }
        binding.etCommand.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                processCommand(binding.etCommand.text.toString())
                true
            } else false
        }
        updateButtons()
        updateStatus(getString(R.string.tap_start_camera))
        updateOfflineBadge()
        maybeShowCoachMarks()
    }

    override fun onResume() {
        super.onResume()
        updateOfflineBadge()
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
        voiceHelper?.destroy()
        voiceHelper = null
        listeningToast?.cancel()
        _binding = null
    }

    // ---------------- detector ----------------

    private fun ensureDetector(): Detector? = synchronized(detectorLock) {
        var d = detector
        if (d == null) {
            d = try {
                ModelManager.createDetector(requireContext())
            } catch (e: ModelLoadException) {
                Log.e(TAG, "model missing", e)
                null
            } catch (e: Throwable) {
                // Last line of defense: ANY failure here (including Errors like
                // NoClassDefFoundError) must degrade to "no detector", never
                // crash the app. The UI shows "model missing" instead.
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
                val intervalMs = 1000L / Prefs.effectiveFps(requireContext())
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

        var bmp = FrameConverter.yuvToBitmap(image) ?: return
        bmp = FrameConverter.rotate(bmp, rotation)

        frameW = bmp.width
        frameH = bmp.height
        cropSize = min(frameW, frameH)
        cropX = (frameW - cropSize) / 2
        cropY = (frameH - cropSize) / 2
        val square = Bitmap.createBitmap(bmp, cropX, cropY, cropSize, cropSize)
        bmp.recycle()

        val threshold = Prefs.confidence(requireContext())
        var detections = det.detect(square, threshold)

        // Label filter: chips first, voice/text command overrides.
        val filterLabels = effectiveFilterLabels()
        if (filterLabels.isNotEmpty()) {
            detections = detections.filter { it.label in filterLabels }
        }
        // Color filter ("red bottles"): classify inside each box.
        val wantColor = commandColor
        if (wantColor != null) {
            detections = detections.filter { ColorFilter.classify(square, it.box) == wantColor }
        }
        square.recycle()

        val visible = tracker.update(detections)
        lastVisible = visible
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

    /** Chip selection, or the voice/text command filter when active. */
    private fun effectiveFilterLabels(): Set<String> {
        val cmd = commandLabels
        if (cmd != null) {
            return if ("box" in cmd) {
                (cmd - "box") + CommandParser.expandBoxLabel()
            } else cmd
        }
        return selectedLabels
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
        lastVisible = emptyList()
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
            // Manual chip choice clears any voice/text command filter.
            commandLabels = null
            commandColor = null
            commandText = null
            _binding?.etCommand?.setText("")
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
                repository.saveSession(
                    counts, bitmap, "live",
                    LocationHelper.lastKnownLabel(requireContext()),
                )
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

    // ---------------- voice + text commands ----------------

    private fun processCommand(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        val cmd = CommandParser.parse(text)
        if (cmd.labels.isEmpty() && cmd.color == null) {
            if (cmd.unsupportedAttribute != null) {
                toast(getString(R.string.attr_unsupported, cmd.unsupportedAttribute))
            } else {
                toast(R.string.command_no_match)
            }
            return
        }
        // "count everything" reset phrase clears the command filter.
        if ("everything" in text.lowercase() || "sab kuch" in text.lowercase() || "سب کچھ" in text) {
            commandLabels = null
            commandColor = null
        } else {
            commandLabels = cmd.labels.ifEmpty { null }
            commandColor = cmd.color
        }
        commandText = text
        if (cmd.unsupportedAttribute != null && cmd.labels.isNotEmpty()) {
            toast(getString(R.string.attr_unsupported, cmd.unsupportedAttribute))
        }
        resetSession()
        val desc = buildString {
            cmd.color?.let { append("$it ") }
            append(if (cmd.labels.isEmpty()) getString(R.string.chip_all) else cmd.labels.joinToString(", "))
        }
        updateStatus(getString(R.string.command_active, desc))
        if (cmd.isQuestion) {
            // Answer from the current (just-reset, so next-frame) counts — reply shortly.
            binding.tvStatus.postDelayed({
                if (!isAdded) return@postDelayed
                val session = effectiveSessionCounts()
                val total = session.values.sum()
                val answer = getString(R.string.voice_answer, desc, total)
                updateStatus(answer)
                voiceHelper?.speak(answer)
            }, 2500)
        }
    }

    private fun onMicTapped() {
        val ctx = requireContext()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startVoiceListening()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startVoiceListening() {
        val helper = voiceHelper ?: VoiceHelper(requireContext(), object : VoiceHelper.Listener {
            override fun onVoiceResult(text: String) {
                listeningToast?.cancel()
                _binding?.etCommand?.setText(text)
                processCommand(text)
            }

            override fun onVoiceError(message: String) {
                listeningToast?.cancel()
                val msg = when (message) {
                    "no_speech" -> getString(R.string.voice_no_speech)
                    "mic_permission" -> getString(R.string.mic_permission_needed)
                    else -> getString(R.string.voice_failed)
                }
                toast(msg)
            }
        }).also { voiceHelper = it }
        if (!helper.isRecognitionAvailable()) {
            toast(R.string.voice_failed)
            return
        }
        listeningToast = Toast.makeText(requireContext(), R.string.mic_listening, Toast.LENGTH_LONG)
        listeningToast?.show()
        helper.startListening()
    }

    // ---------------- verify count (trust & accuracy) ----------------

    private fun showVerifyDialog() {
        val threshold = Prefs.confidence(requireContext())
        val questionable = lastVisible.filter { it.confidence < threshold + 0.15f }
        if (questionable.isEmpty()) {
            toast(R.string.no_questionable)
            return
        }
        val ctx = requireContext()
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        val note = TextView(ctx).apply {
            text = getString(R.string.accuracy_disclaimer)
            setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            textSize = 13f
        }
        root.addView(note)
        for (t in questionable) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                val pad = (8 * resources.displayMetrics.density).toInt()
                setPadding(0, pad, 0, pad)
            }
            val label = TextView(ctx).apply {
                text = "${t.label} — ${(t.confidence * 100).toInt()}%"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val remove = com.google.android.material.button.MaterialButton(ctx).apply {
                text = getString(R.string.verify_remove)
                setOnClickListener {
                    adjustCount(t.label, -1)
                    row.visibility = View.GONE
                    toast(getString(R.string.verify_removed, t.label))
                }
            }
            row.addView(label)
            row.addView(remove)
            root.addView(row)
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.verify_title)
            .setView(root)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    // ---------------- badges, coach marks ----------------

    private fun updateOfflineBadge() {
        val b = _binding ?: return
        b.offlineBadge.visibility =
            if (NetworkMonitor.isOnline(requireContext())) View.GONE else View.VISIBLE
    }

    private fun maybeShowCoachMarks() {
        if (Prefs.coachDone(requireContext())) return
        val steps = listOf(
            getString(R.string.coach_1),
            getString(R.string.coach_2),
            getString(R.string.coach_3),
        )
        fun showStep(i: Int) {
            if (!isAdded) return
            if (i >= steps.size) {
                Prefs.setCoachDone(requireContext())
                return
            }
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(steps[i])
                .setPositiveButton(
                    if (i == steps.lastIndex) getString(R.string.coach_done)
                    else getString(R.string.coach_next)
                ) { _, _ -> showStep(i + 1) }
                .setCancelable(false)
                .show()
        }
        binding.root.post { showStep(0) }
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
