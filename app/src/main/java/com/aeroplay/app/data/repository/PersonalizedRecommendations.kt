package com.aeroplay.app.data.repository

import com.aeroplay.app.core.database.AeroPlayDao
import com.aeroplay.app.core.database.BlockedRecommendationChannelEntity
import com.aeroplay.app.core.database.FavoriteEntity
import com.aeroplay.app.core.database.HistoryEntity
import com.aeroplay.app.core.database.RecommendationFeedbackEntity
import com.aeroplay.app.core.database.RecommendationImpressionEntity
import com.aeroplay.app.core.database.SearchHistoryEntity
import com.aeroplay.app.core.database.PlaylistVideoEntity
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.PlaylistItem
import com.aeroplay.app.core.model.VideoItem
import com.aeroplay.app.core.model.AutoMix
import com.aeroplay.app.core.network.SourceDiagnostics
import com.aeroplay.app.data.source.DemoLocalMediaSource
import com.aeroplay.app.data.source.ExperimentalExtractorSource
import com.aeroplay.app.data.youtube.YouTubePolicyFeatureFlags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln
import kotlin.math.sqrt

data class RecommendationSeed(val video: VideoItem, val plays: Int, val watchedAt: Long)

data class UserInterestProfile(
    val preferredTopics: Map<String, Double>,
    val preferredChannels: Map<String, Double>,
    val preferredSearchTerms: List<String>,
    val positiveInteractions: Set<String>,
    val negativeInteractions: Set<String>,
    val recentWatchHistory: Set<String>,
    val longTermInterests: Set<String>,
    val blockedChannels: Set<String>,
    val impressions: Map<String, RecommendationImpressionEntity>
)

/** Title/channel signals are calculated on-device. Only a few selected search terms leave the device. */
internal object RecommendationRanking {
    private val marks = Regex("\\p{M}+")
    private val words = Regex("[\\p{L}\\p{N}]+")
    private val stopWords = setOf(
        "the", "and", "for", "with", "from", "official", "video", "videos", "music", "lyrics",
        "lyric", "audio", "full", "hd", "4k", "1080p", "720p", "mv", "live", "feat", "ft",
        "remastered", "cua", "va", "la", "nhung", "mot", "cac", "nhac", "bai", "hat",
        "nhat", "moi", "hay", "nhieu", "ban", "phim", "open", "movie", "test", "clip", "vietnam"
    )

    fun normalize(value: String): String = marks.replace(Normalizer.normalize(value, Normalizer.Form.NFD), "")
        .lowercase(Locale.ROOT).replace('đ', 'd').replace(Regex("\\s+"), " ").trim()

    fun terms(value: String): List<String> = words.findAll(normalize(value)).map { it.value }.filter {
        it.length > 1 && it !in stopWords && !it.all(Char::isDigit)
    }.toList()

    fun seedWeight(seed: RecommendationSeed, now: Long): Double {
        val days = ((now - seed.watchedAt).coerceAtLeast(0) / 86_400_000.0)
        return (1.0 + ln(seed.plays.coerceAtLeast(1).toDouble())) / (1.0 + days / 30.0)
    }

