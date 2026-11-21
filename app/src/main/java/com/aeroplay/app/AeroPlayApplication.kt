package com.aeroplay.app

import android.app.Application
import android.app.WallpaperManager
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.aeroplay.app.core.network.NewPipeOkHttpDownloader
import com.aeroplay.app.data.youtube.YouTubeAccountRepository
import com.aeroplay.app.playback.widget.PlaybackWidget
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class AeroPlayApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var newPipeDownloader: NewPipeOkHttpDownloader

    @Inject
    lateinit var youtubeAccountRepository: YouTubeAccountRepository

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

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 27) {
            runCatching {
                WallpaperManager.getInstance(this).addOnColorsChangedListener({ _, which ->
                    if (which and WallpaperManager.FLAG_SYSTEM != 0) PlaybackWidget.redraw(this)
                }, Handler(mainLooper))
            }
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { youtubeAccountRepository.pruneExpiredApiData() }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        PlaybackWidget.redraw(this)
    }
}
