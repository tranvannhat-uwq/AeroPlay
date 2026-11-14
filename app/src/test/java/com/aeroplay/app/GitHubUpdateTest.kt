package com.aeroplay.app

import com.aeroplay.app.updates.GitHubRepository
import com.aeroplay.app.updates.ReleaseMetadata
import com.aeroplay.app.updates.AppRelease
import com.aeroplay.app.updates.UpdateHash
import com.aeroplay.app.updates.GitHubReleaseClient
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException

class GitHubUpdateTest {
    private val repo = GitHubRepository.parse("https://github.com/tranvannhat-uwq/AeroPlay.git")
    private val hash = "a".repeat(64)
    private fun manifest() = JSONObject().put("schemaVersion", 1).put("packageName", "com.aeroplay.app")
        .put("versionCode", 3).put("versionName", "1.1.0").put("minSdk", 26)
        .put("apkName", "AeroPlay-v1.1.0.apk").put("size", 1000).put("sha256", hash)
    private fun release() = JSONObject().put("tag_name", "v1.1.0").put("draft", false).put("prerelease", false)
        .put("body", "New release").put("assets", JSONArray().put(JSONObject().put("name", "AeroPlay-v1.1.0.apk")
            .put("size", 1000).put("digest", "sha256:$hash")
            .put("browser_download_url", "https://github.com/tranvannhat-uwq/AeroPlay/releases/download/v1.1.0/AeroPlay-v1.1.0.apk")))

    @Test fun canonicalRepositoryAndCompleteReleaseRoundTrip() {
        assertEquals("tranvannhat-uwq/AeroPlay", repo.fullName)
        val parsed = ReleaseMetadata.parse(repo, release(), manifest())
        assertEquals(3L, parsed.versionCode)
        assertEquals(parsed, AppRelease.restore(parsed.toJson()))
    }

    @Test fun rejectsRepositoryLookalikesAndTraversal() {
        for (value in listOf("https://github.com.evil.test/u/r", "http://github.com/u/r", "u/..", "a/b/c", "https://github.com/u/r?token=x", "https://evil@github.com/u/r")) {
            assertThrows("Must reject $value", IllegalArgumentException::class.java) { GitHubRepository.parse(value) }
        }
    }

    @Test fun rejectsDraftPreReleaseAndMismatchedVersion() {
        assertThrows(IllegalArgumentException::class.java) { ReleaseMetadata.parse(repo, release().put("draft", true), manifest()) }
        assertThrows(IllegalArgumentException::class.java) { ReleaseMetadata.parse(repo, release().put("prerelease", true), manifest()) }
        assertThrows(IllegalArgumentException::class.java) { ReleaseMetadata.parse(repo, release().put("tag_name", "v9.9.9"), manifest()) }
    }

    @Test fun rejectsOtherPackageOrMissingApk() {
        assertThrows(IllegalArgumentException::class.java) { ReleaseMetadata.parse(repo, release(), manifest().put("packageName", "another.app")) }
        assertThrows(IOException::class.java) { ReleaseMetadata.parse(repo, release().put("assets", JSONArray()), manifest()) }
    }

    @Test fun rejectsSizeAndDigestMismatch() {
        assertThrows(IllegalArgumentException::class.java) { ReleaseMetadata.parse(repo, release(), manifest().put("size", 999)) }
        assertThrows(IllegalArgumentException::class.java) { ReleaseMetadata.parse(repo, release(), manifest().put("sha256", "b".repeat(64))) }
        assertThrows(IllegalArgumentException::class.java) { ReleaseMetadata.parse(repo, release(), manifest().put("sha256", "invalid")) }
    }

    @Test fun downloadMustBelongToSameRepositoryAndRelease() {
        for (url in listOf("https://evil.test/update.apk", "https://github.com/another/repo/releases/download/v1.1.0/x.apk",
            "https://github.com/tranvannhat-uwq/AeroPlay/releases/download/v1.0.0/x.apk")) {
            assertThrows(IllegalArgumentException::class.java) { ReleaseMetadata.validateAssetUrl(repo, "v1.1.0", url) }
        }
    }

    @Test fun realFileHashDetectsChangedBytes() {
        val file = File.createTempFile("update-hash", ".apk")
        try {
            file.writeText("abc")
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", UpdateHash.sha256(file))
            file.appendText("changed")
            assertNotEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", UpdateHash.sha256(file))
        } finally { file.delete() }
    }

    @Test fun clientReadsSmallMetadataBodiesAndBoundAssetUrl() = runBlocking<Unit> {
        val response = release()
        response.getJSONArray("assets").put(JSONObject().put("name", "update.json")
            .put("browser_download_url", "https://github.com/tranvannhat-uwq/AeroPlay/releases/download/v1.1.0/update.json"))
        var calls = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            val body = if (chain.request().url.host == "api.github.com") response.toString() else manifest().toString()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody()).build()
        }.build()
        assertEquals(3L, GitHubReleaseClient(client).latest(repo)?.versionCode)
        assertEquals(2, calls)
    }

    @Test fun repositoryWithoutReleaseIsNotANetworkSuccessClaim() = runBlocking<Unit> {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(404).message("Not Found")
                .body("{}".toResponseBody()).build()
        }.build()
        assertNull(GitHubReleaseClient(client).latest(repo))
    }
}