    fun buildProfile(
        history: List<HistoryEntity>,
        favorites: List<FavoriteEntity>,
        playlists: List<PlaylistVideoEntity>,
        searches: List<SearchHistoryEntity>,
        feedback: List<RecommendationFeedbackEntity>,
        blockedChannels: List<BlockedRecommendationChannelEntity>,
        impressions: List<RecommendationImpressionEntity>,
        now: Long = System.currentTimeMillis()
    ): UserInterestProfile {
        val topicWeights = mutableMapOf<String, Double>()
        val channelWeights = mutableMapOf<String, Double>()
        val recent = mutableSetOf<String>()
        val longTerm = mutableSetOf<String>()
        history.forEach { item ->
            val ageDays = ((now - item.watchedAtEpochMs).coerceAtLeast(0L) / 86_400_000.0)
            val completion = if (item.totalDurationMs > 0L) (item.totalWatchedMs.toDouble() / item.totalDurationMs).coerceIn(0.0, 1.0) else 0.2
            val weight = ((1.0 + ln(item.playCount.coerceAtLeast(1).toDouble())) * (0.5 + completion) + item.completedPlayCount * 0.45) /
                (1.0 + ageDays / 45.0)
            val key = stableKey(item.toVideoItem())
            if (ageDays <= 30.0) recent += key
            if (ageDays > 30.0) longTerm += terms(item.title)
            terms(item.title).forEach { topicWeights[it] = (topicWeights[it] ?: 0.0) + weight }
            terms(item.channelName).forEach { topicWeights[it] = (topicWeights[it] ?: 0.0) + weight * 0.5 }
            val channel = normalize(item.channelName)
            if (channel.isNotBlank()) channelWeights[channel] = (channelWeights[channel] ?: 0.0) + weight
        }
        favorites.forEach { item ->
            val weight = 1.6
            terms(item.title).forEach { topicWeights[it] = (topicWeights[it] ?: 0.0) + weight }
            val channel = normalize(item.channelName)
            if (channel.isNotBlank()) channelWeights[channel] = (channelWeights[channel] ?: 0.0) + weight
        }
        playlists.forEach { item ->
            terms(item.title).forEach { topicWeights[it] = (topicWeights[it] ?: 0.0) + 1.25 }
            val channel = normalize(item.channelName)
            if (channel.isNotBlank()) channelWeights[channel] = (channelWeights[channel] ?: 0.0) + 1.25
        }
        val searchTerms = searches.sortedByDescending { it.searchedAtEpochMs }.take(12).map { it.query }.distinct()
        searchTerms.forEachIndexed { index, query ->
            val recencyWeight = 1.25 / (1.0 + index * 0.12)
            terms(query).forEach { topicWeights[it] = (topicWeights[it] ?: 0.0) + recencyWeight }
        }
        val negatives = feedback.filter { it.feedbackType == NOT_INTERESTED || it.feedbackType == MARKED_WATCHED }
            .map { it.videoId }.toSet()
        val positives = (favorites.map { stableKey(it.toVideoItem()) } + playlists.map { stableKey(it.toVideoItem()) }).toSet()
        return UserInterestProfile(
            preferredTopics = topicWeights.toList().sortedByDescending { it.second }.take(24).toMap(),
            preferredChannels = channelWeights,
            preferredSearchTerms = searchTerms,
            positiveInteractions = positives,
            negativeInteractions = negatives,
            recentWatchHistory = recent,
            longTermInterests = longTerm,
            blockedChannels = blockedChannels.map { it.normalizedChannelName }.toSet(),
            impressions = impressions.associateBy { it.videoId }
        )
    }

