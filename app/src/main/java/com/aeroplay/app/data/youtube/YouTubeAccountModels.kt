package com.aeroplay.app.data.youtube

import android.app.PendingIntent

data class YouTubeAccountProfile(
    val accountId: String,
    val email: String,
    val displayName: String,
    val photoUrl: String = "",
    val channelId: String = "",
    val channelTitle: String = "",
    val channelThumbnailUrl: String = ""
)

enum class YouTubeSyncStatus {
    NOT_CONNECTED,
    CONNECTED,
    NEEDS_READ_AUTHORIZATION,
    SYNCING,
    SYNCED,
    OFFLINE,
    REAUTHORIZATION_REQUIRED,
    QUOTA_EXCEEDED,
    NO_YOUTUBE_CHANNEL,
    SELECT_YOUTUBE_CHANNEL,
    FAILED
}

data class YouTubeAccountUiState(
    val featureEnabled: Boolean = false,
    val configured: Boolean = false,
    val profile: YouTubeAccountProfile? = null,
    val status: YouTubeSyncStatus = YouTubeSyncStatus.NOT_CONNECTED,
    val statusMessage: String = "",
    val lastSyncAtEpochMs: Long? = null,
    val channels: List<YouTubeChannelOption> = emptyList(),
    val groupErrors: Map<String, String> = emptyMap(),
    val loadingPlaylistIds: Set<String> = emptySet(),
    val playlistLoadErrors: Map<String, String> = emptyMap(),
    val likedVideos: List<YouTubeLikedVideo> = emptyList(),
    val playlists: List<YouTubePlaylist> = emptyList(),
    val subscriptions: List<YouTubeSubscription> = emptyList(),
    val pendingAuthorization: PendingIntent? = null,
    val privacyPolicyAccepted: Boolean = false
)

data class YouTubeLikedVideo(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String,
    val publishedAt: String = ""
) {
    val watchUrl: String get() = "https://www.youtube.com/watch?v=$videoId"
}

data class YouTubePlaylist(
    val playlistId: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String,
    val itemCount: Int,
    val privacyStatus: String,
    val etag: String = "",
    val items: List<YouTubePlaylistItem> = emptyList()
)

data class YouTubePlaylistItem(
    val playlistItemId: String,
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String,
    val position: Int
) {
    val watchUrl: String get() = "https://www.youtube.com/watch?v=$videoId"
}

data class YouTubeSubscription(
    val subscriptionId: String,
    val channelId: String,
    val channelTitle: String,
    val thumbnailUrl: String,
    val description: String = ""
)

data class YouTubeChannelOption(
    val channelId: String,
    val title: String,
    val thumbnailUrl: String
)

data class YouTubeAccountData(
    val likedVideos: List<YouTubeLikedVideo> = emptyList(),
    val playlists: List<YouTubePlaylist> = emptyList(),
    val subscriptions: List<YouTubeSubscription> = emptyList()
)

data class YouTubeReadSyncResult(
    val channels: List<YouTubeChannelOption>,
    val likedVideos: List<YouTubeLikedVideo>,
    val playlists: List<YouTubePlaylist>,
    val subscriptions: List<YouTubeSubscription>,
    val groupErrors: Map<String, String> = emptyMap()
)

sealed interface YouTubeAuthorizationOutcome {
    data class Authorized(val accessToken: String, val grantedScopes: Set<String>) : YouTubeAuthorizationOutcome
    data class NeedsUserAction(val pendingIntent: PendingIntent) : YouTubeAuthorizationOutcome
}
