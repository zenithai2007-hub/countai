package com.zenithai.countai.ui

import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zenithai.countai.R
import com.zenithai.countai.data.SessionRepository
import com.zenithai.countai.databinding.DialogCountcamResultBinding
import com.zenithai.countai.databinding.FragmentCountcamBinding
import com.zenithai.countai.detection.Detector
import com.zenithai.countai.detection.ModelLoadException
import com.zenithai.countai.detection.ModelManager
import com.zenithai.countai.detection.ObjectTracker
import com.zenithai.countai.util.ExcelExporter
import com.zenithai.countai.util.LocationHelper
import com.zenithai.countai.util.PdfReport
import com.zenithai.countai.util.PermissionHelper
import com.zenithai.countai.util.Prefs
import com.zenithai.countai.util.ResultCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * COUNT CAM — the signature one-tap mode.
 *
 * Open → tap Smart Count → point at objects. The AI detects, tracks and
 * counts automatically; when the counts stay stable for ~2 seconds (or 8s
 * pass, or the user taps Done) a result card appears:
 *   COUNT COMPLETE ✅  +  per-type rows  +  total
 *   [Save] [Share] [Export] [Recount]
 */
class CountCamFragment : Fragment() {

    private var _binding: FragmentCountcamBinding? = null
    private val binding get() = _binding!!

    private var session: CameraSession? = null
    private var detector: Detector? = null
    private val tracker = ObjectTracker()

    private var counting = false
    private var countJob: Job? = null
    private var lastStableSignature = ""
    private var stableSinceMs = 0L
    private var startMs = 0L

    private lateinit var repository: SessionRepository

