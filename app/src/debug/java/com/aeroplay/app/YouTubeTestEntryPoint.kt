package com.aeroplay.app

import com.aeroplay.app.data.repository.ContentRepository
import com.aeroplay.app.data.repository.SearchRepository
import com.aeroplay.app.data.repository.SettingsRepository
import com.aeroplay.app.data.repository.VideoMetadataRepository
import com.aeroplay.app.playback.controller.PlaybackControllerManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.aeroplay.app.updates.AppUpdateManager
import com.aeroplay.app.updates.ApkUpdateVerifier

/** Exposes the production graph to device integration tests in debug builds only. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface YouTubeTestEntryPoint {
    fun deviceMedia(): com.aeroplay.app.data.repository.DeviceMediaRepository
    fun recommendations(): com.aeroplay.app.data.repository.PersonalizedRecommendations
    fun database(): com.aeroplay.app.core.database.AeroPlayDatabase
    fun content(): ContentRepository
    fun search(): SearchRepository
    fun metadata(): VideoMetadataRepository
    fun settings(): SettingsRepository
    fun playback(): PlaybackControllerManager
    fun updates(): AppUpdateManager
    fun updateVerifier(): ApkUpdateVerifier
}
