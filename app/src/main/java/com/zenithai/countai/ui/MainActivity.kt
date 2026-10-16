package com.zenithai.countai.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.zenithai.countai.R
import com.zenithai.countai.databinding.ActivityMainBinding

/**
 * Single-activity host. The camera UI is always dark (forced night mode)
 * for contrast against the preview.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val cameraFragment = CameraFragment()
    private val historyFragment = HistoryFragment()
    private val settingsFragment = SettingsFragment()
    private var active: Fragment = cameraFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportFragmentManager.beginTransaction()
            .add(R.id.nav_host, settingsFragment, TAG_SETTINGS).hide(settingsFragment)
            .add(R.id.nav_host, historyFragment, TAG_HISTORY).hide(historyFragment)
            .add(R.id.nav_host, cameraFragment, TAG_CAMERA)
            .commit()

        binding.bottomNav.setOnItemSelectedListener { item ->
            val target = when (item.itemId) {
                R.id.nav_history -> historyFragment
                R.id.nav_settings -> settingsFragment
                else -> cameraFragment
            }
            if (target != active) {
                supportFragmentManager.beginTransaction()
                    .hide(active)
                    .show(target)
                    .commit()
                active = target
            }
            true
        }
    }

    companion object {
        private const val TAG_CAMERA = "camera"
        private const val TAG_HISTORY = "history"
        private const val TAG_SETTINGS = "settings"
    }
}
