package com.zenithai.countai.ui

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zenithai.countai.R
import com.zenithai.countai.data.SessionEntity
import com.zenithai.countai.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** History list. Tap = details, long-press = delete. */
class HistoryAdapter(
    private val onClick: (SessionEntity) -> Unit,
    private val onDelete: (SessionEntity) -> Unit,
) : ListAdapter<SessionEntity, HistoryAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemHistoryBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(s: SessionEntity) {
            val ctx = b.root.context
            b.tvDate.text = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
                .format(Date(s.createdAt))

            val counts = s.counts().toList().sortedByDescending { it.second }
            b.tvCounts.text = if (counts.isEmpty()) {
                "—"
            } else {
                counts.take(4).joinToString(" · ") { (label, c) -> "$label ×$c" } +
                    if (counts.size > 4) "  +${counts.size - 4} more" else ""
            }

            b.tvMode.text = ctx.getString(
                if (s.mode == "photo") R.string.mode_photo else R.string.mode_live
            )

            val thumbBitmap = s.imagePath?.let { path ->
                try {
                    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 4 })
                } catch (e: Exception) {
                    null
                }
            }
            if (thumbBitmap != null) b.thumb.setImageBitmap(thumbBitmap)
            else b.thumb.setImageDrawable(null)

            b.root.setOnClickListener { onClick(s) }
            b.root.setOnLongClickListener { onDelete(s); true }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SessionEntity>() {
            override fun areItemsTheSame(a: SessionEntity, b: SessionEntity): Boolean = a.id == b.id
            override fun areContentsTheSame(a: SessionEntity, b: SessionEntity): Boolean = a == b
        }
    }
}
