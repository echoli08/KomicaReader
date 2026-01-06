package com.komica.reader.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.komica.reader.R
import com.komica.reader.db.HistoryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(private val onItemClick: (HistoryEntry) -> Unit) : 
    ListAdapter<HistoryEntry, HistoryAdapter.ViewHolder>(HistoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_thread, parent, false) // Reuse item_thread layout
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = getItem(position)
        holder.bind(entry, onItemClick)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.threadTitle)
        private val author: TextView = itemView.findViewById(R.id.threadAuthor)
        private val replyCount: TextView = itemView.findViewById(R.id.replyCount)
        private val shareButton: View = itemView.findViewById(R.id.shareButton)
        private val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

        fun bind(entry: HistoryEntry, onItemClick: (HistoryEntry) -> Unit) {
            title.text = entry.title
            
            // Reusing author field for timestamp
            author.text = "瀏覽時間: " + dateFormat.format(Date(entry.updatedAt))
            
            // Hide reply count as we don't store it
            replyCount.visibility = View.GONE
            
            // Hide share button in history list for now
            shareButton.visibility = View.GONE

            itemView.setOnClickListener { onItemClick(entry) }
        }
    }

    class HistoryDiffCallback : DiffUtil.ItemCallback<HistoryEntry>() {
        override fun areItemsTheSame(oldItem: HistoryEntry, newItem: HistoryEntry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: HistoryEntry, newItem: HistoryEntry): Boolean {
            return oldItem == newItem
        }
    }
}