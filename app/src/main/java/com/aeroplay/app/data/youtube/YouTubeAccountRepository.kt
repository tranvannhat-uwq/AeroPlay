package com.aeroplay.app.data.youtube

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.aeroplay.app.BuildConfig
import com.aeroplay.app.core.database.AeroPlayDao
import com.aeroplay.app.core.database.YouTubeLikedVideoEntity
import com.aeroplay.app.core.database.YouTubeRemotePlaylistEntity
import com.aeroplay.app.core.database.YouTubeRemotePlaylistItemEntity
import com.aeroplay.app.core.database.YouTubeSubscriptionEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class YouTubeAccountRepository @Inject constructor(
    private val authManager: GoogleIdentityAuthManager,
    private val profileStore: YouTubeAccountProfileStore,
    private val api: YouTubeDataApiClient,
    private val dao: AeroPlayDao
) {
    private val syncStatus = MutableStateFlow(YouTubeSyncStatus.NOT_CONNECTED)
    private val statusMessage = MutableStateFlow("")
    private val availableChannels = MutableStateFlow<List<YouTubeChannelOption>>(emptyList())
    private val groupErrors = MutableStateFlow<Map<String, String>>(emptyMap())

    val state = profileStore.profile.flatMapLatest { profile ->
        if (profile == null) {
            profileStore.privacyPolicyAccepted.map { accepted ->
                YouTubeAccountUiState(
                    featureEnabled = YouTubePolicyFeatureFlags.accountSyncEnabled,
                    configured = BuildConfig.YOUTUBE_ACCOUNT_SYNC_ENABLED,
                    status = YouTubeSyncStatus.NOT_CONNECTED,
                    privacyPolicyAccepted = accepted
                )
            }
        } else {
            val accountKey = accountKey(profile)
            val data = combine(
                dao.observeYouTubeLikedVideos(accountKey),
                dao.observeYouTubePlaylists(accountKey),
                dao.observeYouTubePlaylistItems(accountKey),
                dao.observeYouTubeSubscriptions(accountKey),
                profileStore.lastSyncAtEpochMs
            ) { likes, playlists, playlistItems, subscriptions, lastSync ->
                CachedAccountData(likes, playlists, playlistItems, subscriptions, lastSync)
            }
            val accountState = combine(data, syncStatus, statusMessage, availableChannels, groupErrors) { cached, status, message, channels, errors ->
                val itemsByPlaylist = cached.playlistItems.groupBy { it.playlistId }
                YouTubeAccountUiState(
                    featureEnabled = YouTubePolicyFeatureFlags.accountSyncEnabled,
                    configured = BuildConfig.YOUTUBE_ACCOUNT_SYNC_ENABLED,
                    profile = profile,
                    status = if (status == YouTubeSyncStatus.NOT_CONNECTED) YouTubeSyncStatus.CONNECTED else status,
                    statusMessage = message,
                    lastSyncAtEpochMs = cached.lastSync,
                    channels = channels,
                    groupErrors = errors,
                    likedVideos = cached.likes.map { it.toDomain() },
                    playlists = cached.playlists.map { playlist ->
                        playlist.toDomain(itemsByPlaylist[playlist.playlistId].orEmpty().map { it.toDomain() })
                    },
                    subscriptions = cached.subscriptions.map { it.toDomain() }
                )
            }
            combine(accountState, profileStore.privacyPolicyAccepted) { current, accepted ->
                current.copy(privacyPolicyAccepted = accepted)
            }
        }
    }

    suspend fun signIn(activity: Activity): YouTubeAccountProfile {
        check(YouTubePolicyFeatureFlags.accountSyncEnabled) {
            "YouTube account sync is disabled. Configure an OAuth web client ID and API-only mode first."
        }
        val profile = authManager.signIn(activity)
        profileStore.save(profile)
        availableChannels.value = emptyList()
        groupErrors.value = emptyMap()
        syncStatus.value = YouTubeSyncStatus.NEEDS_READ_AUTHORIZATION
        statusMessage.value = "Đã đăng nhập Google. Cần cấp quyền đọc YouTube để tải dữ liệu tài khoản."
        pruneExpiredApiData()
        return profile
    }

    suspend fun currentProfile(): YouTubeAccountProfile? = profileStore.profile.first()

    suspend fun acceptPrivacyPolicy() = profileStore.acceptPrivacyPolicy()

    suspend fun hasAcceptedPrivacyPolicy(): Boolean = profileStore.privacyPolicyAccepted.first()

    suspend fun authorizeRead(context: Context, profile: YouTubeAccountProfile): YouTubeAuthorizationOutcome =
        authManager.authorize(context, profile, setOf(GoogleIdentityAuthManager.YOUTUBE_READONLY))

    fun setAwaitingReadAuthorization(message: String = "Google cần bạn xác nhận quyền đọc YouTube.") {
        syncStatus.value = YouTubeSyncStatus.NEEDS_READ_AUTHORIZATION
        statusMessage.value = message
    }

    fun setConnectedMessage(message: String) {
        syncStatus.value = YouTubeSyncStatus.CONNECTED
        statusMessage.value = message
    }

    fun completeAuthorization(context: Context, intent: Intent): YouTubeAuthorizationOutcome.Authorized =
        authManager.fromAuthorizationIntent(context, intent)

    suspend fun syncReadOnly(profile: YouTubeAccountProfile, accessToken: String): YouTubeReadSyncResult {
        syncStatus.value = YouTubeSyncStatus.SYNCING
        statusMessage.value = "Đang đọc dữ liệu YouTube…"
        groupErrors.value = emptyMap()
        return try {
            withTimeout(SYNC_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    pruneExpiredApiData()
                    val channels = api.listMyChannels(accessToken)
                    availableChannels.value = channels
                    when {
                        channels.isEmpty() -> {
                            syncStatus.value = YouTubeSyncStatus.NO_YOUTUBE_CHANNEL
                            statusMessage.value = "Tài khoản Google này chưa có kênh YouTube mà API có thể đọc."
                            return@withContext YouTubeReadSyncResult(channels = emptyList(), likedVideos = emptyList(), playlists = emptyList(), subscriptions = emptyList())
                        }
                        profile.channelId.isNotBlank() && channels.none { it.channelId == profile.channelId } -> {
                            syncStatus.value = YouTubeSyncStatus.SELECT_YOUTUBE_CHANNEL
                            statusMessage.value = "Kênh đã chọn không còn thuộc tài khoản này. Hãy chọn lại kênh từ danh sách API trả về."
                            return@withContext YouTubeReadSyncResult(channels = channels, likedVideos = emptyList(), playlists = emptyList(), subscriptions = emptyList())
                        }
                        profile.channelId.isBlank() && channels.size > 1 -> {
                            syncStatus.value = YouTubeSyncStatus.SELECT_YOUTUBE_CHANNEL
                            statusMessage.value = "Tài khoản có nhiều kênh. Chọn đúng kênh YouTube trước khi tải dữ liệu."
                            return@withContext YouTubeReadSyncResult(channels = channels, likedVideos = emptyList(), playlists = emptyList(), subscriptions = emptyList())
                        }
                    }
                    val channel = channels.singleOrNull() ?: channels.firstOrNull { it.channelId == profile.channelId }
                    if (channel != null) {
                        profileStore.saveChannel(channel)
                    }
                    val key = accountKey(profile.copy(channelId = channel?.channelId.orEmpty()))
                    val errors = linkedMapOf<String, String>()
                    var likes = emptyList<YouTubeLikedVideo>()
                    var playlists = emptyList<YouTubePlaylist>()
                    var subscriptions = emptyList<YouTubeSubscription>()
                    try {
                        likes = api.listLikedVideos(accessToken)
                        val now = System.currentTimeMillis()
                        dao.replaceYouTubeLikedVideos(key, likes.map {
                            YouTubeLikedVideoEntity(key, it.videoId, it.title, it.channelTitle, it.thumbnailUrl, it.publishedAt, now)
                        })
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { errors["likes"] = safeMessage(error) }
                    try {
                        playlists = api.listMyPlaylists(accessToken)
                        val now = System.currentTimeMillis()
                        dao.replaceYouTubePlaylists(key, playlists.map {
                            YouTubeRemotePlaylistEntity(key, it.playlistId, it.title, it.description, it.thumbnailUrl,
                                it.itemCount, it.privacyStatus, it.etag, now)
                        })
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { errors["playlists"] = safeMessage(error) }
                    try {
                        subscriptions = api.listMySubscriptions(accessToken)
                        val now = System.currentTimeMillis()
                        dao.replaceYouTubeSubscriptions(key, subscriptions.map {
                            YouTubeSubscriptionEntity(key, it.subscriptionId, it.channelId, it.channelTitle,
                                it.thumbnailUrl, it.description, now)
                        })
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { errors["subscriptions"] = safeMessage(error) }
                    groupErrors.value = errors
                    val now = System.currentTimeMillis()
                    if (errors.isEmpty()) {
                        profileStore.markSynced(key, now)
                        syncStatus.value = YouTubeSyncStatus.SYNCED
                        statusMessage.value = "Đã làm mới dữ liệu từ YouTube."
                    } else {
                        syncStatus.value = statusFor(errors.values.first())
                        statusMessage.value = "Một số mục chưa tải được. Dữ liệu còn hạn mức cache vẫn được giữ."
                    }
                    YouTubeReadSyncResult(channels, likes, playlists, subscriptions, errors)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            syncStatus.value = statusFor(safeMessage(error))
            statusMessage.value = safeMessage(error)
            throw error
        }
    }

    suspend fun selectChannel(channel: YouTubeChannelOption) {
        profileStore.saveChannel(channel)
        syncStatus.value = YouTubeSyncStatus.CONNECTED
        statusMessage.value = "Đã chọn kênh ${channel.title}. Nhấn Làm mới dữ liệu YouTube để tiếp tục."
    }

    suspend fun loadPlaylistItems(
        profile: YouTubeAccountProfile,
        accessToken: String,
        playlistId: String,
        forceRefresh: Boolean = false
    ): List<YouTubePlaylistItem> = withContext(Dispatchers.IO) {
        pruneExpiredApiData()
        val key = accountKey(profile)
        val cached = dao.observeYouTubePlaylistItems(key).first()
            .filter { it.playlistId == playlistId }
            .sortedBy { it.position }
        val cacheIsFresh = cached.isNotEmpty() &&
            System.currentTimeMillis() - cached.maxOf { it.cachedAtEpochMs } < PLAYLIST_ITEMS_CACHE_MS
        if (cacheIsFresh && !forceRefresh) return@withContext cached.map { it.toDomain() }
        val fetched = api.listPlaylistItems(accessToken, playlistId)
        val now = System.currentTimeMillis()
        dao.replaceYouTubePlaylistItems(key, playlistId, fetched.map {
            YouTubeRemotePlaylistItemEntity(key, playlistId, it.playlistItemId, it.videoId, it.title,
                it.channelTitle, it.thumbnailUrl, it.position, now)
        })
        fetched
    }

    suspend fun hasFreshPlaylistItems(profile: YouTubeAccountProfile, playlistId: String): Boolean = withContext(Dispatchers.IO) {
        val items = dao.observeYouTubePlaylistItems(accountKey(profile)).first()
            .filter { it.playlistId == playlistId }
        items.isNotEmpty() && System.currentTimeMillis() - items.maxOf { it.cachedAtEpochMs } < PLAYLIST_ITEMS_CACHE_MS
    }

    suspend fun signOut(context: Context, revokeAuthorization: Boolean = true) {
        val profile = profileStore.profile.first()
        val failure = runCatching {
            if (revokeAuthorization) authManager.revoke(context) else authManager.clearCredentialState(context)
        }.exceptionOrNull()
        profile?.let { dao.deleteAllYouTubeDataForAccountIdentity(it.accountId) }
        profileStore.clear()
        availableChannels.value = emptyList()
        groupErrors.value = emptyMap()
        syncStatus.value = YouTubeSyncStatus.NOT_CONNECTED
        statusMessage.value = failure?.let { "Đã đăng xuất khỏi AeroPlay; Google chưa xác nhận thu hồi quyền." }.orEmpty()
        if (failure != null) throw failure
    }

    suspend fun clearExpiredAccessToken(context: Context, token: String) {
        authManager.clearExpiredToken(context, token)
    }

    suspend fun handleAuthorizationExpired(profile: YouTubeAccountProfile) {
        dao.deleteAllYouTubeDataForAccountIdentity(profile.accountId)
        profileStore.clearChannelData()
        syncStatus.value = YouTubeSyncStatus.REAUTHORIZATION_REQUIRED
        statusMessage.value = "Quyền truy cập đã hết hạn hoặc bị thu hồi. Cache YouTube đã được xóa; hãy cấp lại quyền đọc để tải dữ liệu mới."
    }

    suspend fun pruneExpiredApiData() {
        val cutoff = System.currentTimeMillis() - YOUTUBE_DATA_MAX_AGE_MS
        dao.pruneExpiredYouTubeData(cutoff)
        profileStore.pruneExpiredChannelData(cutoff)
    }

    private fun accountKey(profile: YouTubeAccountProfile): String =
        "${profile.accountId}|${profile.channelId}"

    private fun safeMessage(error: Throwable): String = when (error) {
        is YouTubeApiException -> error.message
        is IOException -> "Không có kết nối mạng hoặc YouTube API tạm thời không phản hồi."
        else -> error.message?.takeIf { it.length <= 240 } ?: "Không thể đồng bộ dữ liệu YouTube."
    }

    private fun statusFor(message: String): YouTubeSyncStatus = when {
        message.contains("401") || message.contains("authorization expired", ignoreCase = true) -> YouTubeSyncStatus.REAUTHORIZATION_REQUIRED
        message.contains("quota", ignoreCase = true) -> YouTubeSyncStatus.QUOTA_EXCEEDED
        message.contains("kết nối mạng", ignoreCase = true) || message.contains("network", ignoreCase = true) -> YouTubeSyncStatus.OFFLINE
        else -> YouTubeSyncStatus.FAILED
    }

    private data class CachedAccountData(
        val likes: List<YouTubeLikedVideoEntity>,
        val playlists: List<YouTubeRemotePlaylistEntity>,
        val playlistItems: List<YouTubeRemotePlaylistItemEntity>,
        val subscriptions: List<YouTubeSubscriptionEntity>,
        val lastSync: Long?
    )

    private fun YouTubeLikedVideoEntity.toDomain() = YouTubeLikedVideo(videoId, title, channelTitle, thumbnailUrl, publishedAt)
    private fun YouTubeRemotePlaylistEntity.toDomain(items: List<YouTubePlaylistItem>) =
        YouTubePlaylist(playlistId, title, description, thumbnailUrl, itemCount, privacyStatus, etag, items)
    private fun YouTubeRemotePlaylistItemEntity.toDomain() =
        YouTubePlaylistItem(playlistItemId, videoId, title, channelTitle, thumbnailUrl, position)
    private fun YouTubeSubscriptionEntity.toDomain() =
        YouTubeSubscription(subscriptionId, channelId, channelTitle, thumbnailUrl, description)

    private fun statusFor(error: YouTubeApiException): YouTubeSyncStatus = when {
        error.httpCode == 401 -> YouTubeSyncStatus.REAUTHORIZATION_REQUIRED
        error.httpCode == 403 && error.reason.contains("quota", ignoreCase = true) -> YouTubeSyncStatus.QUOTA_EXCEEDED
        else -> statusFor(error.message)
    }

    private companion object {
        const val YOUTUBE_DATA_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
        const val PLAYLIST_ITEMS_CACHE_MS = 24L * 60 * 60 * 1000
        const val SYNC_TIMEOUT_MS = 180_000L
    }
}
