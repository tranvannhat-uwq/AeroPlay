package com.aeroplay.app.updates

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.aeroplay.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

enum class UpdateStage { IDLE, CHECKING, NO_RELEASE, CURRENT, AVAILABLE, DOWNLOADING, VERIFYING, READY, ERROR }

data class UpdateUiState(
    val repository: String, val stage: UpdateStage = UpdateStage.IDLE,
    val release: AppRelease? = null, val progress: Float? = null,
    val message: String = "", val promptVisible: Boolean = false
) {
    val busy get() = stage in setOf(UpdateStage.CHECKING, UpdateStage.DOWNLOADING, UpdateStage.VERIFYING)
}

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: GitHubReleaseClient,
    private val verifier: ApkUpdateVerifier
) {
    private val preferences = context.getSharedPreferences("aeroplay_updates", Context.MODE_PRIVATE)
    private val downloads = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutable = MutableStateFlow(UpdateUiState(preferences.getString("repository", BuildConfig.UPDATE_REPOSITORY)!!))
    val state = mutable.asStateFlow()
    private var checkJob: Job? = null
    private var downloadJob: Job? = null
    private var installing = false

    init { resumePendingDownload() }

    fun setRepository(input: String): Result<Unit> = runCatching {
        require(!state.value.busy && state.value.stage != UpdateStage.READY) { "Hãy hoàn tất hoặc hủy bản tải hiện tại trước khi đổi kho." }
        val repo = GitHubRepository.parse(input)
        preferences.edit().putString("repository", repo.fullName).remove("last_check").apply()
        mutable.value = UpdateUiState(repo.fullName)
    }

    fun checkOnLaunch() {
        if (preferences.getLong("download_id", -1) >= 0 || state.value.busy) return
        if (System.currentTimeMillis() - preferences.getLong("last_check", 0) < 24 * 60 * 60 * 1000L) return
        check(manual = false)
    }

    fun check(manual: Boolean = true) {
        if (state.value.busy || state.value.stage == UpdateStage.READY) {
            if (manual) showPrompt()
            return
        }
        checkJob?.cancel()
        mutable.update { it.copy(stage = UpdateStage.CHECKING, release = null, message = "Đang kiểm tra GitHub Releases…", promptVisible = manual) }
        checkJob = scope.launch {
            try {
                val repo = GitHubRepository.parse(state.value.repository)
                val release = client.latest(repo)
                coroutineContext.ensureActive()
                preferences.edit().putLong("last_check", System.currentTimeMillis()).apply()
                when {
                    release == null -> mutable.update { it.copy(stage = UpdateStage.NO_RELEASE, message = "Kho này chưa có bản phát hành ổn định kèm update.json và APK.", promptVisible = manual) }
                    release.versionCode <= BuildConfig.VERSION_CODE -> mutable.update { it.copy(stage = UpdateStage.CURRENT, message = "Bạn đang dùng bản AeroPlay mới nhất.", promptVisible = manual) }
                    release.minSdk > Build.VERSION.SDK_INT -> mutable.update { it.copy(stage = UpdateStage.ERROR, message = "Bản mới yêu cầu Android API ${release.minSdk} trở lên.", promptVisible = manual) }
                    else -> mutable.update { it.copy(stage = UpdateStage.AVAILABLE, release = release, message = "Có bản AeroPlay ${release.versionName}.", promptVisible = true) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { fail(error, manual) }
        }
    }

    fun showPrompt() { mutable.update { it.copy(promptVisible = true) } }
    fun dismissPrompt() { mutable.update { it.copy(promptVisible = false) } }

    fun download() {
        val release = state.value.release ?: return
        if (state.value.busy) return
        mutable.update { it.copy(stage = UpdateStage.DOWNLOADING, progress = null, message = "Đang bắt đầu tải APK…", promptVisible = true) }
        downloadJob = scope.launch {
            try {
                val target = apkFile(release)
                check(target.parentFile?.mkdirs() == true || target.parentFile?.isDirectory == true) { "Không tạo được thư mục cập nhật." }
                if (target.exists()) check(target.delete()) { "Không thay thế được tệp APK cũ." }
                val request = DownloadManager.Request(Uri.parse(release.apkUrl))
                    .setTitle("AeroPlay ${release.versionName}").setDescription("Đang tải bản cập nhật AeroPlay")
                    .setMimeType("application/vnd.android.package-archive")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "updates/${target.name}")
                val id = downloads.enqueue(request)
                preferences.edit().putLong("download_id", id).putString("pending_release", release.toJson()).commit()
                monitor(id, release)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { clearPending(); fail(error, true) }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        scope.launch {
            clearPending()
            mutable.update { it.copy(stage = if (it.release == null) UpdateStage.IDLE else UpdateStage.AVAILABLE,
                progress = null, message = "Đã hủy bản tải cập nhật.", promptVisible = false) }
        }
    }

    private fun resumePendingDownload() {
        val id = preferences.getLong("download_id", -1)
        if (id < 0) return
        downloadJob = scope.launch {
            try {
                val release = AppRelease.restore(requireNotNull(preferences.getString("pending_release", null)))
                if (release.versionCode <= BuildConfig.VERSION_CODE) {
                    clearPending()
                    mutable.update { it.copy(stage = UpdateStage.CURRENT, message = "AeroPlay đã được cập nhật thành công.") }
                    return@launch
                }
                mutable.update { it.copy(stage = UpdateStage.DOWNLOADING, release = release, message = "Đang tiếp tục bản cập nhật…") }
                monitor(id, release)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { clearPending(); fail(error, false) }
        }
    }

    private suspend fun monitor(id: Long, release: AppRelease) {
        while (coroutineContext.isActive) {
            val cursor = requireNotNull(downloads.query(DownloadManager.Query().setFilterById(id))) { "Không tìm thấy bản tải cập nhật." }
            val status = cursor.use {
                check(it.moveToFirst()) { "Bản tải cập nhật đã bị xóa. Hãy tải lại." }
                val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val bytes = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                if (status == DownloadManager.STATUS_FAILED) error("Tải APK thất bại (mã ${it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))}). Vui lòng thử lại.")
                mutable.update { state -> state.copy(progress = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else null,
                    message = if (status == DownloadManager.STATUS_PAUSED || status == DownloadManager.STATUS_PENDING) "Đang chờ mạng hoặc bộ tải Android…" else "Đang tải APK cập nhật…") }
                status
            }
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                mutable.update { it.copy(stage = UpdateStage.VERIFYING, message = "Đang kiểm tra APK và chữ ký…") }
                verifier.verify(apkFile(release), release)
                mutable.update { it.copy(stage = UpdateStage.READY, progress = 1f, message = "APK đã được kiểm tra. Sẵn sàng cài cập nhật.", promptVisible = true) }
                return
            }
            delay(750)
        }
    }

    fun install(activity: Activity) {
        if (state.value.stage != UpdateStage.READY || installing) return
        if (!context.packageManager.canRequestPackageInstalls()) {
            preferences.edit().putBoolean("waiting_permission", true).apply()
            mutable.update { it.copy(message = "Bật ‘Cho phép từ nguồn này’ cho AeroPlay, sau đó quay lại để cài cập nhật.") }
            runCatching { activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))) }
                .onFailure { fail(it, true) }
            return
        }
        val release = state.value.release ?: return
        installing = true
        scope.launch {
            try {
                val file = apkFile(release)
                verifier.verify(file, release)
                withContext(Dispatchers.Main) {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
                    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    activity.startActivity(intent)
                    mutable.update { it.copy(promptVisible = false, message = "Hãy xác nhận cập nhật trong trình cài đặt Android.") }
                }
            } catch (error: Exception) { fail(error, true) }
            finally { installing = false }
        }
    }

    fun onActivityResumed(activity: Activity) {
        if (preferences.getBoolean("waiting_permission", false) && state.value.stage == UpdateStage.READY) {
            preferences.edit().remove("waiting_permission").apply()
            if (context.packageManager.canRequestPackageInstalls()) install(activity) else showPrompt()
        }
    }

    private fun apkFile(release: AppRelease): File = File(
        requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)) { "Không có bộ nhớ để tải APK." },
        "updates/aeroplay-${release.versionCode}-${release.sha256.take(12)}.apk"
    )

    private fun clearPending() {
        val id = preferences.getLong("download_id", -1)
        if (id >= 0) runCatching { downloads.remove(id) }
        preferences.edit().remove("download_id").remove("pending_release").remove("waiting_permission").commit()
    }

    private fun fail(error: Throwable, visible: Boolean) {
        mutable.update { it.copy(stage = UpdateStage.ERROR, message = error.message ?: "Không thể cập nhật. Vui lòng kiểm tra mạng và thử lại.", promptVisible = visible) }
    }
}
