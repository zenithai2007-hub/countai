package com.zenithai.countai.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.zenithai.countai.R
import com.zenithai.countai.databinding.FragmentDifferenceBinding
import com.zenithai.countai.detection.Detector
import com.zenithai.countai.detection.ModelManager
import com.zenithai.countai.util.DifferenceUtil
import com.zenithai.countai.util.FrameConverter
import com.zenithai.countai.util.Prefs
import com.zenithai.countai.util.PremiumManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * Difference detector (signature feature): count objects in a "before"
 * photo and an "after" photo, then report Previous / Current / Difference
 * per type with changed rows highlighted.
 */
class DifferenceFragment : Fragment() {

    private var _binding: FragmentDifferenceBinding? = null
    private val binding get() = _binding!!

    private var beforeUri: Uri? = null
    private var afterUri: Uri? = null

    private val pickBefore = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            beforeUri = uri
            binding.imgBefore.setImageURI(uri)
            maybeCompare()
        }
    }
    private val pickAfter = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            afterUri = uri
            binding.imgAfter.setImageURI(uri)
            maybeCompare()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDifferenceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnPickBefore.setOnClickListener { checkPremium { pickBefore.launch("image/*") } }
        binding.btnPickAfter.setOnClickListener { checkPremium { pickAfter.launch("image/*") } }
        binding.btnBack.setOnClickListener { (activity as? MainActivity)?.showTools() }
        binding.premiumTag.visibility =
            if (PremiumManager.isUnlocked(PremiumManager.Feature.DIFFERENCE_DETECTOR)) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun checkPremium(action: () -> Unit) {
        if (!PremiumManager.isUnlocked(PremiumManager.Feature.DIFFERENCE_DETECTOR)) {
            Toast.makeText(requireContext(), R.string.premium_locked, Toast.LENGTH_LONG).show()
            return
        }
        action()
    }

    private fun maybeCompare() {
        val b = beforeUri ?: return
        val a = afterUri ?: return
        binding.tvDiffStatus.text = getString(R.string.detecting)
        binding.diffRows.removeAllViews()
        lifecycleScope.launch(Dispatchers.Default) {
            var detector: Detector? = null
            try {
                detector = ModelManager.createDetector(requireContext())
                val before = countPhoto(detector, b)
                val after = countPhoto(detector, a)
                val rows = DifferenceUtil.compare(before, after)
                withContext(Dispatchers.Main) { showRows(rows) }
            } catch (e: Exception) {
                Log.e(TAG, "compare failed", e)
                withContext(Dispatchers.Main) {
                    _binding?.tvDiffStatus?.text = "Failed: ${e.message}"
                }
            } finally {
                try {
                    detector?.close()
                } catch (e: Exception) {
                    Log.w(TAG, "detector close failed", e)
                }
            }
        }
    }

    private fun countPhoto(detector: Detector, uri: Uri): Map<String, Int> {
        val original = requireContext().contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return emptyMap()
        // Same square center-crop the live pipeline uses.
        val side = min(original.width, original.height)
        val square = Bitmap.createBitmap(
            original,
            (original.width - side) / 2, (original.height - side) / 2, side, side,
        )
        original.recycle()
        val input = Bitmap.createScaledBitmap(square, detector.inputSize, detector.inputSize, true)
        val detections = detector.detect(input, Prefs.confidence(requireContext()))
        input.recycle()
        square.recycle()
        return detections.groupingBy { it.label }.eachCount()
    }

    private fun showRows(rows: List<com.zenithai.countai.util.DiffRow>) {
        val b = _binding ?: return
        b.diffRows.removeAllViews()
        if (rows.isEmpty()) {
            b.tvDiffStatus.text = getString(R.string.photo_no_objects)
            return
        }
        val changed = rows.count { it.changed }
        b.tvDiffStatus.text = if (changed == 0) getString(R.string.diff_no_change)
        else getString(R.string.diff_changed_count, changed)
        val ctx = requireContext()
        val density = ctx.resources.displayMetrics.density
        for (row in rows) {
            val line = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
                if (row.changed) setBackgroundColor(0xFF3A2E00.toInt())
            }
            val name = TextView(ctx).apply {
                text = row.label.replaceFirstChar { it.uppercase() }
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f)
            }
            fun cell(text: String, color: Int): TextView = TextView(ctx).apply {
                this.text = text
                setTextColor(color)
                textSize = 16f
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val diffColor = when {
                row.diff > 0 -> 0xFF00E676.toInt()
                row.diff < 0 -> 0xFFFF5252.toInt()
                else -> 0xFFB0B0B0.toInt()
            }
            val diffText = when {
                row.diff > 0 -> "+${row.diff}"
                else -> "${row.diff}"
            }
            line.addView(name)
            line.addView(cell("${row.before}", 0xFFB0B0B0.toInt()))
            line.addView(cell("${row.after}", 0xFFFFFFFF.toInt()))
            line.addView(cell(diffText, diffColor))
            b.diffRows.addView(line)
        }
    }

    companion object {
        private const val TAG = "DifferenceFragment"
    }
}
