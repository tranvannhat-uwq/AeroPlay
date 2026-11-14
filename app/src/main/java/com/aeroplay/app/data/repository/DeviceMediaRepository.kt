package com.aeroplay.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.aeroplay.app.core.database.AeroPlayDao
import com.aeroplay.app.core.database.DeviceVideoEntity
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.StreamVariant
import com.aeroplay.app.core.model.VideoDetail
import com.aeroplay.app.core.model.VideoItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

data class MediaImportState(
    val busy: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val progress: Float? = null,
    val message: String = ""
)

/** Keeps a private copy of explicitly selected videos so provider grants cannot expire mid-playback. */
@Singleton
class DeviceMediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AeroPlayDao
) {
    private val directory = File(context.filesDir, "device_media").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutation = Mutex()
    private val batches = Mutex()
    private val mutableImport = MutableStateFlow(MediaImportState())
    val importState = mutableImport.asStateFlow()
    val videos = dao.observeDeviceVideos().map { rows -> rows.map { it.summary() } }

    init {
        scope.launch {
            mutation.withLock {
                directory.listFiles()?.filter { it.extension == "part" }?.forEach { it.delete() }
            }
        }
    }

    fun importVideos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val selected = uris.distinct()
        if (!mutableImport.value.busy) {
            mutableImport.value = MediaImportState(busy = true, total = selected.size, message = "Đang thêm video…")
        }
        scope.launch {
            batches.withLock { importBatch(selected) }
        }
    }

    private suspend fun importBatch(selected: List<Uri>) {
        mutableImport.value = MediaImportState(busy = true, total = selected.size, message = "Đang thêm video…")
        var added = 0
        var duplicates = 0
        var failed = 0
        var errorMessage = ""
        try {
            selected.forEachIndexed { index, uri ->
                try {
                    if (importOne(uri)) added++ else duplicates++
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    failed++
                    errorMessage = error.message ?: "Không đọc được video."
                }
                mutableImport.value = mutableImport.value.copy(completed = index + 1, progress = null)
            }
        } finally {
            mutableImport.value = MediaImportState(
                completed = selected.size, total = selected.size,
                message = buildString {
                    if (added > 0) append("Đã thêm $added video. ")
                    if (duplicates > 0) append("$duplicates video đã có trong thư viện. ")
                    if (failed > 0) append("$failed video chưa thêm được. $errorMessage")
                }.trim()
            )
        }
    }

    internal suspend fun importOne(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "Hãy chọn video bằng trình chọn ảnh hoặc tệp." }
        mutation.withLock {
            val resolver = context.contentResolver
            var displayName = "Video"
            var size = -1L
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) displayName = it.getString(nameIndex) ?: displayName
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !it.isNull(sizeIndex)) size = it.getLong(sizeIndex)
                }
            }
            if (size > 0) check(size + 16L * 1024 * 1024 < directory.usableSpace) {
                "Không đủ bộ nhớ để lưu bản sao video."
            }
            val temporary = File(directory, "${UUID.randomUUID()}.part")
            var published: File? = null
            var artwork: File? = null
            var inserted = false
            try {
                val hash = MessageDigest.getInstance("SHA-256")
                var copied = 0L
                var reportedAt = 0L
                resolver.openInputStream(uri).use { input ->
                    checkNotNull(input) { "Không mở được video đã chọn." }
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            check(directory.usableSpace > count + 8L * 1024 * 1024) { "Bộ nhớ thiết bị đã đầy." }
                            output.write(buffer, 0, count)
                            hash.update(buffer, 0, count)
                            copied += count
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (now - reportedAt >= 100) {
                                reportedAt = now
                                mutableImport.value = mutableImport.value.copy(
                                    progress = if (size > 0) (copied.toFloat() / size).coerceIn(0f, 1f) else null,
                                    message = "Đang thêm $displayName"
                                )
                            }
                        }
                    }
                }
                check(copied > 0L) { "Video đã chọn đang trống." }
                val digest = hash.digest().joinToString("") { "%02x".format(it) }
                val id = "device_$digest"
                if (dao.getDeviceVideo(id) != null) return@withLock false
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(temporary.absolutePath)
                    check(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes") {
                        "Tệp đã chọn không có hình ảnh video hỗ trợ."
                    }
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    val baseName = displayName.substringBeforeLast('.', displayName)
                    val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        ?.takeIf { it.isNotBlank() } ?: baseName.takeUnless { it.all(Char::isDigit) }
                        ?: "Video từ Ảnh"
                    val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                        ?.takeIf { it.startsWith("video/") } ?: "video/mp4"
                    val thumbnail = File(directory, "$digest.jpg")
                    artwork = thumbnail
                    runCatching {
                        val frame = if (Build.VERSION.SDK_INT >= 27) {
                            retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 640, 360)
                        } else {
                            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { full ->
                                val scale = minOf(640f / full.width, 360f / full.height, 1f)
                                val resized = Bitmap.createScaledBitmap(full,
                                    (full.width * scale).toInt().coerceAtLeast(1),
                                    (full.height * scale).toInt().coerceAtLeast(1), true)
                                if (resized !== full) full.recycle()
                                resized
                            }
                        }
                        frame?.let { bitmap ->
                            try {
                                thumbnail.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                            } finally { bitmap.recycle() }
                        }
                    }
                    val file = File(directory, "$digest.media")
                    published = file
                    check(temporary.renameTo(file)) { "Không lưu được video vào thư viện." }
                    val row = DeviceVideoEntity(
                        id, title, file.name, if (thumbnail.isFile) thumbnail.name else "", mime,
                        duration, width, height, copied, System.currentTimeMillis()
                    )
                    withContext(NonCancellable) {
                        inserted = dao.insertDeviceVideo(row) != -1L
                    }
                    check(inserted) { "Không lưu được thông tin video." }
                    true
                } finally { retriever.release() }
            } finally {
                temporary.delete()
                if (!inserted) {
                    published?.delete()
                    artwork?.delete()
                }
            }
        }
    }

    suspend fun detail(idOrUrl: String): Result<VideoDetail> = withContext(Dispatchers.IO) {
        try {
            val id = if (idOrUrl.startsWith("device_")) idOrUrl else {
                val name = Uri.parse(idOrUrl).lastPathSegment.orEmpty()
                "device_${name.substringBefore('.')}"
            }
            val row = requireNotNull(dao.getDeviceVideo(id)) { "Video đã được xóa khỏi thư viện thiết bị." }
            val file = File(directory, row.fileName)
            check(file.isFile) { "Không tìm thấy bản sao video. Hãy chọn và thêm lại video." }
            val video = row.summary()
            Result.success(VideoDetail(
                id = row.videoId, url = video.url, title = row.title,
                description = "Video đã thêm từ thiết bị.", channelName = video.channelName,
                channelUrl = "", channelAvatarUrl = "", thumbnailUrl = video.thumbnailUrl,
                durationSeconds = video.durationSeconds, sourceType = ContentSourceType.DEVICE_LOCAL,
                streamVariants = listOf(StreamVariant(
                    id = "${row.videoId}_original", resolutionLabel = "Bản gốc",
                    width = row.width, height = row.height, url = video.url, formatMimeType = row.mimeType
                ))
            ))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Result.failure(error) }
    }

    suspend fun remove(videoId: String) = withContext(Dispatchers.IO) {
        mutation.withLock {
            val row = dao.getDeviceVideo(videoId) ?: return@withLock
            dao.removeDeviceReferences(videoId)
            File(directory, row.fileName).delete()
            if (row.thumbnailName.isNotEmpty()) File(directory, row.thumbnailName).delete()
        }
    }

    suspend fun rename(videoId: String, title: String) {
        val name = title.trim()
        require(name.isNotEmpty())
        mutation.withLock { dao.renameDeviceVideo(videoId, name) }
    }

    private fun DeviceVideoEntity.summary() = VideoItem(
        id = videoId, url = Uri.fromFile(File(directory, fileName)).toString(),
        title = title, channelName = "Trên thiết bị", channelUrl = "",
        thumbnailUrl = if (thumbnailName.isNotBlank()) Uri.fromFile(File(directory, thumbnailName)).toString() else "",
        durationSeconds = durationMs / 1000, uploadedText = "Đã lưu để phát ngoại tuyến",
        sourceType = ContentSourceType.DEVICE_LOCAL
    )
}
