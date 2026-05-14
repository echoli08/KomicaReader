package com.komica.reader.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.komica.reader.databinding.ItemHistoryBinding
import com.komica.reader.model.KomicaThread

class HistoryAdapter(
    private val onClick: (KomicaThread) -> Unit,
    private val onLongClick: (KomicaThread) -> Unit
) : ListAdapter<KomicaThread, HistoryAdapter.HistoryViewHolder>(DiffCallback) {

    fun SubmitItems(history: List<KomicaThread>) {
        submitList(history)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        return HistoryViewHolder(ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) = holder.Bind(getItem(position))

    inner class HistoryViewHolder(private val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun Bind(thread: KomicaThread) {
            binding.historyTitle.text = thread.title
            binding.historyUrl.text = thread.url
            binding.root.setOnClickListener { onClick(thread) }
            binding.root.setOnLongClickListener {
                onLongClick(thread)
                true
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<KomicaThread>() {
            override fun areItemsTheSame(oldItem: KomicaThread, newItem: KomicaThread): Boolean {
                return oldItem.url == newItem.url
            }

            override fun areContentsTheSame(oldItem: KomicaThread, newItem: KomicaThread): Boolean {
                return oldItem == newItem
            }
        }
    }
}
