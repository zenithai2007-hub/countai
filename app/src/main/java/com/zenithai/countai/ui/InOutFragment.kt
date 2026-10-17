package com.zenithai.countai.ui

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.zenithai.countai.R
import com.zenithai.countai.databinding.FragmentInoutBinding
import com.zenithai.countai.detection.Detector
import com.zenithai.countai.detection.LineCounter
import com.zenithai.countai.detection.ModelLoadException
import com.zenithai.countai.detection.ModelManager
import com.zenithai.countai.detection.ObjectTracker
import com.zenithai.countai.util.PermissionHelper
import com.zenithai.countai.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * IN / OUT counter: a draggable virtual line; every tracked object that
 * crosses it counts once — top→bottom = IN, bottom→top = OUT.
 * Useful for people, vehicles, boxes, inventory flow.
 */
class InOutFragment : Fragment() {

    private var _binding: FragmentInoutBinding? = null
    private val binding get() = _binding!!

    private var session: CameraSession? = null
    private var detector: Detector? = null
    private val tracker = ObjectTracker()
    private val lineCounter = LineCounter(0.5f)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCounting() else toast(R.string.permission_denied)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentInoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnToggle.setOnClickListener {
            if (session?.isRunning == true) stopAll() else
                PermissionHelper.requestCamera(this, permissionLauncher, onGranted = { startCounting() })
        }
        binding.btnResetLine.setOnClickListener {
            lineCounter.reset()
            tracker.reset()
            updateCounters()
        }
        binding.btnBack.setOnClickListener { (activity as? MainActivity)?.showTools() }
        binding.overlay.onLineMoved = { lineCounter.lineY = it }
        updateToggle()
    }

    override fun onPause() {
        super.onPause()
        stopAll()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopAll()
        _binding = null
    }

    private fun startCounting() {
        lifecycleScope.launch(Dispatchers.Default) {
            val det = try {
                ModelManager.createDetector(requireContext())
            } catch (e: ModelLoadException) {
                Log.e(TAG, "model missing", e); null
            } catch (e: Exception) {
                Log.e(TAG, "detector failed", e); null
            }
            withContext(Dispatchers.Main) {
                if (!isAdded || _binding == null) return@withContext
                if (det == null) {
                    toast(R.string.model_missing)
                    return@withContext
                }
                detector = det
                tracker.reset()
                lineCounter.reset()
                lineCounter.lineY = binding.overlay.lineYNorm
                val s = CameraSession(requireContext(), viewLifecycleOwner, binding.previewView)
                s.fps = Prefs.effectiveFps(requireContext())
                s.listener = object : CameraSession.FrameListener {
                    override fun onFrame(square: Bitmap) {
                        val det = detector ?: run { square.recycle(); return }
                        try {
                            val detections = det.detect(square, Prefs.confidence(requireContext()))
                            val visible = tracker.update(detections)
                            lineCounter.update(visible)
                            // Geometry for overlay mapping.
                            val size = square.width
                            activity?.runOnUiThread {
                                _binding?.let { b ->
                                    b.overlay.setDetections(visible, size, size, 0, 0, size)
                                    updateCounters()
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "inout frame failed", e)
                        } finally {
                            square.recycle()
                        }
                    }

                    override fun onCameraError(message: String) {
                        activity?.runOnUiThread {
                            toast("Camera error: $message")
                            stopAll()
                        }
                    }
                }
                session = s
                s.start()
                updateToggle()
            }
        }
    }

    private fun stopAll() {
        session?.release()
        session = null
        try {
            detector?.close()
        } catch (e: Exception) {
            Log.w(TAG, "detector close failed", e)
        }
        detector = null
        _binding?.let {
            it.overlay.clear()
            updateToggle()
        }
    }

    private fun updateCounters() {
        val b = _binding ?: return
        b.tvIn.text = lineCounter.inCount.toString()
        b.tvOut.text = lineCounter.outCount.toString()
        b.tvNet.text = lineCounter.netCount.toString()
    }

    private fun updateToggle() {
        _binding?.btnToggle?.text = getString(
            if (session?.isRunning == true) R.string.stop_camera else R.string.start_camera
        )
    }

    private fun toast(resId: Int) {
        context?.let { Toast.makeText(it, resId, Toast.LENGTH_SHORT).show() }
    }

    private fun toast(text: String) {
        context?.let { Toast.makeText(it, text, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        private const val TAG = "InOutFragment"
    }
}
