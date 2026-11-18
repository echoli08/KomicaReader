package com.komica.reader.adapter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.komica.reader.databinding.ItemImageSplitBinding
import com.komica.reader.widget.ZoomImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

// 依 Rect 區域延遲解碼每一頁，不一次載入所有頁面點陣圖，降低記憶體占用。
class ImageSplitPagerAdapter(
    private val file: File,
    private val regions: List<Rect>,
    private val pagerWidth: Int,
    private val maxScale: Float,
    private val onNavigate: (ZoomImageView.ImageNavigation) -> Unit
) : RecyclerView.Adapter<ImageSplitPagerAdapter.PageViewHolder>() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        return PageViewHolder(
            ItemImageSplitBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun getItemCount(): Int = regions.size

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) = holder.Bind(regions[position])

    override fun onViewRecycled(holder: PageViewHolder) = holder.Recycle()

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        scope.cancel()
        super.onDetachedFromRecyclerView(recyclerView)
    }

    inner class PageViewHolder(private val binding: ItemImageSplitBinding) : RecyclerView.ViewHolder(binding.root) {
        private var decodeJob: Job? = null

        fun Bind(rect: Rect) {
            decodeJob?.cancel()
            binding.pageError.visibility = View.GONE
            binding.pageProgress.visibility = View.VISIBLE
            binding.splitImage.setImageDrawable(null)
            binding.splitImage.SetMaxScale(maxScale)
            binding.splitImage.SetNavigationListener(onNavigate)
            binding.splitImage.ResetZoom()
            decodeJob = scope.launch {
                val bitmap = withContext(Dispatchers.IO) { DecodeRegion(rect) }
                binding.pageProgress.visibility = View.GONE
                if (bitmap == null) {
                    binding.pageError.visibility = View.VISIBLE
                } else {
                    binding.splitImage.setImageBitmap(bitmap)
                }
            }
        }

        // 回收時取消進行中的解碼並清空 ImageView，避免殘留點陣圖與寫入已回收的 View。
        fun Recycle() {
            decodeJob?.cancel()
            decodeJob = null
            binding.splitImage.setImageDrawable(null)
        }

        private fun DecodeRegion(rect: Rect): Bitmap? {
            val decoder = BitmapRegionDecoder.newInstance(file.absolutePath, false) ?: return null
            return try {
                val options = BitmapFactory.Options().apply { inSampleSize = SampleSize(rect) }
                decoder.decodeRegion(rect, options)
            } catch (e: Exception) {
                null
            } finally {
                decoder.recycle()
            }
        }

        // 取樣率為 2 的次幂，目標讓解碼寬度不超過 pager 寬度約 2 倍，兼顧清晰度與記憶體。
        private fun SampleSize(rect: Rect): Int {
            val denominator = max(1, pagerWidth * 2)
            return Integer.highestOneBit(max(1, rect.width() / denominator)).coerceAtLeast(1)
        }
    }
}
