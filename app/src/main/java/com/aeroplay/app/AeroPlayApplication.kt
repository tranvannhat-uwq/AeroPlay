package com.aeroplay.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.aeroplay.app.core.network.NewPipeOkHttpDownloader
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class AeroPlayApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var newPipeDownloader: NewPipeOkHttpDownloader

    override fun newImageLoader(): ImageLoader {
        // Optimized thumbnail cache for 4GB RAM devices (Section VII)
        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(48L * 1024L * 1024L) // 48 MB thumbnail cache
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
