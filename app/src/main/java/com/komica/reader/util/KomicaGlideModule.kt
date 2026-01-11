package com.komica.reader.util

import android.content.Context
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions

@GlideModule
class KomicaGlideModule : AppGlideModule() {
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        // 繁體中文註解：設定 50MB 磁碟快取
        val diskCacheSizeBytes = 1024L * 1024L * 50L
        builder.setDiskCache(InternalCacheDiskCacheFactory(context, diskCacheSizeBytes))

        // 繁體中文註解：使用 20% 可用記憶體作為快取
        builder.setMemoryCache(LruResourceCache(calculateMemoryCacheSize()))

        // 繁體中文註解：預設採用較省記憶體的解碼格式，降低 OOM 風險
        builder.setDefaultRequestOptions(
            RequestOptions().format(DecodeFormat.PREFER_RGB_565)
        )
    }

    private fun calculateMemoryCacheSize(): Long {
        return (Runtime.getRuntime().maxMemory() * 0.2).toLong()
    }
}