    private val permissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) beginCount()
        else Toast.makeText(requireContext(), R.string.permission_denied, Toast.LENGTH_LONG).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCountcamBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = SessionRepository(requireContext())
        binding.btnSmartCount.setOnClickListener { onSmartCountTapped() }
        binding.btnDone.setOnClickListener { finishCount() }
        updateUi()
    }

    override fun onPause() {
        super.onPause()
        stopEverything()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopEverything()
        _binding = null
    }

    private fun onSmartCountTapped() {
        if (counting) {
            finishCount()
            return
        }
        PermissionHelper.requestCamera(this, permissionLauncher, onGranted = { beginCount() })
    }

    private fun beginCount() {
        lifecycleScope.launch(Dispatchers.Default) {
            val det = try {
                ModelManager.createDetector(requireContext())
            } catch (e: ModelLoadException) {
                Log.e(TAG, "model missing", e)
                null
            } catch (e: Exception) {
                Log.e(TAG, "detector failed", e)
                null
            }
            withContext(Dispatchers.Main) {
                if (!isAdded || _binding == null) return@withContext
                if (det == null) {
                    Toast.makeText(requireContext(), R.string.model_missing, Toast.LENGTH_LONG).show()
                    return@withContext
                }
                detector = det
                tracker.reset()
                counting = true
                startMs = SystemClock.uptimeMillis()
                stableSinceMs = startMs
                lastStableSignature = ""
                startCamera()
                updateUi()
                // Safety timeout: never count longer than 10s without asking.
                countJob = lifecycleScope.launch {
                    delay(MAX_COUNT_MS)
                    if (counting) finishCount()
                }
            }
        }
    }

    private fun startCamera() {
        val s = CameraSession(requireContext(), viewLifecycleOwner, binding.previewView)
        s.fps = Prefs.effectiveFps(requireContext())
        s.listener = object : CameraSession.FrameListener {
            override fun onFrame(square: Bitmap) {
                if (!counting) {
                    square.recycle()
                    return
                }
                val det = detector ?: run { square.recycle(); return }
                try {
                    val detections = det.detect(square, Prefs.confidence(requireContext()))
                    val visible = tracker.update(detections)
                    val sessionCounts = tracker.sessionCounts()
                    val sig = sessionCounts.toList().sortedBy { it.first }
                        .joinToString("|") { "${it.first}:${it.second}" }
                    val now = SystemClock.uptimeMillis()
                    if (sig == lastStableSignature) {
                        if (now - stableSinceMs >= STABLE_MS && sessionCounts.values.sum() > 0) {
                            activity?.runOnUiThread { finishCount() }
                        }
                    } else {
                        lastStableSignature = sig
                        stableSinceMs = now
                    }
                    activity?.runOnUiThread {
                        _binding?.let { b ->
                            b.overlay.setDetections(
                                visible,
                                square.width, square.height, 0, 0, square.width,
                            )
                            b.tvCountingTotal.text = sessionCounts.values.sum().toString()
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "count frame failed", e)
                } finally {
                    square.recycle()
                }
            }

            override fun onCameraError(message: String) {
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Camera error: $message", Toast.LENGTH_LONG).show()
                    stopEverything()
                    updateUi()
                }
            }
        }
        session = s
        s.start()
    }

    private fun finishCount() {
        if (!counting) return
        counting = false
        countJob?.cancel()
        countJob = null
        session?.stop()
        val counts = tracker.sessionCounts()
        updateUi()
        if (counts.values.sum() == 0) {
            Toast.makeText(requireContext(), R.string.photo_no_objects, Toast.LENGTH_LONG).show()
            return
        }
        showResultCard(counts)
    }

    private fun showResultCard(counts: Map<String, Int>) {
        val ctx = requireContext()
        val dlg = DialogCountcamResultBinding.inflate(layoutInflater)
        val rows = counts.toList().sortedByDescending { it.second }
        val rowViews = StringBuilder()
        for ((label, c) in rows) {
            rowViews.append("${label.replaceFirstChar { it.uppercase() }}: $c\n")
        }
        dlg.dlgTitle.text = getString(R.string.count_complete)
        dlg.dlgRows.text = rowViews.toString().trim()
        dlg.dlgTotal.text = getString(R.string.share_total_line, counts.values.sum())
        dlg.dlgDisclaimer.text = getString(R.string.accuracy_disclaimer)

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setView(dlg.root)
            .setCancelable(false)
            .create()

        dlg.btnSave.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val still = session?.captureStill()
                    repository.saveSession(
                        counts, still, "countcam",
                        LocationHelper.lastKnownLabel(ctx),
                    )
                    still?.recycle()
                    Toast.makeText(ctx, R.string.saved_toast, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(ctx, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            dialog.dismiss()
        }
        dlg.btnShare.setOnClickListener {
            dialog.dismiss()
            ResultCard.share(ctx, getString(R.string.countcam_share_title), counts)
        }
        dlg.btnExport.setOnClickListener {
            dialog.dismiss()
            exportResult(ctx, counts)
        }
        dlg.btnRecount.setOnClickListener {
            dialog.dismiss()
            beginCount()
        }
        dialog.show()
    }

    private fun exportResult(ctx: android.content.Context, counts: Map<String, Int>) {
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val pdf = PdfReport.generate(ctx, getString(R.string.countcam_share_title), counts)
                val xls = ExcelExporter.exportCounts(ctx, "countcam", counts)
                withContext(Dispatchers.Main) {
                    shareFiles(ctx, listOf(pdf, xls))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun shareFiles(ctx: android.content.Context, files: List<java.io.File>) {
        val uris = files.map {
            androidx.core.content.FileProvider.getUriForFile(
                ctx, "${ctx.packageName}.fileprovider", it
            )
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(
                android.content.Intent.EXTRA_STREAM, ArrayList(uris)
            )
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(android.content.Intent.createChooser(intent, getString(R.string.btn_export)))
    }

    private fun stopEverything() {
        counting = false
        countJob?.cancel()
        countJob = null
        session?.release()
        session = null
        try {
            detector?.close()
        } catch (e: Exception) {
            Log.w(TAG, "detector close failed", e)
        }
        detector = null
    }

    private fun updateUi() {
        val b = _binding ?: return
        b.btnSmartCount.text =
            getString(if (counting) R.string.countcam_stop else R.string.smart_count)
        b.btnDone.visibility = if (counting) View.VISIBLE else View.GONE
        b.countingPanel.visibility = if (counting) View.VISIBLE else View.GONE
        b.hintPanel.visibility = if (counting) View.GONE else View.VISIBLE
        if (!counting) {
            b.overlay.clear()
            b.tvCountingTotal.text = "0"
        }
    }

    companion object {
        private const val TAG = "CountCamFragment"

        /** Counts are "done" when nothing changes for this long. */
        private const val STABLE_MS = 2000L

        /** Hard cap for one Smart Count run. */
        private const val MAX_COUNT_MS = 10000L
    }
}
