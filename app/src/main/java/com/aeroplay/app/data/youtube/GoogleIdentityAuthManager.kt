package com.aeroplay.app.data.youtube

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import com.aeroplay.app.BuildConfig
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.tasks.await
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleIdentityAuthManager @Inject constructor() {
    companion object {
        const val YOUTUBE_READONLY = "https://www.googleapis.com/auth/youtube.readonly"
    }

    suspend fun signIn(activity: Activity): YouTubeAccountProfile {
        check(BuildConfig.YOUTUBE_ACCOUNT_SYNC_ENABLED) { "Google / YouTube sync is not configured." }
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.YOUTUBE_OAUTH_WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val response = CredentialManager.create(activity).getCredential(activity, request)
        val credential = response.credential
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            error("Google returned an unsupported sign-in credential.")
        }
        val googleCredential = try {
            GoogleIdTokenCredential.createFrom(credential.data)
        } catch (error: GoogleIdTokenParsingException) {
            throw IllegalStateException("Google sign-in returned an invalid identity response.", error)
        }
        val email = googleCredential.id.trim().lowercase(Locale.ROOT)
        check(email.isNotBlank()) { "Google did not return an account identifier." }
        return YouTubeAccountProfile(
            accountId = email,
            email = googleCredential.id,
            displayName = googleCredential.displayName.orEmpty().ifBlank { googleCredential.id },
            photoUrl = googleCredential.profilePictureUri?.toString().orEmpty()
        )
    }

    suspend fun authorize(
        context: Context,
        profile: YouTubeAccountProfile,
        scopes: Set<String>
    ): YouTubeAuthorizationOutcome {
        val request = AuthorizationRequest.builder()
            .setAccount(Account(profile.email, "com.google"))
            .setRequestedScopes(scopes.map(::Scope))
            .build()
        return fromResult(Identity.getAuthorizationClient(context).authorize(request).await())
    }

    fun fromAuthorizationIntent(context: Context, data: Intent): YouTubeAuthorizationOutcome.Authorized {
        val result: AuthorizationResult = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
        val accessToken = result.accessToken?.takeIf(String::isNotBlank)
            ?: error("Google did not return an access token.")
        return YouTubeAuthorizationOutcome.Authorized(accessToken, result.grantedScopes.orEmpty().toSet())
    }

    suspend fun revoke(context: Context) {
        Identity.getAuthorizationClient(context).revokeAccess(RevokeAccessRequest.builder().build()).await()
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    }

    suspend fun clearCredentialState(context: Context) {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    }

    suspend fun clearExpiredToken(context: Context, accessToken: String) {
        Identity.getAuthorizationClient(context)
            .clearToken(ClearTokenRequest.builder().setToken(accessToken).build()).await()
    }

    private fun fromResult(result: AuthorizationResult): YouTubeAuthorizationOutcome =
        if (result.hasResolution()) {
            YouTubeAuthorizationOutcome.NeedsUserAction(
                checkNotNull(result.pendingIntent) { "Google authorization did not provide a consent screen." }
            )
        } else {
            YouTubeAuthorizationOutcome.Authorized(
                accessToken = result.accessToken?.takeIf(String::isNotBlank)
                    ?: error("Google did not return an access token."),
                grantedScopes = result.grantedScopes.orEmpty().toSet()
            )
        }
}
