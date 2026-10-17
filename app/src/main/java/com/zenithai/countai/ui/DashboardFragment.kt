package com.zenithai.countai.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.zenithai.countai.R
import com.zenithai.countai.data.CountAiDatabase
import com.zenithai.countai.data.SessionEntity
import com.zenithai.countai.databinding.FragmentDashboardBinding
import com.zenithai.countai.util.CsvExporter
import com.zenithai.countai.util.ExcelExporter
import com.zenithai.countai.util.PdfReport
import com.zenithai.countai.util.PremiumManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Business dashboard: inventory overview, counting history stats,
 * daily/weekly/monthly totals, a simple bar chart, and export buttons.
 * Premium feature (free in debug builds).
 */
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnBack.setOnClickListener { (activity as? MainActivity)?.showTools() }
        binding.premiumTag.visibility =
            if (PremiumManager.isUnlocked(PremiumManager.Feature.BUSINESS_DASHBOARD)) View.GONE else View.VISIBLE

        binding.btnExportCsv.setOnClickListener { exportAll("csv") }
        binding.btnExportPdf.setOnClickListener { exportAll("pdf") }
        binding.btnExportExcel.setOnClickListener { exportAll("xls") }

        loadStats()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadStats() {
        val ctx = requireContext()
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = CountAiDatabase.getInstance(ctx).historyDao()
            val sessions = dao.getAllNow()
            val inv = CountAiDatabase.getInstance(ctx).inventoryDao().getAllNow()

            val now = System.currentTimeMillis()
            val dayStart = startOfDay(0)
            val weekStart = startOfDay(7)
            val monthStart = startOfMonth()

            val todayTotal = sessions.filter { it.createdAt >= dayStart }.sumOf { it.totalCount() }
            val weekTotal = sessions.filter { it.createdAt >= weekStart }.sumOf { it.totalCount() }
            val monthTotal = sessions.filter { it.createdAt >= monthStart }.sumOf { it.totalCount() }

            val labelTotals = mutableMapOf<String, Int>()
            for (s in sessions) {
                for ((label, c) in s.counts()) {
                    labelTotals[label] = (labelTotals[label] ?: 0) + c
                }
            }
            val topLabels = labelTotals.toList().sortedByDescending { it.second }.take(8)
            val invTotal = inv.sumOf { it.lastCount }

            withContext(Dispatchers.Main) {
                _binding?.let { b ->
                    b.tvStatSessions.text = sessions.size.toString()
                    b.tvStatToday.text = todayTotal.toString()
                    b.tvStatWeek.text = weekTotal.toString()
                    b.tvStatMonth.text = monthTotal.toString()
                    b.tvStatInventory.text = getString(
                        R.string.dashboard_inventory_line, inv.size, invTotal
                    )
                    b.barChart.setData(topLabels)
                    b.tvChartEmpty.visibility =
                        if (topLabels.isEmpty()) View.VISIBLE else View.GONE
                    b.barChart.visibility =
                        if (topLabels.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    private fun startOfDay(daysAgo: Int): Long {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, -daysAgo)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun startOfMonth(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun exportAll(kind: String) {
        val ctx = requireContext()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val dao = CountAiDatabase.getInstance(ctx).historyDao()
                val sessions: List<SessionEntity> = dao.getAllNow()
                val file = when (kind) {
                    "pdf" -> {
                        val totals = mutableMapOf<String, Int>()
                        for (s in sessions) for ((l, c) in s.counts()) {
                            totals[l] = (totals[l] ?: 0) + c
                        }
                        PdfReport.generate(
                            ctx, getString(R.string.dashboard_report_title), totals,
                            listOf(getString(R.string.dashboard_sessions_line, sessions.size)),
                        )
                    }
                    "xls" -> ExcelExporter.export(ctx, sessions)
                    else -> CsvExporter.export(ctx, sessions)
                }
                val uri = FileProvider.getUriForFile(
                    ctx, "${ctx.packageName}.fileprovider", file
                )
                withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "*/*"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, getString(R.string.btn_export)))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
