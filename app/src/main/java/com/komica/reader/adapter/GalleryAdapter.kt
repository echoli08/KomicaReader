package com.komica.reader.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.komica.reader.R
import com.komica.reader.model.Post

class GalleryAdapter(private val onImageClick: (Int) -> Unit) : 
    ListAdapter<Post, GalleryAdapter.ViewHolder>(PostDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gallery_image, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position, onImageClick)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val image: ImageView = itemView.findViewById(R.id.galleryImage)
        private val postNumber: TextView = itemView.findViewById(R.id.postNumber)

        fun bind(post: Post, position: Int, onImageClick: (Int) -> Unit) {
            postNumber.text = post.number.toString()
            
            // 繁體中文註解：thumbnailUrl 已為非空字串，直接使用
            val thumbUrl = post.thumbnailUrl
            
            if (thumbUrl.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(thumbUrl)
                    .centerCrop()
                    .into(image)
            } else {
                image.setImageDrawable(null)
            }

            itemView.setOnClickListener { onImageClick(position) }
        }
    }

    class PostDiffCallback : DiffUtil.ItemCallback<Post>() {
        override fun areItemsTheSame(oldItem: Post, newItem: Post): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Post, newItem: Post): Boolean {
            return oldItem == newItem
        }
    }
}
