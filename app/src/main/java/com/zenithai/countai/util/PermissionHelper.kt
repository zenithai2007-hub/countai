package com.zenithai.countai.util

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zenithai.countai.R

/**
 * Camera permission flow with a proper rationale: the user is told WHY the
 * camera is needed (on-device counting, nothing uploaded) before the system
 * dialog appears, and is guided to Settings when they permanently deny it.
 */
object PermissionHelper {

    fun isCameraGranted(fragment: Fragment): Boolean =
        ContextCompat.checkSelfPermission(
            fragment.requireContext(), Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Shows a rationale dialog first when the user previously denied the
     * permission, then launches the system permission request. The result
     * arrives in the [launcher] callback — handle it with [handleResult].
     */
    fun requestCamera(
        fragment: Fragment,
        launcher: ActivityResultLauncher<String>,
        onGranted: () -> Unit,
    ) {
        if (isCameraGranted(fragment)) {
            onGranted()
            return
        }
        if (fragment.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle(R.string.camera_rationale_title)
                .setMessage(R.string.camera_rationale_message)
                .setPositiveButton(R.string.continue_text) { _, _ ->
                    launcher.launch(Manifest.permission.CAMERA)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    /** Shared result handling for the permission launcher. */
    fun handleResult(
        fragment: Fragment,
        granted: Boolean,
        onGranted: () -> Unit,
        onDenied: (permanently: Boolean) -> Unit,
    ) {
        if (granted) {
            onGranted()
        } else {
            val permanently =
                !fragment.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
            onDenied(permanently)
        }
    }

    fun showGoToSettingsDialog(fragment: Fragment) {
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.camera_rationale_title)
            .setMessage(R.string.go_to_settings_message)
            .setPositiveButton(R.string.open_settings) { _, _ -> openAppSettings(fragment) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun openAppSettings(fragment: Fragment) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", fragment.requireContext().packageName, null)
        )
        fragment.startActivity(intent)
    }
}
