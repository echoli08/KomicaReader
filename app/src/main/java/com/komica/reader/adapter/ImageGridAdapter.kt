package com.komica.reader.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.komica.reader.databinding.ItemImageGridBinding

class ImageGridAdapter(
    private val imageUrls: List<String>,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<ImageGridAdapter.ImageGridViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageGridViewHolder {
        return ImageGridViewHolder(ItemImageGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount(): Int = imageUrls.size

    override fun onBindViewHolder(holder: ImageGridViewHolder, position: Int) = holder.Bind(imageUrls[position], position)

    inner class ImageGridViewHolder(private val binding: ItemImageGridBinding) : RecyclerView.ViewHolder(binding.root) {
        fun Bind(url: String, position: Int) {
            Glide.with(binding.gridImage).load(url).centerCrop().into(binding.gridImage)
            binding.root.setOnClickListener { onClick(position) }
        }
    }
}