    fun rank(
        candidates: List<VideoItem>,
        seeds: List<RecommendationSeed>,
        relatedIds: Set<String> = emptySet(),
        now: Long = System.currentTimeMillis()
    ): List<VideoItem> {
        val baseProfile = buildProfile(seeds.map { seed ->
            HistoryEntity(
                videoId = seed.video.id, url = seed.video.url, title = seed.video.title,
                channelName = seed.video.channelName, channelUrl = seed.video.channelUrl,
                channelAvatarUrl = seed.video.channelAvatarUrl, thumbnailUrl = seed.video.thumbnailUrl,
                durationSeconds = seed.video.durationSeconds, viewCount = seed.video.viewCount,
                uploadedText = seed.video.uploadedText, sourceTypeName = seed.video.sourceType.name,
                watchedAtEpochMs = seed.watchedAt, playCount = seed.plays
            )
        }, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), now)
        return rankForProfile(candidates, baseProfile, relatedIds, now)
    }

    fun rankForProfile(
        candidates: List<VideoItem>,
        profile: UserInterestProfile,
        relatedIds: Set<String> = emptySet(),
        now: Long = System.currentTimeMillis()
    ): List<VideoItem> {
        val available = candidates.distinctBy(::stableKey).filterNot { it.isLive }
        val unseen = available.filterNot { stableKey(it) in profile.recentWatchHistory }
        val eligible = unseen.ifEmpty {
            val newestSeen = profile.recentWatchHistory.firstOrNull()
            available.filterNot { stableKey(it) == newestSeen }
        }
        val maximumTopic = profile.preferredTopics.values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
        val channelMaximum = profile.preferredChannels.values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
        val scored = eligible.mapNotNull { candidate ->
            val key = stableKey(candidate)
            val channelKey = normalize(candidate.channelName)
            if (key in profile.negativeInteractions || channelKey in profile.blockedChannels) return@mapNotNull null
            val candidateTerms = terms(candidate.title + " " + candidate.channelName).distinct()
            val topical = candidateTerms.sumOf { (profile.preferredTopics[it] ?: 0.0) / maximumTopic }.let {
                (it / sqrt(candidateTerms.size.coerceAtLeast(1).toDouble())).coerceIn(0.0, 4.0)
            }
            val channel = ((profile.preferredChannels[channelKey] ?: 0.0) / channelMaximum).coerceIn(0.0, 1.0)
            val directRelated = if (candidate.id in relatedIds) 1.4 else 0.0
            val favoriteBoost = if (key in profile.positiveInteractions) 0.8 else 0.0
            val seenRecentlyPenalty = if (key in profile.recentWatchHistory) 1.5 else 0.0
            val impression = profile.impressions[key] ?: profile.impressions[candidate.id]
            val unseenImpressionPenalty = if (impression != null && impression.clickCount == 0 && impression.impressionCount >= 3) 0.45 * impression.impressionCount else 0.0
            val ageDays = candidate.publishedAtEpochMs?.let { ((now - it).coerceAtLeast(0L) / 86_400_000.0) }
                ?: candidate.uploadedText.let(::estimatedAgeDays)
            val freshness = ageDays?.let { (1.0 / (1.0 + it / 90.0)).coerceAtMost(0.8) } ?: 0.0
            val popularity = if (candidate.viewCount > 0L) (ln(candidate.viewCount + 1.0) / 50.0).coerceAtMost(0.6) else 0.0
            val score = topical + channel * 1.35 + directRelated + favoriteBoost + freshness + popularity - seenRecentlyPenalty - unseenImpressionPenalty
            if (score <= 0.0) null else candidate to score
        }.sortedByDescending { it.second }

        val perChannel = mutableMapOf<String, Int>()
        val selected = mutableListOf<VideoItem>()
        val topicCounts = mutableMapOf<String, Int>()
        for ((video, _) in scored) {
            val channel = normalize(video.channelName)
            val count = perChannel[channel] ?: 0
            val signature = terms(video.title).firstOrNull().orEmpty()
            val topicCount = topicCounts[signature] ?: 0
            if (count >= 2 || (signature.isNotBlank() && topicCount >= 6)) continue
            selected += video
            perChannel[channel] = count + 1
            if (signature.isNotBlank()) topicCounts[signature] = topicCount + 1
            if (selected.size >= 10) break
        }
        SourceDiagnostics.event("recommendation.rank", "candidates=${available.size} returned=${selected.size} blocked=${profile.blockedChannels.size} negative=${profile.negativeInteractions.size}")
        return selected
    }

    private fun estimatedAgeDays(uploadedText: String): Double? {
        val text = normalize(uploadedText)
        val match = Regex("(\\d+(?:[.,]\\d+)?)\\s*(day|days|week|weeks|month|months|year|years|ngay|tuan|thang|nam)").find(text) ?: return null
        val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val unit = match.groupValues[2]
        val scale = when {
            unit.startsWith("week") || unit == "tuan" -> 7.0
            unit.startsWith("month") || unit == "thang" -> 30.0
            unit.startsWith("year") || unit == "nam" -> 365.0
            else -> 1.0
        }
        return amount * scale
    }

    fun stableKey(video: VideoItem): String = "${video.sourceType.name}:${video.id}"

    const val NOT_INTERESTED = "NOT_INTERESTED"
    const val BLOCK_CHANNEL = "BLOCK_CHANNEL"
    const val MARKED_WATCHED = "MARKED_WATCHED"
}

data class PersonalizedResult(
    val videos: List<VideoItem>,
    val message: String = "",
    val musicVideos: List<VideoItem> = emptyList(),
    val popularMusicVideos: List<VideoItem> = emptyList(),
    val musicPlaylists: List<PlaylistItem> = emptyList(),
    val hasMusicStyleContext: Boolean = false,
    val autoMixes: List<AutoMix> = emptyList()
)

