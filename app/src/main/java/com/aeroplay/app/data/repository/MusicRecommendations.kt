package com.aeroplay.app.data.repository

import com.aeroplay.app.core.database.AeroPlayDao
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.VideoItem
import com.aeroplay.app.data.source.ExperimentalExtractorSource
import com.aeroplay.app.data.youtube.YouTubePolicyFeatureFlags
import kotlinx.coroutines.withTimeoutOrNull
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln

/** Conservative title normalization used to recognize alternate uploads of one song. */
object SongIdentityResolver {
    private val decorations = setOf(
        "official", "video", "music", "mv", "audio", "lyrics", "lyric", "visualizer",
        "cover", "karaoke", "remastered", "remaster", "hd", "4k", "full", "version",
        "sped", "up", "slowed", "reverb", "live", "performance", "sub", "vietsub",
        "feat", "ft", "prod", "by", "nhac", "bai", "hat", "ca", "khuc", "loi",
        "viet", "nam", "officially"
    )
    private val tokens = Regex("[\\p{L}\\p{N}]+")

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace('đ', 'd')
        .replace(Regex("\\b(feat|ft)\\.?\\b.*$"), " ")
        .replace(Regex("\\b(official|lyrics?|audio|visualizer|remaster(ed)?|cover|karaoke|live|hd|4k|1080p|720p|mv|music video|sped up|slowed( and reverb)?)\\b"), " ")
        .replace(Regex("\\b(nhac|bai hat|loi bai hat|vietsub|official mv)\\b"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim().replace(Regex("\\s+"), " ")

    fun key(title: String): Set<String> = tokens.findAll(normalize(title))
        .map { it.value }
        .filter { it.length > 1 && it !in decorations && !it.all(Char::isDigit) }
        .toSet()

    fun likelySameSong(first: String, second: String): Boolean {
        val left = key(first)
        val right = key(second)
        if (left.isEmpty() || right.isEmpty()) return false
        if (minOf(left.size, right.size) < 2) return false
        if (left == right) return true
        val overlap = left.intersect(right).size.toDouble()
        val smallerCoverage = overlap / minOf(left.size, right.size)
        val unionCoverage = overlap / left.union(right).size
        return smallerCoverage >= 0.8 && unionCoverage >= 0.55
    }
}

data class MusicStyleProfile(
    val genres: Set<String>,
    val isLikelyMusic: Boolean,
    val confidence: Double
)

object MusicStyleAnalyzer {
    private val genreTerms = mapOf(
        "ballad" to setOf("ballad", "ballade"),
        "acoustic" to setOf("acoustic", "unplugged", "moc mac"),
        "bolero" to setOf("bolero", "nhac vang", "nhac tru tinh"),
        "lofi" to setOf("lofi", "lo fi", "chillhop"),
        "edm" to setOf("edm", "electronic", "electro", "house", "techno", "dance"),
        "remix" to setOf("remix", "remixed", "vinahouse"),
        "rap" to setOf("rap", "hip hop", "hiphop", "rap viet"),
        "rnb" to setOf("r b", "rnb", "soul"),
        "rock" to setOf("rock", "metal", "punk"),
        "pop" to setOf("pop", "nhac tre", "v pop", "k pop", "kpop", "j pop", "jpop"),
        "classical" to setOf("classical", "orchestra", "symphony", "piano solo"),
        "instrumental" to setOf("instrumental", "no vocals", "nhac khong loi")
    )
    private val musicSignals = setOf(
        "official mv", "music video", "official audio", "audio", "mv", "lyrics", "lyric video", "karaoke",
        "cover", "nhac", "am nhac", "bai hat", "ca khuc", "music", "song", "single", "album",
        "remix", "instrumental", "vietsub"
    )

    private fun contextText(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace('đ', 'd')
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim().replace(Regex("\\s+"), " ")

    fun analyze(title: String, channel: String = "", description: String = ""): MusicStyleProfile {
        val normalizedTitle = " ${contextText(title)} "
        val normalizedAll = " ${contextText("$title $channel $description")} "
        val genres = genreTerms.filterValues { terms -> terms.any { " $it " in normalizedAll } }.keys
        val signalCount = musicSignals.count { " $it " in normalizedTitle }
        val likelyMusic = genres.isNotEmpty() || signalCount > 0
        val confidence = when {
            genres.isNotEmpty() && signalCount > 0 -> 0.95
            genres.isNotEmpty() -> 0.78
            signalCount >= 2 -> 0.72
            signalCount == 1 -> 0.55
            else -> 0.0
        }
        return MusicStyleProfile(genres, likelyMusic, confidence)
    }

    fun isVietnameseContext(title: String, channel: String = "", description: String = ""): Boolean {
        val original = "$title $channel $description"
        val normalized = " ${contextText(original)} "
        return original.any { it in "ăâđêôơưĂÂĐÊÔƠƯ" } ||
            listOf("viet", "vietnam", "tieng viet", "nhac viet", "v pop").any { " $it " in normalized }
    }
}

/** Orders source-provided candidates while favoring other songs over alternate uploads. */
object MusicRecommendationRanker {
    private val tokens = Regex("[\\p{L}\\p{N}]+")
    private val genericMusicWords = setOf(
        "official", "video", "music", "mv", "audio", "lyrics", "lyric", "full", "hd", "4k",
        "cover", "karaoke", "remix", "live", "version", "song", "songs", "nhac", "bai", "hat",
        "ca", "khuc", "loi", "viet", "nam", "the", "and", "with", "for", "feat", "ft"
    )

    fun rank(
        candidates: List<VideoItem>,
        current: VideoItem,
        limit: Int = 30,
        currentDescription: String = "",
        forceMusic: Boolean = false
    ): List<VideoItem> {
        val analyzedStyle = MusicStyleAnalyzer.analyze(current.title, current.channelName, currentDescription)
        val seedStyle = if (forceMusic && !analyzedStyle.isLikelyMusic) {
            analyzedStyle.copy(isLikelyMusic = true, confidence = 0.4)
        } else analyzedStyle
        val seedTerms = musicStyleTerms(current.title)
        val currentArtist = SongIdentityResolver.normalize(current.channelName)
        val eligible = candidates
            .distinctBy { "${it.sourceType.name}:${it.id}" }
            .filterNot { it.id == current.id || it.isLive }
            .filterNot { SongIdentityResolver.likelySameSong(current.title, it.title) }
            .mapIndexedNotNull { sourcePosition, video ->
                val titleStyle = MusicStyleAnalyzer.analyze(video.title, video.channelName)
                val titleTerms = musicStyleTerms(video.title)
                val matchingGenres = seedStyle.genres.intersect(titleStyle.genres).size
                val genreScore = if (seedStyle.genres.isEmpty()) 0.0 else
                    matchingGenres.toDouble() / seedStyle.genres.size.coerceAtLeast(1)
                val titleSimilarity = if (seedTerms.isEmpty() || titleTerms.isEmpty()) 0.0 else {
                    seedTerms.intersect(titleTerms).size.toDouble() / seedTerms.union(titleTerms).size
                }
                val sameChannel = currentArtist.isNotBlank() &&
                    SongIdentityResolver.normalize(video.channelName) == currentArtist
                val popularity = video.viewCount.takeIf { it > 0L }?.let {
                    (ln(it.toDouble() + 1.0) / 50.0).coerceAtMost(0.65)
                } ?: 0.0
                val channelSignal = if (sameChannel) 0.06 else 0.0
                val sourceOrder = sourcePosition * 0.003

                // Music-to-music results favor genre and diversity; general video results retain
                // topical relevance without being mislabeled as music recommendations.
                val score = if (seedStyle.isLikelyMusic) {
                    val musicSignal = if (titleStyle.isLikelyMusic) 0.45 else 0.0
                    genreScore * 2.2 + musicSignal + popularity * 0.04 - titleSimilarity * 0.30 +
                        channelSignal - sourceOrder
                } else {
                    titleSimilarity * 1.8 + popularity * 0.02 + channelSignal - sourceOrder
                }
                // Related items from a music video's own source often have no "music" label in
                // their titles. Keep those as lower-confidence candidates when no explicit genre
                // exists, then let source ordering, popularity and diversity decide.
                if (seedStyle.isLikelyMusic && seedStyle.genres.isNotEmpty() &&
                    titleStyle.genres.isNotEmpty() && matchingGenres == 0
                ) null else Triple(video, score, sourcePosition)
            }
            .sortedWith(compareByDescending<Triple<VideoItem, Double, Int>> { it.second }
                .thenBy { it.third }
                .thenByDescending { it.first.viewCount.coerceAtLeast(0L) })

        val selected = mutableListOf<VideoItem>()
        val usedSongs = mutableListOf<String>()
        val channelCounts = mutableMapOf<String, Int>()
        for ((video, _, _) in eligible) {
            if (usedSongs.any { SongIdentityResolver.likelySameSong(it, video.title) }) continue
            val channel = SongIdentityResolver.normalize(video.channelName)
            if (channel.isNotBlank() && (channelCounts[channel] ?: 0) >= 2) continue
            selected += video
            usedSongs += video.title
            if (channel.isNotBlank()) channelCounts[channel] = (channelCounts[channel] ?: 0) + 1
            if (selected.size >= limit) break
        }
        return selected
    }

    fun popular(
        candidates: List<VideoItem>,
        current: VideoItem? = null,
        limit: Int = 10,
        currentDescription: String = "",
        fromMusicQuery: Boolean = false
    ): List<VideoItem> {
        val filtered = if (current != null) rank(candidates, current, limit = Int.MAX_VALUE,
            currentDescription = currentDescription) else
            candidates.filter { fromMusicQuery || MusicStyleAnalyzer.analyze(it.title, it.channelName).isLikelyMusic }
        val diverse = if (current != null) filtered else
            diversify(filtered.filterNot { it.isLive }, limit = Int.MAX_VALUE)
        return diverse.sortedByDescending { it.viewCount.coerceAtLeast(0L) }.take(limit)
    }

    fun diversify(candidates: List<VideoItem>, limit: Int = 10): List<VideoItem> {
        val result = mutableListOf<VideoItem>()
        val songs = mutableListOf<String>()
        val channels = mutableMapOf<String, Int>()
        candidates.distinctBy { "${it.sourceType.name}:${it.id}" }.forEach { video ->
            if (video.isLive || songs.any { SongIdentityResolver.likelySameSong(it, video.title) }) return@forEach
            val channel = SongIdentityResolver.normalize(video.channelName)
            if (channel.isNotBlank() && (channels[channel] ?: 0) >= 2) return@forEach
            result += video
            songs += video.title
            if (channel.isNotBlank()) channels[channel] = (channels[channel] ?: 0) + 1
        }
        return result.take(limit)
    }

    private fun musicStyleTerms(title: String): Set<String> = tokens.findAll(SongIdentityResolver.normalize(title))
        .map { it.value }.filter { it.length > 1 && it !in genericMusicWords }.toSet()
}

@Singleton
class MusicSuggestionRepository @Inject constructor(
    private val extractorSource: ExperimentalExtractorSource,
    private val dao: AeroPlayDao
) {
    val restrictionChanges = dao.observeContentRestrictions()

    suspend fun filterPreferences(videos: List<VideoItem>): List<VideoItem> {
        val excluded = dao.getRecommendationFeedback().filter {
            it.feedbackType == RecommendationRanking.NOT_INTERESTED || it.feedbackType == RecommendationRanking.MARKED_WATCHED
        }.map { it.videoId }.toSet()
        val blocked = dao.getBlockedRecommendationChannels().map { it.normalizedChannelName }.toSet()
        return ContentRestrictionPolicy.apply(videos.filterNot {
            RecommendationRanking.stableKey(it) in excluded || RecommendationRanking.normalize(it.channelName) in blocked
        }, dao.getContentRestrictions())
    }
    private data class Cached(val createdAt: Long, val videos: List<VideoItem>)

    private val cache = LinkedHashMap<String, Cached>()

    suspend fun suggestions(
        current: VideoItem,
        description: String,
        related: List<VideoItem>,
        limit: Int = 30
    ): List<VideoItem> {
        val excludedIds = dao.getRecommendationFeedback()
            .filter { it.feedbackType == RecommendationRanking.NOT_INTERESTED ||
                it.feedbackType == RecommendationRanking.MARKED_WATCHED }
            .map { it.videoId }.toSet()
        val blockedChannels = dao.getBlockedRecommendationChannels()
            .map { it.normalizedChannelName }.toSet()
        fun isAllowed(video: VideoItem): Boolean =
            RecommendationRanking.stableKey(video) !in excludedIds &&
                RecommendationRanking.normalize(video.channelName) !in blockedChannels

        val style = MusicStyleAnalyzer.analyze(current.title, current.channelName, description)
        val permittedRelated = if (YouTubePolicyFeatureFlags.apiOnlyMode) {
            related.filter { it.sourceType != ContentSourceType.EXPERIMENTAL_EXTRACTOR }
        } else related
        val relatedRanked = MusicRecommendationRanker.rank(
            permittedRelated, current, limit, description, forceMusic = true
        ).filter(::isAllowed).let { filterPreferences(it) }
        if (YouTubePolicyFeatureFlags.apiOnlyMode) return relatedRanked
        if (!current.sourceType.isExperimental || relatedRanked.size >= 12) {
            return relatedRanked
        }

        val vietnameseContext = MusicStyleAnalyzer.isVietnameseContext(current.title, current.channelName, description)
        val query = style.genres.firstOrNull()?.let { genre ->
            if (vietnameseContext) "$genre nhac viet" else "$genre music songs"
        } ?: if (vietnameseContext) "nhac tre viet moi" else "popular songs music"
        val key = "${current.sourceType}:${current.id}:${query.lowercase(Locale.ROOT)}"
        val now = System.currentTimeMillis()
        val cached = synchronized(cache) { cache[key]?.takeIf { now - it.createdAt < CACHE_TTL_MS } }
        if (cached != null) return (relatedRanked + cached.videos)
            .distinctBy { "${it.sourceType}:${it.id}" }
            .let { MusicRecommendationRanker.rank(it, current, limit, description, forceMusic = true) }
            .filter(::isAllowed)
            .let { filterPreferences(it) }

        val searched = withTimeoutOrNull(12_000L) {
            extractorSource.search(query).getOrNull()?.videos.orEmpty()
        }.orEmpty()
        val ranked = MusicRecommendationRanker.rank(
            related + searched, current, limit, description, forceMusic = true
        )
        synchronized(cache) {
            cache[key] = Cached(now, ranked)
            while (cache.size > MAX_CACHE_ENTRIES) cache.remove(cache.keys.first())
        }
        return filterPreferences(ranked.filter(::isAllowed))
    }

    private companion object {
        const val CACHE_TTL_MS = 20 * 60_000L
        const val MAX_CACHE_ENTRIES = 32
    }
}
