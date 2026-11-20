package com.aeroplay.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aeroplay.app.core.model.AppSettings
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.ThemeMode
import com.aeroplay.app.data.youtube.YouTubePolicyFeatureFlags
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.aeroPlayDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "aeroplay_settings"
)

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val GLASS_EFFECTS_ENABLED = booleanPreferencesKey("glass_effects_enabled")
        val SOURCE_TYPE = stringPreferencesKey("active_source_type")
        val WARNING_ACCEPTED = booleanPreferencesKey("experimental_warning_accepted")
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        val DATA_SAVER = booleanPreferencesKey("data_saver_enabled")
        val DEFAULT_SPEED = floatPreferencesKey("default_playback_speed")
        val AUTO_AUDIO_ONLY = booleanPreferencesKey("auto_audio_only")
        val PAUSE_HEADPHONE = booleanPreferencesKey("pause_on_headphone_disconnect")
        val PIP_ENABLED = booleanPreferencesKey("pip_enabled")
        val BG_PLAYBACK = booleanPreferencesKey("background_playback_enabled")
        val AUTO_SIMILAR_MUSIC = booleanPreferencesKey("auto_play_similar_music")
        val AMBIENT_LIGHTING = booleanPreferencesKey("ambient_lighting_enabled")
        val STABLE_VOLUME = booleanPreferencesKey("stable_volume_enabled")
        val API_SOURCE_SELECTED = booleanPreferencesKey("youtube_api_source_selected_v1")
        val PLAYER_SOURCE_SELECTED = booleanPreferencesKey("youtube_player_source_selected_v1")
    }

    override val settingsFlow: Flow<AppSettings> = context.aeroPlayDataStore.data.map { prefs ->
        val theme = prefs[Keys.THEME_MODE]?.let {
            runCatching { ThemeMode.valueOf(it) }.getOrNull()
        } ?: ThemeMode.SYSTEM

        val configuredSource = prefs[Keys.SOURCE_TYPE]?.let {
            runCatching { ContentSourceType.valueOf(it) }.getOrNull()
        } ?: ContentSourceType.DEMO_LOCAL
        val needsYouTubeSource = if (YouTubePolicyFeatureFlags.apiOnlyMode)
            prefs[Keys.API_SOURCE_SELECTED] != true else prefs[Keys.PLAYER_SOURCE_SELECTED] != true
        val source = if (needsYouTubeSource)
            ContentSourceType.EXPERIMENTAL_EXTRACTOR else configuredSource

        AppSettings(
            themeMode = theme,
            activeSourceType = source,
            experimentalWarningAccepted = prefs[Keys.WARNING_ACCEPTED] ?: false,
            defaultQualityLabel = prefs[Keys.DEFAULT_QUALITY] ?: "720p",
            dataSaverEnabled = prefs[Keys.DATA_SAVER] ?: false,
            defaultPlaybackSpeed = prefs[Keys.DEFAULT_SPEED] ?: 1.0f,
            autoAudioOnlyWhenBackground = prefs[Keys.AUTO_AUDIO_ONLY] ?: false,
            pauseOnHeadphoneDisconnect = prefs[Keys.PAUSE_HEADPHONE] ?: true,
            pipEnabled = prefs[Keys.PIP_ENABLED] ?: true,
            backgroundPlaybackEnabled = prefs[Keys.BG_PLAYBACK] ?: true,
            autoPlaySimilarMusic = prefs[Keys.AUTO_SIMILAR_MUSIC] ?: true,
            ambientLightingEnabled = prefs[Keys.AMBIENT_LIGHTING] ?: false,
            stableVolumeEnabled = prefs[Keys.STABLE_VOLUME] ?: false,
            glassEffectsEnabled = prefs[Keys.GLASS_EFFECTS_ENABLED] ?: true
        )
    }

    override suspend fun getSettingsSnapshot(): AppSettings = settingsFlow.first()

    override suspend fun setThemeMode(mode: ThemeMode) {
        context.aeroPlayDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setGlassEffectsEnabled(enabled: Boolean) {
        context.aeroPlayDataStore.edit { it[Keys.GLASS_EFFECTS_ENABLED] = enabled }
    }

    override suspend fun setActiveSourceType(sourceType: ContentSourceType, warningAccepted: Boolean) {
        context.aeroPlayDataStore.edit {
            it[Keys.SOURCE_TYPE] = sourceType.name
            it[Keys.API_SOURCE_SELECTED] = true
            it[Keys.PLAYER_SOURCE_SELECTED] = true
            if (warningAccepted) {
                it[Keys.WARNING_ACCEPTED] = true
            }
        }
    }

    override suspend fun setDefaultQuality(qualityLabel: String) {
        context.aeroPlayDataStore.edit { it[Keys.DEFAULT_QUALITY] = qualityLabel }
    }

    override suspend fun setDataSaverEnabled(enabled: Boolean) {
        context.aeroPlayDataStore.edit { it[Keys.DATA_SAVER] = enabled }
    }

    override suspend fun setDefaultPlaybackSpeed(speed: Float) {
        context.aeroPlayDataStore.edit { it[Keys.DEFAULT_SPEED] = speed.coerceIn(0.5f, 2.0f) }
    }

    override suspend fun setAutoAudioOnlyWhenBackground(enabled: Boolean) {
        context.aeroPlayDataStore.edit { it[Keys.AUTO_AUDIO_ONLY] = enabled }
    }

    override suspend fun setPauseOnHeadphoneDisconnect(enabled: Boolean) {
        context.aeroPlayDataStore.edit { it[Keys.PAUSE_HEADPHONE] = enabled }
    }

    override suspend fun setPipEnabled(enabled: Boolean) {
        context.aeroPlayDataStore.edit { it[Keys.PIP_ENABLED] = enabled }
    }

    override suspend fun setBackgroundPlaybackEnabled(enabled: Boolean) {
        context.aeroPlayDataStore.edit { it[Keys.BG_PLAYBACK] = enabled }
    }

    override suspend fun setAutoPlaySimilarMusic(enabled: Boolean) {
        context.aeroPlayDataStore.edit { it[Keys.AUTO_SIMILAR_MUSIC] = enabled }
    }

    override suspend fun setAmbientLightingEnabled(enabled: Boolean) {
        context.aeroPlayDataStore.edit { it[Keys.AMBIENT_LIGHTING] = enabled }
    }

    override suspend fun setStableVolumeEnabled(enabled: Boolean) {
        context.aeroPlayDataStore.edit { it[Keys.STABLE_VOLUME] = enabled }
    }
}
