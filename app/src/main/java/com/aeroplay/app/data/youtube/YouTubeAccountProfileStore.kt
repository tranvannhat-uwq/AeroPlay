package com.aeroplay.app.data.youtube

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.youtubeAccountStore by preferencesDataStore(name = "youtube_account_profile")

@Singleton
class YouTubeAccountProfileStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ACCOUNT_ID = stringPreferencesKey("account_id")
        val EMAIL = stringPreferencesKey("email")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val PHOTO_URL = stringPreferencesKey("photo_url")
        val CHANNEL_ID = stringPreferencesKey("channel_id")
        val CHANNEL_TITLE = stringPreferencesKey("channel_title")
        val CHANNEL_THUMBNAIL_URL = stringPreferencesKey("channel_thumbnail_url")
        val CHANNEL_CACHED_AT = longPreferencesKey("channel_cached_at_epoch_ms")
        val LAST_SYNC_ACCOUNT_ID = stringPreferencesKey("last_sync_account_id")
        val LAST_SYNC = longPreferencesKey("last_sync_epoch_ms")
        val PRIVACY_POLICY_ACCEPTED = booleanPreferencesKey("youtube_api_privacy_accepted_v1")
    }

    val privacyPolicyAccepted: Flow<Boolean> = context.youtubeAccountStore.data.map { it[Keys.PRIVACY_POLICY_ACCEPTED] == true }

    val profile: Flow<YouTubeAccountProfile?> = context.youtubeAccountStore.data.map { prefs ->
        val accountId = prefs[Keys.ACCOUNT_ID].orEmpty()
        if (accountId.isBlank()) null else YouTubeAccountProfile(
            accountId = accountId,
            email = prefs[Keys.EMAIL].orEmpty(),
            displayName = prefs[Keys.DISPLAY_NAME].orEmpty(),
            photoUrl = prefs[Keys.PHOTO_URL].orEmpty(),
            channelId = prefs[Keys.CHANNEL_ID].orEmpty(),
            channelTitle = prefs[Keys.CHANNEL_TITLE].orEmpty(),
            channelThumbnailUrl = prefs[Keys.CHANNEL_THUMBNAIL_URL].orEmpty()
        )
    }

    val lastSyncAtEpochMs: Flow<Long?> = context.youtubeAccountStore.data.map { prefs ->
        val currentPartition = "${prefs[Keys.ACCOUNT_ID].orEmpty()}|${prefs[Keys.CHANNEL_ID].orEmpty()}"
        prefs[Keys.LAST_SYNC]?.takeIf { prefs[Keys.LAST_SYNC_ACCOUNT_ID] == currentPartition }
    }

    suspend fun save(profile: YouTubeAccountProfile) {
        context.youtubeAccountStore.edit { prefs ->
            prefs[Keys.ACCOUNT_ID] = profile.accountId
            prefs[Keys.EMAIL] = profile.email
            prefs[Keys.DISPLAY_NAME] = profile.displayName
            prefs[Keys.PHOTO_URL] = profile.photoUrl
            prefs[Keys.CHANNEL_ID] = profile.channelId
            prefs[Keys.CHANNEL_TITLE] = profile.channelTitle
            prefs[Keys.CHANNEL_THUMBNAIL_URL] = profile.channelThumbnailUrl
        }
    }

    suspend fun saveChannel(channel: YouTubeChannelOption) {
        context.youtubeAccountStore.edit { prefs ->
            prefs[Keys.CHANNEL_ID] = channel.channelId
            prefs[Keys.CHANNEL_TITLE] = channel.title
            prefs[Keys.CHANNEL_THUMBNAIL_URL] = channel.thumbnailUrl
            prefs[Keys.CHANNEL_CACHED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun pruneExpiredChannelData(cutoffEpochMs: Long) {
        context.youtubeAccountStore.edit { prefs ->
            if ((prefs[Keys.CHANNEL_CACHED_AT] ?: 0L) < cutoffEpochMs) {
                prefs.remove(Keys.CHANNEL_ID)
                prefs.remove(Keys.CHANNEL_TITLE)
                prefs.remove(Keys.CHANNEL_THUMBNAIL_URL)
                prefs.remove(Keys.CHANNEL_CACHED_AT)
                prefs.remove(Keys.LAST_SYNC_ACCOUNT_ID)
                prefs.remove(Keys.LAST_SYNC)
            }
        }
    }

    suspend fun clearChannelData() {
        context.youtubeAccountStore.edit { prefs ->
            prefs.remove(Keys.CHANNEL_ID)
            prefs.remove(Keys.CHANNEL_TITLE)
            prefs.remove(Keys.CHANNEL_THUMBNAIL_URL)
            prefs.remove(Keys.CHANNEL_CACHED_AT)
            prefs.remove(Keys.LAST_SYNC_ACCOUNT_ID)
            prefs.remove(Keys.LAST_SYNC)
        }
    }

    suspend fun markSynced(accountPartition: String, atEpochMs: Long) {
        context.youtubeAccountStore.edit {
            it[Keys.LAST_SYNC_ACCOUNT_ID] = accountPartition
            it[Keys.LAST_SYNC] = atEpochMs
        }
    }

    suspend fun acceptPrivacyPolicy() {
        context.youtubeAccountStore.edit { it[Keys.PRIVACY_POLICY_ACCEPTED] = true }
    }

    suspend fun clear() {
        context.youtubeAccountStore.edit {
            it.remove(Keys.ACCOUNT_ID)
            it.remove(Keys.EMAIL)
            it.remove(Keys.DISPLAY_NAME)
            it.remove(Keys.PHOTO_URL)
            it.remove(Keys.CHANNEL_ID)
            it.remove(Keys.CHANNEL_TITLE)
            it.remove(Keys.CHANNEL_THUMBNAIL_URL)
            it.remove(Keys.CHANNEL_CACHED_AT)
            it.remove(Keys.LAST_SYNC_ACCOUNT_ID)
            it.remove(Keys.LAST_SYNC)
        }
    }
}
