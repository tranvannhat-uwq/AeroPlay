package com.aeroplay.app.playback.car

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.SearchContentType
import com.aeroplay.app.core.model.VideoItem
import com.aeroplay.app.data.repository.*
import com.aeroplay.app.playback.service.AeroPlayMediaService
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CarMediaLibrary @Inject constructor(
    private val history: HistoryRepository,
    private val playlists: PlaylistRepository,
    private val device: DeviceMediaRepository,
    private val search: SearchRepository,
    private val metadata: VideoMetadataRepository,
    private val resolver: StreamResolver,
    private val settings: SettingsRepository
) {
    companion object {
        const val EXTRA_CAR_AUDIO = "com.aeroplay.app.CAR_AUDIO"
        const val EXTRA_VIDEO_ID = "com.aeroplay.app.CAR_VIDEO_ID"
        const val EXTRA_SOURCE = "com.aeroplay.app.CAR_SOURCE"
        const val EXTRA_ORIGINAL_URL = "com.aeroplay.app.CAR_ORIGINAL_URL"
        const val EXTRA_PARENT = "com.aeroplay.app.CAR_PARENT"
        const val EXTRA_DURATION = "com.aeroplay.app.CAR_DURATION_SECONDS"

        fun summary(item: MediaItem?): VideoItem? {
            val m = item?.mediaMetadata ?: return null
            val extras = m.extras ?: return null
            if (!extras.getBoolean(EXTRA_CAR_AUDIO)) return null
            val id = extras.getString(EXTRA_VIDEO_ID) ?: return null
            val source = runCatching { ContentSourceType.valueOf(extras.getString(EXTRA_SOURCE).orEmpty()) }.getOrNull() ?: return null
            return VideoItem(id, extras.getString(EXTRA_ORIGINAL_URL).orEmpty(), m.title?.toString().orEmpty(),
                m.artist?.toString().orEmpty(), thumbnailUrl = m.artworkUri?.toString().orEmpty(), durationSeconds = extras.getLong(EXTRA_DURATION),
                sourceType = source)
        }
    }

    private val tracks = LinkedHashMap<String, VideoItem>()
    private val contexts = LinkedHashMap<String, List<VideoItem>>()
    private val searches = LinkedHashMap<String, List<VideoItem>>()
    private val trackParents = LinkedHashMap<String, String>()

    fun folder(id: String, title: String) = MediaItem.Builder().setMediaId(id).setMediaMetadata(
        MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build()).build()

    fun root() = folder(CarBrowseIds.ROOT, "AeroPlay")

    suspend fun children(parentId: String): List<MediaItem> = when (parentId) {
        CarBrowseIds.ROOT -> listOf(folder(CarBrowseIds.FAVORITES, "Yêu thích"), folder(CarBrowseIds.RECENT, "Nghe gần đây"),
            folder(CarBrowseIds.PLAYLISTS, "Danh sách phát"), folder(CarBrowseIds.DEVICE, "Trên thiết bị"), folder(CarBrowseIds.MUSIC, "Khám phá nhạc"))
        CarBrowseIds.PLAYLISTS -> playlists.observePlaylists().first().take(100).map { folder("car/playlist/${it.id}", it.name) }
        else -> videos(parentId).map { browseItem(it, parentId) }
    }

    private suspend fun videos(parentId: String): List<VideoItem> {
        val result = when (parentId) {
            CarBrowseIds.FAVORITES -> history.observeFavorites().first()
            CarBrowseIds.RECENT -> history.observeWatchHistory().first()
            CarBrowseIds.DEVICE -> device.videos.first()
            CarBrowseIds.MUSIC -> search.search("nhạc chill", sourceOverride = settings.getSettingsSnapshot().activeSourceType,
                contentType = SearchContentType.VIDEO).getOrThrow().videos
            else -> {
                val playlistId = parentId.removePrefix("car/playlist/").takeIf { parentId.startsWith("car/playlist/") }?.toLongOrNull()
                require(playlistId != null && playlistId > 0) { "Unknown media folder" }
                playlists.observePlaylistVideos(playlistId).first()
            }
        }.distinctBy { it.sourceType to it.id }.take(100)
        rememberContext(parentId, result)
        return result
    }

    suspend fun searchItems(query: String): List<MediaItem> {
        require(query.isNotBlank() && query.length <= 200)
        val result = search.search(query.trim(), sourceOverride = settings.getSettingsSnapshot().activeSourceType,
            contentType = SearchContentType.VIDEO).getOrThrow().videos.take(50)
        searches[query] = result
        while (searches.size > 10) searches.remove(searches.keys.first())
        val parent = "car/search/$query"
        rememberContext(parent, result)
        return result.map { browseItem(it, parent) }
    }

    suspend fun searchResults(query: String): List<MediaItem> = searches[query]?.map { browseItem(it, "car/search/$query") }
        ?: searchItems(query)

    suspend fun item(id: String): MediaItem {
        if (id == CarBrowseIds.ROOT) return root()
        children(CarBrowseIds.ROOT).find { it.mediaId == id }?.let { return it }
        if (id.startsWith("car/playlist/")) {
            return children(CarBrowseIds.PLAYLISTS).first { it.mediaId == id }
        }
        return browseItem(findTrack(id), "")
    }

    private suspend fun findTrack(id: String): VideoItem {
        tracks[id]?.let { return it }
        val (sourceName, originalId) = requireNotNull(CarBrowseIds.decodeTrack(id)) { "Unknown media ID" }
        val source = ContentSourceType.valueOf(sourceName)
        val saved = (history.observeFavorites().first() + history.observeWatchHistory().first() + device.videos.first())
            .firstOrNull { it.id == originalId && it.sourceType == source }
        if (saved != null) return saved
        // Restoring an old browse ID must resolve real metadata rather than trust a controller-supplied URI.
        require(source != ContentSourceType.DEVICE_LOCAL) { "Local item no longer available" }
        if (source == ContentSourceType.EXPERIMENTAL_EXTRACTOR) require(originalId.matches(Regex("[A-Za-z0-9_-]{11}")))
        val detail = metadata.getVideoDetail(originalId, source).getOrThrow()
        return VideoItem(detail.id, detail.url, detail.title, detail.channelName, thumbnailUrl = detail.thumbnailUrl,
            durationSeconds = detail.durationSeconds, sourceType = source)
    }

    suspend fun selection(requested: MediaItem): Pair<VideoItem, List<VideoItem>> {
        val query = requested.requestMetadata.searchQuery
        if (requested.mediaId.isBlank() && query.isNullOrBlank()) {
            resumption()?.let { return it to emptyList() }
            val music = searchItems("nhạc chill")
            require(music.isNotEmpty()) { "No music available" }
            return selection(music.first())
        }
        val selected = if (!query.isNullOrBlank()) {
            val results = searchItems(query)
            require(results.isNotEmpty()) { "No search results" }
            results.first()
        } else requested
        val video = findTrack(selected.mediaId)
        val parent = selected.requestMetadata.extras?.getString(EXTRA_PARENT) ?: trackParents[selected.mediaId]
        val context = parent?.let { contexts[it] }.orEmpty()
        val index = context.indexOfFirst { it.id == video.id && it.sourceType == video.sourceType }
        return video to if (index >= 0) context.drop(index + 1).take(99) else emptyList()
    }

    suspend fun resolve(video: VideoItem): MediaItem {
        val detail = metadata.getVideoDetail(video.url, video.sourceType).getOrThrow()
        val selection = detail.hlsManifestUrl?.takeIf { it.isNotBlank() }?.let {
            ResolvedStreamSelection(primaryUrl = it, mimeType = "application/x-mpegURL", isAudioOnly = true)
        } ?: resolver.resolveStream(detail, isAudioOnly = true, dataSaver = settings.getSettingsSnapshot().dataSaverEnabled).getOrThrow()
        val extras = Bundle().apply {
            putBoolean(EXTRA_CAR_AUDIO, true)
            putString(EXTRA_VIDEO_ID, video.id)
            putString(EXTRA_SOURCE, video.sourceType.name)
            putString(EXTRA_ORIGINAL_URL, video.url)
            putLong(EXTRA_DURATION, detail.durationSeconds)
            putString(AeroPlayMediaService.EXTRA_PRIMARY_MIME_TYPE, selection.mimeType)
        }
        return MediaItem.Builder().setMediaId(CarBrowseIds.track(video.sourceType.name, video.id))
            .setUri(selection.primaryUrl).setMimeType(selection.mimeType)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(detail.title).setArtist(detail.channelName)
                .setArtworkUri(detail.thumbnailUrl.takeIf { it.isNotBlank() }?.let(Uri::parse))
                .setIsBrowsable(false).setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).setExtras(extras).build()).build()
    }

    suspend fun resumption(): VideoItem? = history.observeWatchHistory().first().firstOrNull()

    private fun browseItem(video: VideoItem, parent: String): MediaItem {
        val id = CarBrowseIds.track(video.sourceType.name, video.id)
        tracks[id] = video
        trackParents[id] = parent
        while (tracks.size > 512) tracks.remove(tracks.keys.first())
        while (trackParents.size > 512) trackParents.remove(trackParents.keys.first())
        return MediaItem.Builder().setMediaId(id)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(Bundle().apply { putString(EXTRA_PARENT, parent) }).build())
            .setMediaMetadata(MediaMetadata.Builder().setTitle(video.title).setArtist(video.channelName)
                .setArtworkUri(video.thumbnailUrl.takeIf { it.isNotBlank() }?.let(Uri::parse))
                .setIsBrowsable(false).setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build()).build()
    }

    private fun rememberContext(id: String, videos: List<VideoItem>) {
        contexts[id] = videos
        while (contexts.size > 20) contexts.remove(contexts.keys.first())
    }
}
