package com.aeroplay.app.updates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class GitHubRepository(val owner: String, val name: String) {
    val fullName get() = "$owner/$name"
    companion object {
        fun parse(input: String): GitHubRepository {
            val text = input.trim().trimEnd('/')
            val path = if (text.startsWith("https://")) {
                val url = requireNotNull(text.toHttpUrlOrNull()) { "Link GitHub không hợp lệ." }
                require(url.host == "github.com" && url.port == 443 && url.username.isEmpty() && url.password.isEmpty()) { "Chỉ hỗ trợ kho trên github.com." }
                require(url.query == null && url.fragment == null) { "Hãy nhập link trang chính của kho GitHub." }
                url.encodedPath.trim('/')
            } else text
            val pieces = path.removeSuffix(".git").split('/')
            require(pieces.size == 2 && pieces.all { it.matches(Regex("[A-Za-z0-9_.-]{1,100}")) && it != "." && it != ".." }) {
                "Nhập link GitHub dạng https://github.com/tai-khoan/ten-kho."
            }
            return GitHubRepository(pieces[0], pieces[1])
        }
    }
}

data class AppRelease(
    val repository: String, val tag: String, val versionName: String, val versionCode: Long,
    val minSdk: Int, val apkName: String, val apkUrl: String, val size: Long, val sha256: String,
    val notes: String
) {
    fun toJson() = JSONObject().put("repository", repository).put("tag", tag).put("versionName", versionName)
        .put("versionCode", versionCode).put("minSdk", minSdk).put("apkName", apkName).put("apkUrl", apkUrl)
        .put("size", size).put("sha256", sha256).put("notes", notes).toString()

    companion object {
        fun restore(text: String): AppRelease {
            val json = JSONObject(text)
            val repo = GitHubRepository.parse(json.getString("repository"))
            val tag = json.getString("tag")
            val url = json.getString("apkUrl")
            ReleaseMetadata.validateAssetUrl(repo, tag, url)
            require(json.getString("sha256").matches(Regex("[a-f0-9]{64}")) && json.getLong("size") in 1..ReleaseMetadata.MAX_APK_BYTES &&
                json.getLong("versionCode") > 0) { "Thông tin bản tải đã lưu không hợp lệ." }
            return AppRelease(repo.fullName, tag, json.getString("versionName"), json.getLong("versionCode"),
                json.getInt("minSdk"), json.getString("apkName"), url, json.getLong("size"),
                json.getString("sha256"), json.optString("notes"))
        }
    }
}

object ReleaseMetadata {
    const val MAX_APK_BYTES = 200L * 1024 * 1024

    fun validateAssetUrl(repo: GitHubRepository, tag: String, value: String) {
        val url = requireNotNull(value.toHttpUrlOrNull()) { "Link bản cập nhật không hợp lệ." }
        val parts = url.pathSegments
        require(url.isHttps && url.host == "github.com" && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
            parts.size == 6 && parts[0].equals(repo.owner, true) && parts[1].equals(repo.name, true) &&
            parts[2] == "releases" && parts[3] == "download" && parts[4] == tag && url.query == null && url.fragment == null) {
            "Link tải không thuộc bản phát hành GitHub đã chọn."
        }
    }

    fun parse(repo: GitHubRepository, release: JSONObject, manifest: JSONObject): AppRelease {
        require(!release.optBoolean("draft") && !release.optBoolean("prerelease")) { "Bản phát hành này chưa phải bản ổn định." }
        require(manifest.getInt("schemaVersion") == 1 && manifest.getString("packageName") == "com.aeroplay.app") { "Bản cập nhật không dành cho AeroPlay." }
        val tag = release.getString("tag_name")
        val version = manifest.getString("versionName")
        require(tag == "v$version") { "Phiên bản trong file cập nhật không khớp GitHub Release." }
        val versionCode = manifest.getLong("versionCode")
        require(versionCode > 0 && manifest.getInt("minSdk") >= 26) { "Thông tin phiên bản không hợp lệ." }
        val name = manifest.getString("apkName")
        require(name.matches(Regex("AeroPlay-v[0-9][A-Za-z0-9._-]*\\.apk"))) { "Tên APK cập nhật không hợp lệ." }
        val assets = release.getJSONArray("assets")
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.singleOrNull { it.optString("name") == name }
            ?: throw IOException("GitHub Release chưa đính kèm APK phù hợp.")
        val url = apk.getString("browser_download_url")
        validateAssetUrl(repo, tag, url)
        require(apk.getLong("size") == manifest.getLong("size") && apk.getLong("size") in 1..MAX_APK_BYTES) { "Kích thước APK không khớp thông tin phát hành." }
        val hash = manifest.getString("sha256").lowercase()
        require(hash.matches(Regex("[a-f0-9]{64}"))) { "Bản phát hành thiếu SHA-256 hợp lệ." }
        apk.optString("digest").takeIf { it.isNotBlank() && it != "null" }?.let {
            require(it.equals("sha256:$hash", true)) { "SHA-256 không khớp GitHub Release." }
        }
        return AppRelease(repo.fullName, tag, version, versionCode, manifest.getInt("minSdk"), name, url,
            apk.getLong("size"), hash, release.optString("body").take(6000))
    }
}

@Singleton
class GitHubReleaseClient @Inject constructor(private val client: OkHttpClient) {
    suspend fun latest(repo: GitHubRepository): AppRelease? = withContext(Dispatchers.IO) {
        val endpoint = "https://api.github.com/repos/${repo.fullName}/releases/latest".toHttpUrl()
        val release = fetchJson(endpoint, allowMissing = true) ?: return@withContext null
        val tag = release.getString("tag_name")
        val assets = release.getJSONArray("assets")
        val metadata = (0 until assets.length()).map { assets.getJSONObject(it) }.singleOrNull { it.optString("name") == "update.json" }
            ?: throw IOException("GitHub Release chưa có update.json. Hãy phát hành bằng workflow của AeroPlay.")
        val url = metadata.getString("browser_download_url")
        ReleaseMetadata.validateAssetUrl(repo, tag, url)
        ReleaseMetadata.parse(repo, release, requireNotNull(fetchJson(url.toHttpUrl(), false)))
    }

    private suspend fun fetchJson(url: HttpUrl, allowMissing: Boolean): JSONObject? = runInterruptible {
        val request = Request.Builder().url(url).header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28").header("User-Agent", "AeroPlay-Android-Updater").build()
        client.newCall(request).execute().use { response ->
            if (allowMissing && response.code == 404) return@runInterruptible null
            if (response.code == 403 || response.code == 429) throw IOException("GitHub tạm giới hạn truy cập. Vui lòng thử lại sau.")
            if (!response.isSuccessful) throw IOException("Không thể kiểm tra cập nhật (HTTP ${response.code}).")
            require(response.request.url.isHttps) { "Kết nối cập nhật phải dùng HTTPS." }
            val source = requireNotNull(response.body).source()
            source.request(1024 * 1024 + 1L)
            val bytes = source.buffer.readByteArray().also { require(it.size <= 1024 * 1024) { "File thông tin cập nhật quá lớn." } }
            JSONObject(bytes.toString(Charsets.UTF_8))
        }
    }
}