@Singleton
class PersonalizedRecommendations @Inject constructor(
    private val dao: AeroPlayDao,
    private val openMedia: DemoLocalMediaSource,
    private val extractor: ExperimentalExtractorSource
) {
    val history = dao.observeWatchHistory()
    val favorites: Flow<List<FavoriteEntity>> = dao.observeFavorites()
    val searchHistory: Flow<List<SearchHistoryEntity>> = dao.observeSearchHistory()
    val playlistVideos: Flow<List<PlaylistVideoEntity>> = dao.observeAllPlaylistVideos()

    private data class CachedCandidates(
        val at: Long,
        val videos: List<VideoItem>,
        val musicVideos: List<VideoItem>,
        val playlists: List<PlaylistItem>,
        val relatedIds: Set<String>,
        val sourceSummary: String
    )
    private val cache = LinkedHashMap<String, CachedCandidates>()

    suspend fun recommendations(
        history: List<HistoryEntity>,
        favorites: List<FavoriteEntity>,
        playlists: List<PlaylistVideoEntity>,
        searches: List<SearchHistoryEntity>,
        source: ContentSourceType,
        currentVideo: VideoItem? = null,
        currentDescription: String = "",
        currentIsMusic: Boolean = false
    ): PersonalizedResult = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        val feedback = dao.getRecommendationFeedback()
        val blocked = dao.getBlockedRecommendationChannels()
        val impressions = dao.getRecommendationImpressions()
        val profile = RecommendationRanking.buildProfile(history, favorites, playlists, searches, feedback, blocked, impressions, now)
        fun allowed(video: VideoItem): Boolean =
            video.sourceType == source && RecommendationRanking.stableKey(video) !in profile.negativeInteractions &&
                RecommendationRanking.normalize(video.channelName) !in profile.blockedChannels
        val preferred = (favorites.map { it.toVideoItem() } + history.map { it.toVideoItem() } + playlists.map { it.toVideoItem() })
            .filter(::allowed)
        if (source == ContentSourceType.EXPERIMENTAL_EXTRACTOR && !YouTubePolicyFeatureFlags.allowNewPipeYouTube) {
            val allowed = RecommendationRanking.rankForProfile(openMedia.getRecommendedVideos(), profile, now = now)
            return@withContext PersonalizedResult(
                videos = allowed,
                message = if (allowed.isEmpty()) "Nguồn NewPipe đã bị tắt trong chế độ YouTube API chỉ đọc." else ""
            )
        }
        val sourceHistory = history.filter {
            it.sourceTypeName == source.name || (source.isExperimental && it.sourceTypeName == ContentSourceType.DEVICE_LOCAL.name)
        }
        val sourceFavorites = favorites.filter {
            it.sourceTypeName == source.name || (source.isExperimental && it.sourceTypeName == ContentSourceType.DEVICE_LOCAL.name)
        }
        val seeds = sourceHistory.map { RecommendationSeed(it.toVideoItem(), it.playCount, it.watchedAtEpochMs) } +
            sourceFavorites.map { RecommendationSeed(it.toVideoItem(), 1, it.addedAtEpochMs) }
        val musicSeed = (listOfNotNull(currentVideo?.takeIf { it.sourceType == source && (currentIsMusic ||
            MusicStyleAnalyzer.analyze(it.title, it.channelName, currentDescription).isLikelyMusic) }) +
            seeds.sortedByDescending { RecommendationRanking.seedWeight(it, now) }.map { it.video })
            .firstOrNull { (currentIsMusic && it.id == currentVideo?.id) ||
                MusicStyleAnalyzer.analyze(it.title, it.channelName,
                    if (it.id == currentVideo?.id) currentDescription else "").isLikelyMusic }
            ?: currentVideo?.takeIf { it.sourceType == source && currentIsMusic }
        val musicDescription = currentDescription.takeIf { musicSeed?.id == currentVideo?.id }.orEmpty()
        val inferredStyle = musicSeed?.let { MusicStyleAnalyzer.analyze(it.title, it.channelName, musicDescription) }
        val musicStyle = if (currentIsMusic && musicSeed?.id == currentVideo?.id && inferredStyle?.isLikelyMusic == false) {
            inferredStyle.copy(isLikelyMusic = true, confidence = 0.4)
        } else inferredStyle
        val vietnameseMusicContext = musicSeed?.let {
            MusicStyleAnalyzer.isVietnameseContext(it.title, it.channelName, musicDescription)
        } ?: true
        val musicQuery = when {
            !source.isExperimental -> null
            musicStyle == null -> "nhac viet official mv"
            musicStyle.genres.isNotEmpty() -> musicStyle.genres.first().let { genre ->
                if (vietnameseMusicContext) "$genre nhac viet official audio" else "$genre music official audio"
            }
            vietnameseMusicContext -> "nhac viet official mv"
            else -> "popular songs official music video"
        }
        val queries = (listOfNotNull(musicQuery) + profile.preferredSearchTerms + profile.preferredTopics.keys.take(4))
            .map(String::trim).filter { it.length >= 2 }.distinct().take(2)

        if (!source.isExperimental) {
            val catalog = openMedia.getRecommendedVideos().filter(::allowed)
            val ranked = RecommendationRanking.rankForProfile(catalog, profile, now = now)
            return@withContext PersonalizedResult(
                ranked,
                when {
                    ranked.isNotEmpty() -> ""
                    seeds.isEmpty() && queries.isEmpty() -> "Xem video hoặc tìm chủ đề bạn thích để bắt đầu cá nhân hóa."
                    else -> "Chưa có nội dung tương tự trong danh mục Chuẩn Mở."
                },
                autoMixes = AutoMixPlanner.build(catalog, source, preferred = preferred)
            )
        }

        if (seeds.isEmpty() && queries.isEmpty()) return@withContext PersonalizedResult(
            emptyList(), "Xem video hoặc tìm chủ đề bạn thích để AeroPlay bắt đầu cá nhân hóa."
        )
        val topSeeds = seeds.sortedByDescending { RecommendationRanking.seedWeight(it, now) }
            .let { ordered -> (listOfNotNull(currentVideo?.takeIf { it.sourceType == source }?.let { RecommendationSeed(it, 1, now) }) + ordered) }
            .distinctBy { RecommendationRanking.stableKey(it.video) }.take(2)
        val key = (topSeeds.map { RecommendationRanking.stableKey(it.video) } + queries).joinToString("|")
        var pool = synchronized(cache) { cache[key]?.takeIf { now - it.at < CACHE_TTL_MS } }
        if (pool == null) {
            val candidates = mutableListOf<VideoItem>()
            val musicCandidates = mutableListOf<VideoItem>()
            val playlistCandidates = mutableListOf<PlaylistItem>()
            val relatedIds = mutableSetOf<String>()
            val sourceNames = mutableListOf<String>()
            val completed = withTimeoutOrNull(25_000L) {
                topSeeds.forEach { seed ->
                    val result = extractor.extractVideoDetail(seed.video.url)
                    val related = result.getOrNull()?.relatedVideos.orEmpty()
                    if (related.isNotEmpty()) {
                        candidates += related
                        relatedIds += related.map { it.id }
                        sourceNames += "related:${seed.video.id}"
                    }
                }
                queries.forEach { query ->
                    val result = extractor.search(query).getOrNull()
                    val found = result?.videos.orEmpty()
                    candidates += found
                    if (query == musicQuery) {
                        musicCandidates += found
                        playlistCandidates += result?.playlists.orEmpty()
                    }
                    if (found.isNotEmpty()) sourceNames += "search:${query.take(24)}"
                }
                true
            }
            if (completed == null) SourceDiagnostics.event("recommendation.candidates", "timeout=true seedCount=${topSeeds.size} queryCount=${queries.size}")
            if (candidates.isEmpty() && playlistCandidates.isEmpty()) return@withContext PersonalizedResult(
                emptyList(), if (completed == null) "Tải gợi ý quá lâu. Kéo xuống để thử lại."
                else "Chưa lấy được video liên quan từ nguồn hiện tại.",
                autoMixes = AutoMixPlanner.build(preferred, source, musicSeed?.takeIf(::allowed), musicDescription)
            )
            val loaded = CachedCandidates(
                now,
                candidates.distinctBy(RecommendationRanking::stableKey),
                musicCandidates.distinctBy(RecommendationRanking::stableKey),
                playlistCandidates.distinctBy { it.url.ifBlank { it.id } },
                relatedIds,
                sourceNames.distinct().joinToString(",")
            )
            pool = loaded
            synchronized(cache) {
                cache[key] = loaded
                while (cache.size > MAX_CACHED_PROFILES) cache.remove(cache.keys.first())
            }
            SourceDiagnostics.event("recommendation.candidates", "cache=miss sources=${loaded.sourceSummary} candidates=${loaded.videos.size}")
        } else {
            SourceDiagnostics.event("recommendation.candidates", "cache=hit candidates=${pool.videos.size}")
        }
        val ranked = RecommendationRanking.rankForProfile(pool.videos, profile, pool.relatedIds, now)
        val musicPool = (pool.musicVideos + if (musicSeed != null) pool.videos else emptyList())
            .distinctBy(RecommendationRanking::stableKey)
            .filterNot { candidate ->
                RecommendationRanking.stableKey(candidate) in profile.negativeInteractions ||
                    RecommendationRanking.normalize(candidate.channelName) in profile.blockedChannels
            }
        val styleVideos = musicSeed?.let { seed -> MusicRecommendationRanker.rank(musicPool, seed,
            currentDescription = musicDescription,
            forceMusic = currentIsMusic && seed.id == currentVideo?.id) }
            ?: MusicRecommendationRanker.diversify(musicPool)
        val popularVideos = MusicRecommendationRanker.popular(
            styleVideos, fromMusicQuery = true
        )
        val styleGenres = musicSeed?.let { MusicStyleAnalyzer.analyze(it.title, it.channelName, musicDescription).genres }.orEmpty()
        val stylePlaylists = pool.playlists.asSequence()
            .filter { it.streamCount != 0L && it.url.isNotBlank() }
            .filter { playlist ->
                val text = RecommendationRanking.normalize("${playlist.title} ${playlist.description}")
                styleGenres.isEmpty() || styleGenres.any { it in text } ||
                    text.contains("nhac") || text.contains("music") || text.contains("mix")
            }
            .distinctBy { it.url.ifBlank { it.id } }
            .take(8)
            .toList()
        PersonalizedResult(
            videos = ranked,
            message = if (ranked.isEmpty()) "Chưa có gợi ý mới từ lịch sử và sở thích này." else "",
            musicVideos = styleVideos.take(10),
            popularMusicVideos = popularVideos,
            musicPlaylists = stylePlaylists,
            hasMusicStyleContext = musicSeed != null,
            autoMixes = AutoMixPlanner.build(
                candidates = (styleVideos + pool.musicVideos + pool.videos).filter(::allowed),
                source = source,
                seed = musicSeed?.takeIf(::allowed),
                seedDescription = musicDescription,
                fromMusicSearch = pool.musicVideos.filter(::allowed).map(RecommendationRanking::stableKey).toSet(),
                preferred = preferred
            )
        )
    }

    suspend fun markNotInterested(video: VideoItem) {
        dao.upsertRecommendationFeedback(RecommendationFeedbackEntity(
            RecommendationRanking.stableKey(video), RecommendationRanking.NOT_INTERESTED, video.channelName
        ))
        clearCandidateCache()
    }

    suspend fun blockChannel(video: VideoItem) {
        val normalized = RecommendationRanking.normalize(video.channelName)
        if (normalized.isBlank()) return
        dao.blockRecommendationChannel(BlockedRecommendationChannelEntity(normalized, video.channelName))
        dao.upsertRecommendationFeedback(RecommendationFeedbackEntity(
            RecommendationRanking.stableKey(video), RecommendationRanking.BLOCK_CHANNEL, video.channelName
        ))
        clearCandidateCache()
    }

    suspend fun markWatched(video: VideoItem) {
        dao.upsertRecommendationFeedback(RecommendationFeedbackEntity(
            RecommendationRanking.stableKey(video), RecommendationRanking.MARKED_WATCHED, video.channelName
        ))
        clearCandidateCache()
    }

    suspend fun recordImpression(video: VideoItem) {
        val key = RecommendationRanking.stableKey(video)
        val now = System.currentTimeMillis()
        val previous = dao.getRecommendationImpression(key)
        if (previous != null && now - previous.lastImpressedAtEpochMs < IMPRESSION_COOLDOWN_MS) return
        dao.upsertRecommendationImpression(RecommendationImpressionEntity(
            videoId = key,
            channelName = video.channelName,
            impressionCount = (previous?.impressionCount ?: 0) + 1,
            clickCount = previous?.clickCount ?: 0,
            lastImpressedAtEpochMs = now,
            lastClickedAtEpochMs = previous?.lastClickedAtEpochMs ?: 0L
        ))
    }

    suspend fun recordClick(video: VideoItem) {
        val key = RecommendationRanking.stableKey(video)
        val previous = dao.getRecommendationImpression(key)
        val now = System.currentTimeMillis()
        dao.upsertRecommendationImpression(RecommendationImpressionEntity(
            videoId = key,
            channelName = video.channelName,
            impressionCount = previous?.impressionCount ?: 0,
            clickCount = (previous?.clickCount ?: 0) + 1,
            lastImpressedAtEpochMs = previous?.lastImpressedAtEpochMs ?: 0L,
            lastClickedAtEpochMs = now
        ))
    }

    suspend fun resetPersonalization() {
        dao.clearPersonalizationSignals()
        clearCandidateCache()
    }

    private fun clearCandidateCache() = synchronized(cache) { cache.clear() }

    private companion object {
        const val CACHE_TTL_MS = 20 * 60_000L
        const val IMPRESSION_COOLDOWN_MS = 60 * 60_000L
        const val MAX_CACHED_PROFILES = 8
    }
}
