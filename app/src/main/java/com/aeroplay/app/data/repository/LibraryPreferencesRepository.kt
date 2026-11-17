package com.aeroplay.app.data.repository

import com.aeroplay.app.core.database.AeroPlayDao
import com.aeroplay.app.core.database.ContentRestrictionEntity
import com.aeroplay.app.core.database.EditedMixEntity
import com.aeroplay.app.core.model.AutoMix
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.VideoItem
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

internal object VideoSnapshotCodec {
    fun encode(video: VideoItem): JSONObject = JSONObject().apply {
        put("id", video.id)
        put("url", video.url)
        put("title", video.title)
        put("channel", video.channelName)
        put("channelUrl", video.channelUrl)
        put("avatar", video.channelAvatarUrl)
        put("thumbnail", video.thumbnailUrl)
        put("duration", video.durationSeconds)
        put("views", video.viewCount)
        put("uploaded", video.uploadedText)
        put("live", video.isLive)
        put("source", video.sourceType.name)
        put("publishedAt", video.publishedAtEpochMs)
    }

    fun decode(value: JSONObject): VideoItem = VideoItem(
        id = value.getString("id"), url = value.getString("url"), title = value.getString("title"),
        channelName = value.optString("channel"), channelUrl = value.optString("channelUrl"),
        channelAvatarUrl = value.optString("avatar"), thumbnailUrl = value.optString("thumbnail"),
        durationSeconds = value.optLong("duration"), viewCount = value.optLong("views", -1),
        uploadedText = value.optString("uploaded"), isLive = value.optBoolean("live"),
        sourceType = ContentSourceType.valueOf(value.getString("source")),
        publishedAtEpochMs = if (value.has("publishedAt") && !value.isNull("publishedAt")) value.getLong("publishedAt") else null)

    fun list(json: String): List<VideoItem> {
        val values = runCatching { JSONArray(json) }.getOrElse { return emptyList() }
        return (0 until values.length()).mapNotNull { index -> runCatching { decode(values.getJSONObject(index)) }.getOrNull() }
    }
}

@Singleton
class LibraryPreferencesRepository @Inject constructor(private val dao: AeroPlayDao) {
    val editedMixes = dao.observeEditedMixes().map { entries -> entries.map {
        AutoMix(it.mixId, it.title, it.subtitle, VideoSnapshotCodec.list(it.videosJson), it.isMusic, isCustomized = true)
    } }
    val restrictions = dao.observeContentRestrictions()

    suspend fun saveMix(mix: AutoMix) {
        val unique = mix.videos.distinctBy(RecommendationRanking::stableKey)
        val videos = JSONArray().apply { unique.forEach { put(VideoSnapshotCodec.encode(it)) } }
        dao.saveEditedMix(EditedMixEntity(mix.id, mix.title, "Bản phối bạn đã chỉnh sửa", mix.isMusic, videos.toString()))
    }

    suspend fun resetMix(id: String) = dao.resetEditedMix(id)

    suspend fun restrictVideo(video: VideoItem) {
        dao.saveContentRestriction(ContentRestrictionEntity(RecommendationRanking.stableKey(video), "video",
            video.title, VideoSnapshotCodec.encode(video).toString()))
    }

    suspend fun restrictStyle(style: String) {
        val normalized = RecommendationRanking.normalize(style).take(80)
        if (normalized.isNotBlank()) dao.saveContentRestriction(ContentRestrictionEntity(
            "style:$normalized", "style", style.trim().take(80), ""))
    }

    suspend fun removeRestriction(id: String) = dao.removeContentRestriction(id)
}

/** Removes explicitly limited videos and reduces the share of matching styles/topics. */
object ContentRestrictionPolicy {
    private data class Signals(val exact: Set<String>, val styles: Set<String>, val topics: List<Set<String>>)

    private fun signals(restrictions: List<ContentRestrictionEntity>): Signals {
        val exact = restrictions.filter { it.kind == "video" }.map { it.restrictionId }.toSet()
        val disliked = restrictions.filter { it.kind == "video" }.mapNotNull {
            runCatching { VideoSnapshotCodec.decode(JSONObject(it.videoJson)) }.getOrNull()
        }
        val styles = restrictions.filter { it.kind == "style" }.flatMap {
            listOf(RecommendationRanking.normalize(it.label)) + MusicStyleAnalyzer.analyze(it.label).genres
        }.toSet() +
            disliked.flatMap { MusicStyleAnalyzer.analyze(it.title, it.channelName).genres }
        val topics = disliked.map { RecommendationRanking.terms(it.title).toSet() }.filter { it.size >= 2 }
        return Signals(exact, styles, topics)
    }

    private fun similar(video: VideoItem, signals: Signals): Boolean {
        val genres = MusicStyleAnalyzer.analyze(video.title, video.channelName).genres
        val text = " ${RecommendationRanking.normalize(video.title + " " + video.channelName)} "
        val terms = RecommendationRanking.terms(video.title).toSet()
        return genres.any { it in signals.styles } || signals.styles.any { " $it " in text } ||
            signals.topics.any { topic ->
                val overlap = terms.intersect(topic).size
                overlap >= 2 && overlap.toDouble() / topic.size >= 0.4
            }
    }

    fun preferredSeeds(videos: List<VideoItem>, restrictions: List<ContentRestrictionEntity>): Set<String> {
        val signals = signals(restrictions)
        return videos.filterNot { RecommendationRanking.stableKey(it) in signals.exact || similar(it, signals) }
            .map(RecommendationRanking::stableKey).toSet()
    }

    fun apply(videos: List<VideoItem>, restrictions: List<ContentRestrictionEntity>): List<VideoItem> {
        if (restrictions.isEmpty()) return videos
        val signals = signals(restrictions)
        val available = videos.filterNot { RecommendationRanking.stableKey(it) in signals.exact }
        val (similar, other) = available.partition { similar(it, signals) }
        return other + similar.take(maxOf(1, other.size / 4))
    }
}
