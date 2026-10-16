package com.zenithai.countai.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import com.zenithai.countai.databinding.FragmentSettingsBinding
import com.zenithai.countai.util.Prefs

/**
 * Settings. Confidence + FPS apply live; GPU/NNAPI take effect the next time
 * the camera screen resumes (the detector is rebuilt there).
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prefs = requireContext().getSharedPreferences(Prefs.FILE, 0)

        // Confidence: SeekBar 0..50  ->  0.30..0.80
        binding.seekConfidence.progress =
            ((Prefs.confidence(requireContext()) * 100).toInt() - 30).coerceIn(0, 50)
        updateConfidenceLabel()
        binding.seekConfidence.setOnSeekBarChangeListener(simpleListener {
            prefs.edit().putFloat(Prefs.CONFIDENCE, (30 + it) / 100f).apply()
            updateConfidenceLabel()
        })

        // FPS: SeekBar 0..10  ->  5..15
        binding.seekFps.progress = Prefs.fps(requireContext()) - 5
        updateFpsLabel()
        binding.seekFps.setOnSeekBarChangeListener(simpleListener {
            prefs.edit().putInt(Prefs.FPS, 5 + it).apply()
            updateFpsLabel()
        })

        binding.switchGpu.isChecked = Prefs.useGpu(requireContext())
        binding.switchGpu.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(Prefs.GPU, checked).apply()
        }

        binding.switchNnapi.isChecked = Prefs.useNnapi(requireContext())
        binding.switchNnapi.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(Prefs.NNAPI, checked).apply()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateConfidenceLabel() {
        binding.tvConfidenceValue.text =
            String.format("%.2f", Prefs.confidence(requireContext()))
    }

    private fun updateFpsLabel() {
        binding.tvFpsValue.text = "${Prefs.fps(requireContext())} FPS"
    }

    private fun simpleListener(onChange: (Int) -> Unit) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) onChange(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
}
