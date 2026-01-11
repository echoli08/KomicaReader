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

class GalleryAdapter(
    private val onImageClick: (Int) -> Unit,
    private val onImageLongClick: (String) -> Unit
) : ListAdapter<Post, GalleryAdapter.ViewHolder>(PostDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gallery_image, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position, onImageClick, onImageLongClick)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val image: ImageView = itemView.findViewById(R.id.galleryImage)
        private val postNumber: TextView = itemView.findViewById(R.id.postNumber)

        fun bind(
            post: Post,
            position: Int,
            onImageClick: (Int) -> Unit,
            onImageLongClick: (String) -> Unit
        ) {
            postNumber.text = post.number.toString()

            // ???????thumbnailUrl ???????????
            val thumbUrl = post.thumbnailUrl

            if (thumbUrl.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(thumbUrl)
                    // 繁體中文註解：縮圖限制尺寸，避免載入過大圖片
                    .override(300, 300)
                    .centerCrop()
                    .into(image)
            } else {
                image.setImageDrawable(null)
            }

            itemView.setOnClickListener { onImageClick(position) }
            itemView.setOnLongClickListener {
                if (post.imageUrl.isNotBlank()) {
                    onImageLongClick(post.imageUrl)
                    true
                } else {
                    false
                }
            }
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
