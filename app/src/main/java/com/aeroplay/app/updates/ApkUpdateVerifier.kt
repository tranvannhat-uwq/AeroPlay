package com.aeroplay.app.updates

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

object UpdateHash {
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

@Singleton
class ApkUpdateVerifier @Inject constructor(@ApplicationContext private val context: Context) {
    @Suppress("DEPRECATION")
    fun verify(file: File, release: AppRelease) {
        require(file.isFile && file.length() == release.size) { "Tệp cập nhật tải chưa đầy đủ. Vui lòng tải lại." }
        require(UpdateHash.sha256(file).equals(release.sha256, true)) { "Tệp cập nhật bị hỏng hoặc sai SHA-256. Vui lòng tải lại." }
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = requireNotNull(context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)) { "Không đọc được APK cập nhật." }
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        fun version(info: PackageInfo) = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        fun certificates(info: PackageInfo): Set<String> {
            val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            return signatures.orEmpty().map { signature ->
                MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
            }.toSet()
        }
        require(archive.packageName == context.packageName) { "APK này không phải ứng dụng AeroPlay." }
        require(archive.applicationInfo?.minSdkVersion == release.minSdk && release.minSdk <= Build.VERSION.SDK_INT) {
            "APK không phù hợp với phiên bản Android của thiết bị."
        }
        require(version(archive) == release.versionCode && archive.versionName == release.versionName && version(archive) > version(installed)) {
            "Phiên bản APK không phù hợp với bản cập nhật đã chọn."
        }
        val trusted = certificates(installed)
        require(trusted.isNotEmpty() && certificates(archive) == trusted) { "Bản cập nhật không cùng chữ ký với AeroPlay đang cài. Không thể cài đè." }
    }
}
