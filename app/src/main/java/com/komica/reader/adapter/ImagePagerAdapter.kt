package com.komica.reader.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.komica.reader.databinding.ItemImagePreviewBinding
import com.komica.reader.widget.ZoomImageView

class ImagePagerAdapter(
    private val imageUrls: List<String>,
    private val maxScale: Float,
    private val onNavigate: (ZoomImageView.ImageNavigation) -> Unit,
    private val onLongClick: (String) -> Unit
) : RecyclerView.Adapter<ImagePagerAdapter.ImageViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        return ImageViewHolder(ItemImagePreviewBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount(): Int = imageUrls.size

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) = holder.Bind(imageUrls[position])

    inner class ImageViewHolder(private val binding: ItemImagePreviewBinding) : RecyclerView.ViewHolder(binding.root) {
        fun Bind(url: String) {
            binding.imagePreview.SetMaxScale(maxScale)
            binding.imagePreview.SetNavigationListener(onNavigate)
            binding.imagePreview.ResetZoom()
            Glide.with(binding.imagePreview).load(url).fitCenter().into(binding.imagePreview)
            binding.imagePreview.setOnLongClickListener {
                onLongClick(url)
                true
            }
        }
    }
}
