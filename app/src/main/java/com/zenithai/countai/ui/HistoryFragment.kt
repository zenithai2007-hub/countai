package com.zenithai.countai.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zenithai.countai.R
import com.zenithai.countai.data.SessionEntity
import com.zenithai.countai.data.SessionRepository
import com.zenithai.countai.databinding.FragmentHistoryBinding
import com.zenithai.countai.util.SmartSearch
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Saved counting sessions: list, detail, share, delete, CSV export. */
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: SessionRepository
    private lateinit var adapter: HistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = SessionRepository(requireContext())
        adapter = HistoryAdapter(
            onClick = { showDetail(it) },
            onDelete = { confirmDelete(it) },
        )
        binding.historyList.layoutManager = LinearLayoutManager(requireContext())
        binding.historyList.adapter = adapter

        repository.sessions().observe(viewLifecycleOwner) { list ->
            fullList = list
            applySearch()
        }

        binding.btnExportCsv.setOnClickListener { exportCsv() }
        binding.btnSearch.setOnClickListener { applySearch() }
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                applySearch()
                true
            } else false
        }
    }

    private var fullList: List<SessionEntity> = emptyList()

    /** Natural-language search over history ("last week box counts"). */
    private fun applySearch() {
        val b = _binding ?: return
        val query = b.etSearch.text.toString().trim()
        if (query.isEmpty()) {
            b.tvSearchResult.visibility = View.GONE
            adapter.submitList(fullList)
            updateEmpty(fullList.isEmpty())
            return
        }
        val req = SmartSearch.parse(query)
        if (req.wantMax) {
            val (best, count) = SmartSearch.highest(fullList, req.label)
            if (best == null || count == 0) {
                b.tvSearchResult.visibility = View.VISIBLE
                b.tvSearchResult.text = getString(R.string.search_no_results)
                adapter.submitList(emptyList())
                updateEmpty(true)
            } else {
                val label = req.label ?: getString(R.string.chip_all)
                b.tvSearchResult.visibility = View.VISIBLE
                b.tvSearchResult.text = getString(R.string.search_highest_result, label, count)
                adapter.submitList(listOf(best))
                updateEmpty(false)
            }
            return
        }
        val filtered = SmartSearch.filter(fullList, req)
        if (filtered.isEmpty()) {
            b.tvSearchResult.visibility = View.VISIBLE
            b.tvSearchResult.text = getString(R.string.search_no_results)
        } else {
            b.tvSearchResult.visibility = View.VISIBLE
            b.tvSearchResult.text = getString(R.string.search_found, filtered.size)
        }
        adapter.submitList(filtered)
        updateEmpty(filtered.isEmpty())
    }

    private fun updateEmpty(empty: Boolean) {
        _binding?.let {
            it.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
            it.historyList.visibility = if (empty) View.GONE else View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showDetail(s: SessionEntity) {
        val ctx = requireContext()
        val density = ctx.resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        val imageView = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (220 * density).toInt()
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFF000000.toInt())
        }
        s.imagePath?.let { path ->
            try {
                BitmapFactory.decodeFile(path)?.let { imageView.setImageBitmap(it) }
            } catch (e: Exception) {
                // no thumbnail — counts still shown
            }
        }
        val countsView = TextView(ctx).apply {
            val lines = s.counts().toList()
                .sortedByDescending { it.second }
                .joinToString("\n") { (label, c) -> "$label: $c" }
                .ifEmpty { "—" }
            text = "$lines\n\nTotal objects: ${s.totalCount()}"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            setPadding(0, pad / 2, 0, 0)
        }
        root.addView(imageView)
        root.addView(countsView)

        val date = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
            .format(Date(s.createdAt))
        MaterialAlertDialogBuilder(ctx)
            .setTitle(date)
            .setView(root)
            .setPositiveButton(R.string.share) { _, _ -> shareSession(s) }
            .setNeutralButton(R.string.delete) { _, _ -> confirmDelete(s) }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun shareSession(s: SessionEntity) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject))
            putExtra(Intent.EXTRA_TEXT, repository.shareText(s))
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    private fun confirmDelete(s: SessionEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_confirm_title)
            .setMessage(R.string.delete_confirm_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    repository.deleteSession(s)
                    Toast.makeText(requireContext(), R.string.deleted_toast, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun exportCsv() {
        lifecycleScope.launch {
            try {
                val file = repository.exportAllCsv()
                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    file,
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, getString(R.string.export_csv)))
                Toast.makeText(requireContext(), R.string.exported_toast, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
