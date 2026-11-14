package com.aeroplay.app.data.repository

import com.aeroplay.app.core.database.AeroPlayDao
import com.aeroplay.app.core.database.HistoryEntity
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.VideoItem
import com.aeroplay.app.data.source.DemoLocalMediaSource
import com.aeroplay.app.data.source.ExperimentalExtractorSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln

data class RecommendationSeed(val video: VideoItem, val plays: Int, val watchedAt: Long)

/** Title/channel signals are calculated on-device. Only selected search terms go to the active source. */
internal object RecommendationRanking {
    private val marks = Regex("\\p{M}+")
    private val words = Regex("[\\p{L}\\p{N}]+")
    private val stopWords = setOf(
        "the", "and", "for", "with", "from", "official", "video", "videos", "music", "lyrics",
        "lyric", "audio", "full", "hd", "4k", "1080p", "720p", "mv", "live", "feat", "ft",
        "remastered", "cua", "va", "la", "nhung", "mot", "cac", "nhac", "bai", "hat",
        "nhat", "moi", "hay", "nhieu", "nhat", "ban", "phim", "open", "movie", "test", "clip"
    )

    fun terms(value: String): List<String> {
        val normalized = marks.replace(Normalizer.normalize(value, Normalizer.Form.NFD), "")
            .lowercase(Locale.ROOT).replace('đ', 'd')
        return words.findAll(normalized).map { it.value }.filter {
            it.length > 1 && it !in stopWords && !it.all(Char::isDigit)
        }.toList()
    }

    fun seedWeight(seed: RecommendationSeed, now: Long): Double {
        val days = ((now - seed.watchedAt).coerceAtLeast(0) / 86_400_000.0)
        return (1.0 + ln(seed.plays.coerceAtLeast(1).toDouble())) / (1.0 + days / 14.0)
    }

    fun rank(
        candidates: List<VideoItem>,
        seeds: List<RecommendationSeed>,
        relatedIds: Set<String> = emptySet(),
        now: Long = System.currentTimeMillis()
    ): List<VideoItem> {
        if (seeds.isEmpty()) return emptyList()
        val weights = mutableMapOf<String, Double>()
        val channels = mutableMapOf<String, Double>()
        seeds.forEach { seed ->
            val weight = seedWeight(seed, now)
            terms(seed.video.title).distinct().forEach { weights[it] = (weights[it] ?: 0.0) + weight }
            terms(seed.video.channelName).distinct().forEach { weights[it] = (weights[it] ?: 0.0) + weight * 0.6 }
            if (seed.video.channelName.isNotBlank()) {
                val channel = seed.video.channelName.lowercase(Locale.ROOT)
                channels[channel] = (channels[channel] ?: 0.0) + weight
            }
        }
        val watched = seeds.map { it.video.id }.toSet()
        val available = candidates.distinctBy { it.id }.filter { !it.isLive }
        val unseen = available.filter { it.id !in watched }
        // Small catalogs can be fully watched. Offer similar films to revisit in that
        // case, while keeping the most recently watched item out of this section.
        val eligible = unseen.ifEmpty { available.filter { it.id != seeds.first().video.id } }
        return eligible.map { candidate ->
            val tokens = terms(candidate.title + " " + candidate.channelName).distinct()
            val overlap = tokens.sumOf { weights[it] ?: 0.0 } / kotlin.math.sqrt(tokens.size.coerceAtLeast(1).toDouble())
            val channelScore = channels[candidate.channelName.lowercase(Locale.ROOT)] ?: 0.0
            candidate to overlap + channelScore * 1.3 + if (candidate.id in relatedIds) 0.7 else 0.0
        }.filter { it.second > 0.0 }.sortedByDescending { it.second }.let { scored ->
            val perChannel = mutableMapOf<String, Int>()
            scored.filter { (video, _) ->
                val key = video.channelName.lowercase(Locale.ROOT)
                val count = perChannel[key] ?: 0
                perChannel[key] = count + 1
                count < 3
            }.take(10).map { it.first }
        }
    }
}

data class PersonalizedResult(val videos: List<VideoItem>, val message: String = "")

@Singleton
class PersonalizedRecommendations @Inject constructor(
    dao: AeroPlayDao,
    private val openMedia: DemoLocalMediaSource,
    private val extractor: ExperimentalExtractorSource
) {
    val history = dao.observeWatchHistory()
    private data class CachedCandidates(val at: Long, val videos: List<VideoItem>, val relatedIds: Set<String>)
    private val cache = LinkedHashMap<String, CachedCandidates>()

    suspend fun recommendations(history: List<HistoryEntity>, source: ContentSourceType): PersonalizedResult =
        withContext(Dispatchers.Default) {
            val sourceHistory = history.filter {
                it.sourceTypeName == source.name || (source.isExperimental && it.sourceTypeName == ContentSourceType.DEVICE_LOCAL.name)
            }
            val seeds = sourceHistory.map { RecommendationSeed(it.toVideoItem(), it.playCount, it.watchedAtEpochMs) }
            if (seeds.isEmpty()) return@withContext PersonalizedResult(
                emptyList(), "Xem video trong nguồn này để AeroPlay tìm nội dung phù hợp với bạn."
            )
            if (!source.isExperimental) {
                val ranked = RecommendationRanking.rank(openMedia.getRecommendedVideos(), seeds)
                return@withContext PersonalizedResult(ranked,
                    if (ranked.isEmpty()) "Bạn đã xem các video tương tự trong danh mục Chuẩn Mở." else "")
            }
            val now = System.currentTimeMillis()
            val top = seeds.sortedByDescending { RecommendationRanking.seedWeight(it, now) }
                .distinctBy { it.video.channelName + RecommendationRanking.terms(it.video.title).take(4) }.take(2)
            val key = top.joinToString("|") { it.video.id }
            var pool = synchronized(cache) { cache[key]?.takeIf { now - it.at < 30 * 60_000 } }
            if (pool == null) {
                val candidates = mutableListOf<VideoItem>()
                val relatedIds = mutableSetOf<String>()
                val completed = withTimeoutOrNull(25_000L) {
                    for (seed in top) {
                        if (seed.video.sourceType.isExperimental) {
                            val related = extractor.extractVideoDetail(seed.video.url).getOrNull()?.relatedVideos.orEmpty()
                            candidates += related
                            relatedIds += related.map { it.id }
                        }
                        val titleTerms = RecommendationRanking.terms(seed.video.title).distinct().take(5)
                        if (titleTerms.isNotEmpty()) {
                            val query = titleTerms.joinToString(" ")
                            candidates += extractor.search(query).getOrNull()?.videos.orEmpty()
                        }
                    }
                    true
                }
                if (candidates.isEmpty()) return@withContext PersonalizedResult(
                    emptyList(), if (completed == null) "Tải gợi ý quá lâu. Kéo xuống để thử lại."
                    else "Chưa lấy được video tương tự. Kéo xuống để thử lại."
                )
                val loaded = CachedCandidates(now, candidates.distinctBy { it.id }, relatedIds)
                pool = loaded
                synchronized(cache) {
                    cache[key] = loaded
                    while (cache.size > 8) cache.remove(cache.keys.first())
                }
            }
            val ranked = RecommendationRanking.rank(pool.videos, seeds, pool.relatedIds)
            PersonalizedResult(ranked,
                if (ranked.isEmpty()) "Chưa có gợi ý mới từ lịch sử này. Hãy xem thêm vài video." else "")
        }
}
