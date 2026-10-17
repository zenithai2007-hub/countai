package com.zenithai.countai.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zenithai.countai.R
import com.zenithai.countai.data.CountAiDatabase
import com.zenithai.countai.data.InventoryEntity
import com.zenithai.countai.databinding.FragmentInventoryBinding
import com.zenithai.countai.util.CommandParser
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Smart Inventory Mode: the business names its products ("Product A") and
 * maps each to a detectable object ("bottle"). The latest counted quantity
 * is stored per product so stock can be compared over time.
 */
class InventoryFragment : Fragment() {

    private var _binding: FragmentInventoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentInventoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val dao = CountAiDatabase.getInstance(requireContext()).inventoryDao()
        val adapter = InventoryAdapter(
            onCount = { item -> promptCount(item) },
            onDelete = { item ->
                lifecycleScope.launch {
                    // Tombstone for the cloud if this product was synced.
                    val userId = com.zenithai.countai.di.ServiceLocator
                        .authRepository(requireContext()).currentUserId
                    val db = CountAiDatabase.getInstance(requireContext())
                    if (item.remoteId != null && userId != null) {
                        db.pendingDeleteDao().insert(
                            com.zenithai.countai.data.PendingDelete(
                                tableName = "inventory_items",
                                remoteId = item.remoteId,
                                userId = userId,
                            )
                        )
                    }
                    db.inventoryDao().deleteById(item.id)
                }
            },
        )
        binding.inventoryList.layoutManager = LinearLayoutManager(requireContext())
        binding.inventoryList.adapter = adapter
        dao.getAll().observe(viewLifecycleOwner) { list ->
            adapter.submit(list)
            binding.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
        binding.btnAddProduct.setOnClickListener { showAddDialog() }
        binding.btnBack.setOnClickListener { (activity as? MainActivity)?.showTools() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showAddDialog() {
        val ctx = requireContext()
        val density = ctx.resources.displayMetrics.density
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (8 * density).toInt(), (20 * density).toInt(), 0)
        }
        val nameInput = EditText(ctx).apply {
            hint = getString(R.string.product_name_hint)
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
        }
        val labelSpinner = Spinner(ctx).apply {
            adapter = ArrayAdapter(
                ctx, android.R.layout.simple_spinner_dropdown_item,
                CommandParser.knownLabels(),
            )
        }
        val labelHint = TextView(ctx).apply {
            text = getString(R.string.maps_to_label)
            setTextColor(0xFFB0B0B0.toInt())
            textSize = 13f
            setPadding(0, (12 * density).toInt(), 0, (4 * density).toInt())
        }
        root.addView(nameInput)
        root.addView(labelHint)
        root.addView(labelSpinner)

        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.add_product)
            .setView(root)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(ctx, R.string.product_name_hint, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val label = labelSpinner.selectedItem as String
                lifecycleScope.launch {
                    CountAiDatabase.getInstance(ctx).inventoryDao()
                        .upsert(InventoryEntity(productName = name, label = label))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun promptCount(item: InventoryEntity) {
        val ctx = requireContext()
        val input = EditText(ctx).apply {
            hint = getString(R.string.enter_count_hint)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            setText(item.lastCount.takeIf { it > 0 }?.toString().orEmpty())
        }
        val pad = (20 * ctx.resources.displayMetrics.density).toInt()
        val root = LinearLayout(ctx).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(getString(R.string.count_for_product, item.productName))
            .setView(root)
            .setPositiveButton(R.string.save) { _, _ ->
                val count = input.text.toString().toIntOrNull()
                if (count == null || count < 0) {
                    Toast.makeText(ctx, R.string.invalid_count, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    CountAiDatabase.getInstance(ctx).inventoryDao()
                        .updateCount(item.id, count, System.currentTimeMillis())
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private class InventoryAdapter(
        private val onCount: (InventoryEntity) -> Unit,
        private val onDelete: (InventoryEntity) -> Unit,
    ) : RecyclerView.Adapter<InventoryAdapter.Holder>() {

        private var items: List<InventoryEntity> = emptyList()

        fun submit(list: List<InventoryEntity>) {
            items = list
            notifyDataSetChanged()
        }

        class Holder(val root: LinearLayout) : RecyclerView.ViewHolder(root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val ctx = parent.context
            val density = ctx.resources.displayMetrics.density
            val root = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xFF1E1E1E.toInt())
                val m = (6 * density).toInt()
                (layoutParams as? ViewGroup.MarginLayoutParams)?.setMargins(0, 0, 0, m)
                setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT,
                ).apply { setMargins(0, 0, 0, m) }
            }
            return Holder(root)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            val ctx = holder.root.context
            holder.root.removeAllViews()
            val name = TextView(ctx).apply {
                text = item.productName
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            val meta = TextView(ctx).apply {
                val date = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
                    .format(Date(item.updatedAt))
                text = "→ ${item.label}   •   $date"
                setTextColor(0xFFB0B0B0.toInt())
                textSize = 13f
            }
            val count = TextView(ctx).apply {
                text = "${item.lastCount}"
                setTextColor(0xFF00E676.toInt())
                textSize = 28f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            val buttons = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            val btnCount = com.google.android.material.button.MaterialButton(ctx).apply {
                text = ctx.getString(R.string.update_count)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { onCount(item) }
            }
            val btnDelete = com.google.android.material.button.MaterialButton(ctx).apply {
                text = ctx.getString(R.string.delete)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { onDelete(item) }
            }
            buttons.addView(btnCount)
            buttons.addView(btnDelete)
            holder.root.addView(name)
            holder.root.addView(meta)
            holder.root.addView(count)
            holder.root.addView(buttons)
        }

        override fun getItemCount(): Int = items.size
    }
}
