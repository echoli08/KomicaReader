package com.komica.reader.util;

import android.content.Context;
import androidx.annotation.NonNull;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.load.engine.cache.LruResourceCache;
import com.bumptech.glide.module.AppGlideModule;

@GlideModule
public class KomicaGlideModule extends AppGlideModule {
    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        // 50MB Disk Cache
        int diskCacheSizeBytes = 1024 * 1024 * 50; 
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, diskCacheSizeBytes));
        
        // Use 20% of available memory for memory cache
        builder.setMemoryCache(new LruResourceCache(calculateMemoryCacheSize(context)));
    }

    private int calculateMemoryCacheSize(Context context) {
        return (int) (Runtime.getRuntime().maxMemory() * 0.2);
    }
}
