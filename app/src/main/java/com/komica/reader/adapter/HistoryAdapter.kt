package com.komica.reader.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.komica.reader.databinding.ItemHistoryBinding
import com.komica.reader.model.KomicaThread

class HistoryAdapter(
    private val onClick: (KomicaThread) -> Unit,
    private val onLongClick: (KomicaThread) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {
    private val items = mutableListOf<KomicaThread>()

    fun SubmitItems(history: List<KomicaThread>) {
        items.clear()
        items.addAll(history)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        return HistoryViewHolder(ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) = holder.Bind(items[position])

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
}
