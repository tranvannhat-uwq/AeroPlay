package com.aeroplay.app.feature.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aeroplay.app.core.network.SourceDiagnostics
import com.aeroplay.app.data.youtube.YouTubeAccountRepository
import com.aeroplay.app.data.youtube.YouTubeAccountUiState
import com.aeroplay.app.data.youtube.YouTubeAuthorizationOutcome
import com.aeroplay.app.data.youtube.YouTubeChannelOption
import com.aeroplay.app.data.youtube.YouTubeSyncStatus
import com.aeroplay.app.data.youtube.YouTubePolicyFeatureFlags
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class YouTubeAccountViewModel @Inject constructor(
    private val repository: YouTubeAccountRepository
) : ViewModel() {
    private enum class AuthorizationPurpose { FULL_SYNC, OPEN_PLAYLIST }

    private data class PendingAuthorizationAction(
        val purpose: AuthorizationPurpose,
        val playlistId: String = "",
        val forceRefresh: Boolean = false
    )

    private val pendingIntent = MutableStateFlow<PendingIntent?>(null)
    private val authorizationMessage = MutableStateFlow<String?>(null)
    private val loadingPlaylists = MutableStateFlow<Set<String>>(emptySet())
    private val playlistErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _state = combine(
        repository.state,
        pendingIntent,
        loadingPlaylists,
        playlistErrors,
        authorizationMessage
    ) { base, authIntent, loading, errors, authError ->
        base.copy(
            pendingAuthorization = authIntent,
            loadingPlaylistIds = loading,
            playlistLoadErrors = errors,
            statusMessage = authError ?: base.statusMessage
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YouTubeAccountUiState(
        featureEnabled = YouTubePolicyFeatureFlags.accountSyncEnabled,
        configured = YouTubePolicyFeatureFlags.accountSyncEnabled
    ))
    val state: StateFlow<YouTubeAccountUiState> = _state
    private var pendingAction: PendingAuthorizationAction? = null

    fun signIn(activity: Activity) {
        viewModelScope.launch {
            try {
                if (!repository.hasAcceptedPrivacyPolicy()) {
                    authorizationMessage.value = "Hãy đọc và chấp nhận Chính sách quyền riêng tư YouTube API trước khi đăng nhập."
                    return@launch
                }
                val profile = repository.signIn(activity)
                authorizationMessage.value = null
                requestReadAccess(activity, profile, PendingAuthorizationAction(AuthorizationPurpose.FULL_SYNC))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                SourceDiagnostics.oauthFailure("sign_in", error)
                authorizationMessage.value = "Không thể đăng nhập Google. Kiểm tra Google Play services và cấu hình OAuth rồi thử lại."
            }
        }
    }

    fun acceptPrivacyPolicyAndSignIn(activity: Activity) {
        viewModelScope.launch {
            try {
                repository.acceptPrivacyPolicy()
                authorizationMessage.value = null
                val profile = repository.signIn(activity)
                requestReadAccess(activity, profile, PendingAuthorizationAction(AuthorizationPurpose.FULL_SYNC))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                SourceDiagnostics.oauthFailure("sign_in_after_consent", error)
                authorizationMessage.value = "Không thể đăng nhập Google. Kiểm tra Google Play services và cấu hình OAuth rồi thử lại."
            }
        }
    }

    fun refresh(context: Context) {
        viewModelScope.launch {
            val profile = repository.currentProfile() ?: return@launch
            requestReadAccess(context, profile, PendingAuthorizationAction(AuthorizationPurpose.FULL_SYNC))
        }
    }

    fun openPlaylist(context: Context, playlistId: String, forceRefresh: Boolean = false) {
        if (playlistId in loadingPlaylists.value) return
        loadingPlaylists.value = loadingPlaylists.value + playlistId
        playlistErrors.value = playlistErrors.value - playlistId
        viewModelScope.launch {
            val profile = repository.currentProfile()
            if (profile == null) {
                loadingPlaylists.value = loadingPlaylists.value - playlistId
                return@launch
            }
            if (!forceRefresh && repository.hasFreshPlaylistItems(profile, playlistId)) {
                loadingPlaylists.value = loadingPlaylists.value - playlistId
                return@launch
            }
            requestReadAccess(context, profile, PendingAuthorizationAction(AuthorizationPurpose.OPEN_PLAYLIST, playlistId, forceRefresh))
        }
    }

    fun selectChannel(context: Context, channel: YouTubeChannelOption) {
        viewModelScope.launch {
            try {
                repository.selectChannel(channel)
                refresh(context)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                repository.setConnectedMessage("Không thể chọn kênh YouTube. Hãy làm mới dữ liệu rồi thử lại.")
            }
        }
    }

    fun onAuthorizationResult(context: Context, data: Intent?) {
        val action = pendingAction
        pendingAction = null
        pendingIntent.value = null
        if (data == null || action == null) {
            repository.setAwaitingReadAuthorization("Chưa hoàn tất cấp quyền đọc YouTube. Bạn có thể thử lại.")
            return
        }
        viewModelScope.launch {
            try {
                val grant = repository.completeAuthorization(context, data)
                executeAuthorizedAction(context, grant.accessToken, action)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                SourceDiagnostics.oauthFailure("authorization_result", error)
                val message = "Không hoàn tất được quyền đọc YouTube. Kiểm tra OAuth Consent, scope youtube.readonly và tài khoản thử nghiệm."
                repository.setConnectedMessage(message)
                if (action.purpose == AuthorizationPurpose.OPEN_PLAYLIST) {
                    playlistErrors.value = playlistErrors.value + (action.playlistId to message)
                    loadingPlaylists.value = loadingPlaylists.value - action.playlistId
                }
            }
        }
    }

    fun onAuthorizationCancelled() {
        val action = pendingAction
        pendingAction = null
        pendingIntent.value = null
        repository.setAwaitingReadAuthorization("Bạn đã hủy cấp quyền đọc YouTube.")
        if (action?.purpose == AuthorizationPurpose.OPEN_PLAYLIST) {
            loadingPlaylists.value = loadingPlaylists.value - action.playlistId
        }
    }

    fun authorizationLaunchHandled() {
        pendingIntent.value = null
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            try {
                repository.signOut(context)
                pendingIntent.value = null
                pendingAction = null
                authorizationMessage.value = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                SourceDiagnostics.oauthFailure("revoke", error)
                repository.setConnectedMessage("Đã đăng xuất và xóa cache cục bộ; Google chưa xác nhận thu hồi quyền.")
            }
        }
    }

    private suspend fun requestReadAccess(
        context: Context,
        profile: com.aeroplay.app.data.youtube.YouTubeAccountProfile,
        action: PendingAuthorizationAction
    ) {
        try {
            when (val auth = repository.authorizeRead(context, profile)) {
                is YouTubeAuthorizationOutcome.NeedsUserAction -> {
                    pendingAction = action
                    pendingIntent.value = auth.pendingIntent
                    repository.setAwaitingReadAuthorization()
                }
                is YouTubeAuthorizationOutcome.Authorized -> executeAuthorizedAction(context, auth.accessToken, action)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            SourceDiagnostics.oauthFailure("request_read_scope", error)
            val message = "Không thể yêu cầu quyền YouTube. Kiểm tra Android OAuth Client, Web Client ID và Google Play services."
            repository.setConnectedMessage(message)
            if (action.purpose == AuthorizationPurpose.OPEN_PLAYLIST) {
                playlistErrors.value = playlistErrors.value + (action.playlistId to message)
                loadingPlaylists.value = loadingPlaylists.value - action.playlistId
            }
        }
    }

    private suspend fun executeAuthorizedAction(
        context: Context,
        accessToken: String,
        action: PendingAuthorizationAction
    ) {
        val profile = repository.currentProfile() ?: return
        when (action.purpose) {
            AuthorizationPurpose.FULL_SYNC -> {
                try {
                    repository.syncReadOnly(profile, accessToken)
                } catch (error: Exception) {
                    if (error is com.aeroplay.app.data.youtube.YouTubeApiException && error.httpCode == 401) {
                        SourceDiagnostics.oauthFailure("youtube_api_unauthorized", error)
                        runCatching { repository.clearExpiredAccessToken(context, accessToken) }
                        repository.handleAuthorizationExpired(profile)
                    }
                }
            }
            AuthorizationPurpose.OPEN_PLAYLIST -> {
                try {
                    repository.loadPlaylistItems(profile, accessToken, action.playlistId, action.forceRefresh)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (error is com.aeroplay.app.data.youtube.YouTubeApiException && error.httpCode == 401) {
                        SourceDiagnostics.oauthFailure("playlist_api_unauthorized", error)
                        runCatching { repository.clearExpiredAccessToken(context, accessToken) }
                        repository.handleAuthorizationExpired(profile)
                    }
                    playlistErrors.value = playlistErrors.value +
                        (action.playlistId to (error.message ?: "Không thể tải bài trong playlist."))
                } finally {
                    loadingPlaylists.value = loadingPlaylists.value - action.playlistId
                }
            }
        }
    }
}
