package com.komica.reader.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.komica.reader.R
import com.komica.reader.model.Thread

class ThreadAdapter(
    threads: List<Thread>,
    private val onThreadClickListener: OnThreadClickListener?
) : RecyclerView.Adapter<ThreadAdapter.ThreadViewHolder>() {

    private val threads: MutableList<Thread> = ArrayList(threads)

    interface OnThreadClickListener {
        fun onThreadClick(thread: Thread)
        fun onShareClick(thread: Thread)
    }

    fun updateThreads(newThreads: List<Thread>) {
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = threads.size

            override fun getNewListSize(): Int = newThreads.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return threads[oldItemPosition].url == newThreads[newItemPosition].url
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = threads[oldItemPosition]
                val newItem = newThreads[newItemPosition]
                return oldItem.postNumber == newItem.postNumber &&
                    oldItem.title == newItem.title &&
                    oldItem.replyCount == newItem.replyCount &&
                    oldItem.lastReplyTime == newItem.lastReplyTime
            }
        })

        threads.clear()
        threads.addAll(newThreads)
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThreadViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_thread, parent, false)
        return ThreadViewHolder(view)
    }

    override fun onBindViewHolder(holder: ThreadViewHolder, position: Int) {
        holder.bind(threads[position])
    }

    override fun getItemCount(): Int = threads.size

    inner class ThreadViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val threadPostNumber: TextView = itemView.findViewById(R.id.threadPostNumber)
        private val threadTitle: TextView = itemView.findViewById(R.id.threadTitle)
        private val threadAuthor: TextView = itemView.findViewById(R.id.threadAuthor)
        private val contentPreview: TextView = itemView.findViewById(R.id.contentPreview)
        private val lastReplyTime: TextView = itemView.findViewById(R.id.lastReplyTime)
        private val replyCount: TextView = itemView.findViewById(R.id.replyCount)
        private val shareButton: View = itemView.findViewById(R.id.shareButton)

        private val titleTextSize: Float

        init {
            val prefs = itemView.context.getSharedPreferences("KomicaReader", android.content.Context.MODE_PRIVATE)
            titleTextSize = prefs.getFloat("theme_font_size", 16f)
        }

        fun bind(thread: Thread) {
            threadTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, titleTextSize)

            threadPostNumber.text = "No. ${thread.postNumber}"
            threadTitle.text = thread.title
            threadAuthor.text = thread.author
            replyCount.text = "回覆: ${thread.replyCount}"

            if (thread.contentPreview.isNotBlank()) {
                contentPreview.text = thread.contentPreview
                contentPreview.visibility = View.VISIBLE
            } else {
                contentPreview.visibility = View.GONE
            }

            if (thread.lastReplyTime.isNotBlank()) {
                lastReplyTime.text = thread.lastReplyTime
                lastReplyTime.visibility = View.VISIBLE
            } else {
                lastReplyTime.visibility = View.GONE
            }

            itemView.setOnClickListener {
                onThreadClickListener?.onThreadClick(thread)
            }

            shareButton.setOnClickListener {
                onThreadClickListener?.onShareClick(thread)
            }
        }
    }
}
