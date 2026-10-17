package com.zenithai.countai.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.zenithai.countai.R
import com.zenithai.countai.databinding.FragmentToolsBinding

/**
 * Hub of advanced modes: CountCam, IN/OUT line, Video, Photo compare,
 * Inventory, Dashboard. Each card opens its screen via MainActivity.
 */
class ToolsFragment : Fragment() {

    private var _binding: FragmentToolsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentToolsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val tools = listOf(
            Tool(R.string.tool_countcam, R.string.tool_countcam_desc, null) {
                (activity as? MainActivity)?.showScreen(MainActivity.SCREEN_COUNTCAM)
            },
            Tool(R.string.tool_inout, R.string.tool_inout_desc, null) {
                (activity as? MainActivity)?.showScreen(MainActivity.SCREEN_INOUT)
            },
            Tool(R.string.tool_video, R.string.tool_video_desc, com.zenithai.countai.util.PremiumManager.Feature.VIDEO_COUNT) {
                (activity as? MainActivity)?.showScreen(MainActivity.SCREEN_VIDEO)
            },
            Tool(R.string.tool_diff, R.string.tool_diff_desc, com.zenithai.countai.util.PremiumManager.Feature.DIFFERENCE_DETECTOR) {
                (activity as? MainActivity)?.showScreen(MainActivity.SCREEN_DIFF)
            },
            Tool(R.string.tool_inventory, R.string.tool_inventory_desc, null) {
                (activity as? MainActivity)?.showScreen(MainActivity.SCREEN_INVENTORY)
            },
            Tool(R.string.tool_dashboard, R.string.tool_dashboard_desc, com.zenithai.countai.util.PremiumManager.Feature.BUSINESS_DASHBOARD) {
                (activity as? MainActivity)?.showScreen(MainActivity.SCREEN_DASHBOARD)
            },
        )
        for (t in tools) binding.toolsList.addView(makeCard(t))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class Tool(
        val title: Int,
        val desc: Int,
        val premiumFeature: com.zenithai.countai.util.PremiumManager.Feature?,
        val open: () -> Unit,
    )

    private fun makeCard(tool: Tool): View {
        val ctx = requireContext()
        val density = ctx.resources.displayMetrics.density
        val card = MaterialCardView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins(0, 0, 0, (12 * density).toInt())
            }
            setCardBackgroundColor(0xFF1E1E1E.toInt())
            radius = 16 * density
            isClickable = true
            isFocusable = true
            setOnClickListener { tool.open() }
        }
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }
        val titleRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val title = TextView(ctx).apply {
            text = getString(tool.title)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleRow.addView(title)
        tool.premiumFeature?.let {
            if (!com.zenithai.countai.util.PremiumManager.isUnlocked(it)) {
                val tag = TextView(ctx).apply {
                    text = getString(R.string.premium_tag)
                    setTextColor(0xFF000000.toInt())
                    textSize = 11f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setBackgroundColor(0xFFFFC107.toInt())
                    val p = (6 * density).toInt()
                    setPadding(p * 2, p, p * 2, p)
                }
                titleRow.addView(tag)
            }
        }
        val desc = TextView(ctx).apply {
            text = getString(tool.desc)
            setTextColor(0xFFB0B0B0.toInt())
            textSize = 14f
            setPadding(0, (6 * density).toInt(), 0, 0)
        }
        inner.addView(titleRow)
        inner.addView(desc)
        card.addView(inner)
        return card
    }
}
