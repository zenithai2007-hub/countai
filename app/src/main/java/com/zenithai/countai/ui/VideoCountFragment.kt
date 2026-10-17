package com.zenithai.countai.ui

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.zenithai.countai.R
import com.zenithai.countai.data.SessionRepository
import com.zenithai.countai.databinding.FragmentVideoBinding
import com.zenithai.countai.detection.Detector
import com.zenithai.countai.detection.ModelManager
import com.zenithai.countai.detection.VideoCounter
import com.zenithai.countai.util.LocationHelper
import com.zenithai.countai.util.Prefs
import com.zenithai.countai.util.PremiumManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Video counting: pick a video from the gallery, CountAI samples frames and
 * reports unique objects across the whole video (Premium feature — free in
 * debug builds).
 */
class VideoCountFragment : Fragment() {

    private var _binding: FragmentVideoBinding? = null
    private val binding get() = _binding!!

    private var job: Job? = null
    private lateinit var repository: SessionRepository

    private val videoPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) analyzeVideo(uri) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentVideoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = SessionRepository(requireContext())
        binding.btnPickVideo.setOnClickListener {
            if (!PremiumManager.isUnlocked(PremiumManager.Feature.VIDEO_COUNT)) {
                Toast.makeText(requireContext(), R.string.premium_locked, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            videoPicker.launch("video/*")
        }
        binding.btnBack.setOnClickListener { (activity as? MainActivity)?.showTools() }
        binding.btnSaveVideo.setOnClickListener { saveLastResult() }
        updatePremiumTag()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        job?.cancel()
        _binding = null
    }

    private var lastCounts: Map<String, Int>? = null

    private fun analyzeVideo(uri: Uri) {
        job?.cancel()
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.progress = 0
        binding.tvVideoStatus.text = getString(R.string.analyzing_video)
        binding.resultRows.removeAllViews()
        binding.btnSaveVideo.visibility = View.GONE
        lastCounts = null

        job = lifecycleScope.launch {
            var detector: Detector? = null
            try {
                detector = ModelManager.createDetector(requireContext())
                val counts = VideoCounter.count(
                    requireContext(), uri, detector,
                    Prefs.confidence(requireContext()),
                    onProgress = { p ->
                        binding.progressBar.post {
                            _binding?.let {
                                it.progressBar.progress = p
                                it.tvVideoStatus.text = getString(R.string.analyzing_video_pct, p)
                            }
                        }
                    },
                )
                lastCounts = counts
                showResults(counts)
            } catch (e: Exception) {
                Log.e(TAG, "video count failed", e)
                _binding?.tvVideoStatus?.text = "Failed: ${e.message}"
            } finally {
                try {
                    detector?.close()
                } catch (e: Exception) {
                    Log.w(TAG, "detector close failed", e)
                }
                _binding?.progressBar?.visibility = View.GONE
            }
        }
    }

    private fun showResults(counts: Map<String, Int>) {
        val b = _binding ?: return
        b.resultRows.removeAllViews()
        if (counts.values.sum() == 0) {
            b.tvVideoStatus.text = getString(R.string.video_no_objects)
            return
        }
        b.tvVideoStatus.text = getString(R.string.video_done)
        val density = resources.displayMetrics.density
        for ((label, c) in counts.toList().sortedByDescending { it.second }) {
            val tv = TextView(requireContext()).apply {
                text = "${label.replaceFirstChar { it.uppercase() }}: $c"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 18f
                setPadding(0, (6 * density).toInt(), 0, (6 * density).toInt())
            }
            b.resultRows.addView(tv)
        }
        val total = TextView(requireContext()).apply {
            text = getString(R.string.share_total_line, counts.values.sum())
            setTextColor(0xFF00E676.toInt())
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        b.resultRows.addView(total)
        b.btnSaveVideo.visibility = View.VISIBLE
    }

    private fun saveLastResult() {
        val counts = lastCounts ?: return
        lifecycleScope.launch {
            try {
                repository.saveSession(
                    counts, null, "video",
                    LocationHelper.lastKnownLabel(requireContext()),
                )
                Toast.makeText(requireContext(), R.string.saved_toast, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updatePremiumTag() {
        _binding?.premiumTag?.visibility =
            if (PremiumManager.isUnlocked(PremiumManager.Feature.VIDEO_COUNT)) View.GONE else View.VISIBLE
    }

    companion object {
        private const val TAG = "VideoCountFragment"
    }
}
