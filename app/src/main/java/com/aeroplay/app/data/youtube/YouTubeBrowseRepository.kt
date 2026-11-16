package com.aeroplay.app.data.youtube

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** OAuth is used only for documented metadata API requests, never for the embedded player. */
@Singleton
class YouTubeBrowseRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profiles: YouTubeAccountProfileStore,
    private val auth: GoogleIdentityAuthManager,
    private val api: YouTubeDataApiClient
) {
    private suspend fun token(): String {
        check(profiles.privacyPolicyAccepted.first()) { "Vào Cài đặt → Tài khoản YouTube để đăng nhập và cấp quyền đọc." }
        val profile = profiles.profile.first() ?: error("Vào Cài đặt → Tài khoản YouTube để đăng nhập Google.")
        val outcome = auth.authorize(context, profile, setOf(GoogleIdentityAuthManager.YOUTUBE_READONLY))
        check(outcome is YouTubeAuthorizationOutcome.Authorized) { "Cần cấp lại quyền. Vào Cài đặt → Tài khoản YouTube → Làm mới dữ liệu." }
        return outcome.accessToken
    }

    suspend fun home() = capture { api.popularVideos(token()) }
    suspend fun search(query: String, pageToken: String?) = capture { api.searchVideos(token(), query, pageToken) }

    private suspend fun <T> capture(block: suspend () -> T): Result<T> = try { Result.success(block()) }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { Result.failure(error) }
}
