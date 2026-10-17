package com.zenithai.countai.ui

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zenithai.countai.R
import com.zenithai.countai.databinding.FragmentSettingsBinding
import com.zenithai.countai.detection.CustomModelImporter
import com.zenithai.countai.di.ServiceLocator
import com.zenithai.countai.sync.SupabaseConfig
import com.zenithai.countai.sync.SyncManager
import com.zenithai.countai.util.LanguageManager
import com.zenithai.countai.util.PerformanceManager
import com.zenithai.countai.util.Prefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings: confidence, GPU/NNAPI, language (EN/UR/Roman), performance
 * preset, location toggle, privacy info, custom model import, premium info.
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private var pendingModelUri: Uri? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        binding.switchLocation.isChecked = granted
        Prefs.setLocationEnabled(requireContext(), granted)
        if (!granted) toast(R.string.location_denied)
    }

    private val modelPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            pendingModelUri = uri
            labelsPicker.launch("text/*")
        }
    }

    private val labelsPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        val modelUri = pendingModelUri
        pendingModelUri = null
        if (uri != null && modelUri != null) {
            importCustomModel(modelUri, uri)
        }
    }

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

        // FPS slider retired in v2 — performance presets control FPS now.
        binding.seekFps.visibility = View.GONE
        binding.tvFpsValue.visibility = View.GONE
        binding.tvFpsTitle.visibility = View.GONE
        binding.tvFpsHint.visibility = View.GONE

        binding.switchGpu.isChecked = Prefs.useGpu(requireContext())
        binding.switchGpu.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(Prefs.GPU, checked).apply()
        }

        binding.switchNnapi.isChecked = Prefs.useNnapi(requireContext())
        binding.switchNnapi.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(Prefs.NNAPI, checked).apply()
        }

        buildLanguageChips()
        buildPerfChips()

        binding.switchLocation.isChecked = Prefs.locationEnabled(requireContext())
        binding.switchLocation.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            } else {
                Prefs.setLocationEnabled(requireContext(), false)
            }
        }

        binding.btnImportModel.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.custom_model_import)
                .setMessage(R.string.custom_model_pick_help)
                .setPositiveButton(R.string.continue_text) { _, _ ->
                    modelPicker.launch("*/*")
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        binding.btnClearModel.setOnClickListener {
            CustomModelImporter.clear(requireContext())
            updateCustomModelStatus()
            toast(R.string.custom_model_cleared)
        }
        updateCustomModelStatus()
        maybeSuggestBatterySaver()
        setupCloudSection()
    }

    override fun onResume() {
        super.onResume()
        updateCloudSection()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun buildLanguageChips() {
        val group = binding.langChips
        group.removeAllViews()
        val current = LanguageManager.current(requireContext())
        for (code in listOf(
            LanguageManager.ENGLISH,
            LanguageManager.URDU,
            LanguageManager.ROMAN_URDU,
        )) {
            val chip = Chip(requireContext()).apply {
                text = LanguageManager.displayName(code)
                isCheckable = true
                isChecked = code == current
                tag = code
                id = View.generateViewId()
                setOnClickListener {
                    LanguageManager.setLanguage(requireContext(), code)
                    // Recreate so every string reloads in the new language.
                    activity?.recreate()
                }
            }
            group.addView(chip)
        }
    }

    private fun buildPerfChips() {
        val group = binding.perfChips
        group.removeAllViews()
        val current = Prefs.perfMode(requireContext())
        for (mode in listOf(
            PerformanceManager.MODE_BATTERY_SAVER,
            PerformanceManager.MODE_BALANCED,
            PerformanceManager.MODE_HIGH_ACCURACY,
        )) {
            val chip = Chip(requireContext()).apply {
                text = PerformanceManager.displayName(mode)
                isCheckable = true
                isChecked = mode == current
                tag = mode
                id = View.generateViewId()
                setOnClickListener {
                    Prefs.setPerfMode(requireContext(), mode)
                    val fps = PerformanceManager.fpsFor(mode)
                    toast(getString(R.string.perf_set, PerformanceManager.displayName(mode), fps))
                    buildPerfChips()
                }
            }
            group.addView(chip)
        }
    }

    private fun importCustomModel(modelUri: Uri, labelsUri: Uri) {
        val result = CustomModelImporter.import(requireContext(), modelUri, labelsUri)
        if (result != null) {
            toast(R.string.custom_model_imported)
        } else {
            toast(R.string.custom_model_failed)
        }
        updateCustomModelStatus()
    }

    private fun updateCustomModelStatus() {
        _binding?.tvCustomModelStatus?.text = if (
            CustomModelImporter.isCustomActive(requireContext())
        ) {
            getString(R.string.custom_model_active)
        } else {
            getString(R.string.custom_model_bundled)
        }
    }

    private fun maybeSuggestBatterySaver() {
        val ctx = requireContext()
        if (!Prefs.perfSuggested(ctx) && PerformanceManager.shouldSuggestBatterySaver(ctx)) {
            Prefs.setPerfSuggested(ctx)
            MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.perf_suggest_title)
                .setMessage(R.string.perf_suggest_text)
                .setPositiveButton(R.string.perf_battery) { _, _ ->
                    Prefs.setPerfMode(ctx, PerformanceManager.MODE_BATTERY_SAVER)
                    buildPerfChips()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun updateConfidenceLabel() {
        binding.tvConfidenceValue.text =
            String.format("%.2f", Prefs.confidence(requireContext()))
    }

    // ---- Cloud backup (v3) ----

    private fun setupCloudSection() {
        binding.switchCloudBackup.isChecked = Prefs.cloudBackup(requireContext())
        binding.switchCloudBackup.setOnCheckedChangeListener { _, checked ->
            val ctx = requireContext()
            if (checked && !ServiceLocator.authRepository(ctx).isSignedIn) {
                // Backup needs an account: send them to the Account screen.
                binding.switchCloudBackup.isChecked = false
                (activity as? MainActivity)?.showScreen(MainActivity.SCREEN_AUTH)
                toast(R.string.cloud_signin_first)
                return@setOnCheckedChangeListener
            }
            Prefs.setCloudBackup(ctx, checked)
            if (checked) SyncManager.schedulePeriodic(ctx)
            updateCloudSection()
        }
        binding.btnSyncNow.setOnClickListener {
            val ctx = requireContext()
            if (!ServiceLocator.authRepository(ctx).isSignedIn) {
                toast(R.string.cloud_signin_first)
                return@setOnClickListener
            }
            val started = SyncManager.syncNow(ctx)
            toast(if (started) R.string.cloud_sync_started else R.string.cloud_debounced)
            updateCloudSection()
        }
        binding.btnAccount.setOnClickListener {
            (activity as? MainActivity)?.showScreen(MainActivity.SCREEN_AUTH)
        }
        updateCloudSection()
    }

    private fun updateCloudSection() {
        val ctx = requireContext()
        val auth = ServiceLocator.authRepository(ctx)
        binding.tvCloudStatus.text = when {
            !SupabaseConfig.isConfigured -> getString(R.string.cloud_status_off)
            !auth.isSignedIn -> getString(R.string.cloud_status_out)
            else -> getString(R.string.cloud_status_on, auth.currentEmail ?: "")
        }
        val last = Prefs.lastSync(ctx)
        binding.tvLastSync.text = if (last == 0L) {
            getString(R.string.cloud_last_sync, getString(R.string.cloud_never))
        } else {
            val date = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(last))
            getString(R.string.cloud_last_sync, date)
        }
        // Keep the toggle honest: it only means something when signed in.
        if (!auth.isSignedIn && binding.switchCloudBackup.isChecked) {
            binding.switchCloudBackup.isChecked = false
            Prefs.setCloudBackup(ctx, false)
        }
    }

    private fun toast(resId: Int) {
        context?.let { Toast.makeText(it, resId, Toast.LENGTH_SHORT).show() }
    }

    private fun toast(text: String) {
        context?.let { Toast.makeText(it, text, Toast.LENGTH_SHORT).show() }
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
