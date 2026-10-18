package com.zenithai.countai.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.zenithai.countai.R
import com.zenithai.countai.databinding.ActivityMainBinding
import com.zenithai.countai.util.LanguageManager

/**
 * Single-activity host. Light theme (white + yellow) forced for a bright,
 * friendly look.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val cameraFragment = CameraFragment()
    private val toolsFragment = ToolsFragment()
    private val historyFragment = HistoryFragment()
    private val settingsFragment = SettingsFragment()
    private val countCamFragment = CountCamFragment()
    private val inOutFragment = InOutFragment()
    private val videoFragment = VideoCountFragment()
    private val inventoryFragment = InventoryFragment()
    private val diffFragment = DifferenceFragment()
    private val dashboardFragment = DashboardFragment()
    private val authFragment = AuthFragment()
    private var active: Fragment = cameraFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        LanguageManager.applyFromPrefs(this)
        // Light theme: white backgrounds, yellow brand accents.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fm = supportFragmentManager
        fm.beginTransaction()
            .add(R.id.nav_host, settingsFragment, SCREEN_SETTINGS).hide(settingsFragment)
            .add(R.id.nav_host, historyFragment, SCREEN_HISTORY).hide(historyFragment)
            .add(R.id.nav_host, toolsFragment, SCREEN_TOOLS).hide(toolsFragment)
            .add(R.id.nav_host, countCamFragment, SCREEN_COUNTCAM).hide(countCamFragment)
            .add(R.id.nav_host, inOutFragment, SCREEN_INOUT).hide(inOutFragment)
            .add(R.id.nav_host, videoFragment, SCREEN_VIDEO).hide(videoFragment)
            .add(R.id.nav_host, inventoryFragment, SCREEN_INVENTORY).hide(inventoryFragment)
            .add(R.id.nav_host, diffFragment, SCREEN_DIFF).hide(diffFragment)
            .add(R.id.nav_host, dashboardFragment, SCREEN_DASHBOARD).hide(dashboardFragment)
            .add(R.id.nav_host, authFragment, SCREEN_AUTH).hide(authFragment)
            .add(R.id.nav_host, cameraFragment, SCREEN_CAMERA)
            .commit()

        binding.bottomNav.setOnItemSelectedListener { item ->
            val target = when (item.itemId) {
                R.id.nav_tools -> toolsFragment
                R.id.nav_history -> historyFragment
                R.id.nav_settings -> settingsFragment
                else -> cameraFragment
            }
            showFragment(target)
            true
        }
    }

    /** Opens any screen by tag (used by the Tools hub). */
    fun showScreen(tag: String) {
        val target = supportFragmentManager.findFragmentByTag(tag) ?: return
        showFragment(target)
    }

    fun showTools() = showScreen(SCREEN_TOOLS)

    private fun showFragment(target: Fragment) {
        if (target == active) return
        supportFragmentManager.beginTransaction()
            .hide(active)
            .show(target)
            .commit()
        active = target
        // Keep the bottom nav in sync when arriving from the Tools hub.
        val itemId = when (target) {
            toolsFragment, countCamFragment, inOutFragment, videoFragment,
            inventoryFragment, diffFragment, dashboardFragment, authFragment -> R.id.nav_tools
            historyFragment -> R.id.nav_history
            settingsFragment -> R.id.nav_settings
            else -> R.id.nav_camera
        }
        if (binding.bottomNav.selectedItemId != itemId) {
            binding.bottomNav.selectedItemId = itemId
        }
    }

    companion object {
        const val SCREEN_CAMERA = "camera"
        const val SCREEN_TOOLS = "tools"
        const val SCREEN_HISTORY = "history"
        const val SCREEN_SETTINGS = "settings"
        const val SCREEN_COUNTCAM = "countcam"
        const val SCREEN_INOUT = "inout"
        const val SCREEN_VIDEO = "video"
        const val SCREEN_INVENTORY = "inventory"
        const val SCREEN_DIFF = "diff"
        const val SCREEN_DASHBOARD = "dashboard"
        const val SCREEN_AUTH = "auth"
    }
}
