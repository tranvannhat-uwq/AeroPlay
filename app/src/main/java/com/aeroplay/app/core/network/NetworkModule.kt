package com.aeroplay.app.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideNewPipeDownloader(okHttpClient: OkHttpClient): NewPipeOkHttpDownloader {
        val downloader = NewPipeOkHttpDownloader(okHttpClient)
        synchronized(NewPipe::class.java) {
            if (NewPipe.getDownloader() == null) {
                NewPipe.init(
                    downloader,
                    Localization("vi", "VN"),
                    ContentCountry("VN")
                )
                SourceDiagnostics.event("newpipe.init", "localization=vi-VN country=VN")
            }
        }
        return downloader
    }
}
