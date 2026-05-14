package com.komica.reader.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.komica.reader.databinding.ItemThreadBinding
import com.komica.reader.model.KomicaThread

class ThreadListAdapter(
    private val onThreadClick: (KomicaThread) -> Unit
) : ListAdapter<KomicaThread, ThreadListAdapter.ThreadViewHolder>(DiffCallback) {

    fun SubmitThreads(threads: List<KomicaThread>) {
        submitList(threads)
    }

    fun GetThreadAt(position: Int): KomicaThread? {
        return currentList.getOrNull(position)
    }

    fun FindPositionByUrl(url: String): Int {
        return currentList.indexOfFirst { it.url == url }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThreadViewHolder {
        val binding = ItemThreadBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ThreadViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ThreadViewHolder, position: Int) {
        holder.Bind(getItem(position))
    }

    inner class ThreadViewHolder(
        private val binding: ItemThreadBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun Bind(thread: KomicaThread) {
            binding.threadTitle.text = thread.title
            binding.threadPreview.text = thread.contentPreview.ifBlank { "(無文字內容)" }
            binding.threadMeta.text = "${thread.replyCount} 回覆　${thread.lastReplyTime}"
            if (thread.imageUrl.isBlank()) {
                binding.threadThumbnail.visibility = View.GONE
                binding.thumbLabel.visibility = View.VISIBLE
                binding.thumbLabel.text = "文"
                Glide.with(binding.threadThumbnail).clear(binding.threadThumbnail)
            } else {
                binding.threadThumbnail.visibility = View.VISIBLE
                binding.thumbLabel.visibility = View.GONE
                Glide.with(binding.threadThumbnail)
                    .load(thread.imageUrl)
                    .fitCenter()
                    .into(binding.threadThumbnail)
            }
            binding.root.setOnClickListener { onThreadClick(thread) }
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
