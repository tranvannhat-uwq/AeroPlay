package com.aeroplay.app.feature.search

import com.aeroplay.app.core.model.VideoItem
import com.aeroplay.app.data.repository.MusicRecommendationRanker
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ln

enum class SearchIntent { GENERAL, RECENT, POPULAR, MUSIC, CHANNEL, PLAYLIST, EXACT_ENTITY }
enum class QueryLanguage { VIETNAMESE, ENGLISH, MIXED, UNKNOWN }
enum class SearchSortOrder(val label: String) {
    RELEVANCE("Phù hợp nhất"), RECENT("Mới nhất"), POPULAR("Phổ biến"), PERSONALIZED("Cá nhân hóa")
}
enum class SearchTimeRange(val label: String) {
    ANY("Mọi thời điểm"), TODAY("Hôm nay"), WEEK("Tuần này"), MONTH("Tháng này"), YEAR("Năm nay")
}
data class SearchQueryAnalysis(
    val originalQuery: String,
    val normalizedQuery: String,
    val tokens: List<String>,
    val intent: SearchIntent,
    val language: QueryLanguage,
    val isVietnameseContext: Boolean
)

object SearchQueryAnalyzer {
    private val tokenPattern = Regex("[\\p{L}\\p{N}]+")
    private val vietnameseIntentWords = setOf(
        "nhac", "bai", "hat", "tre", "remix", "hai", "viet", "nam", "review", "danh", "gia",
        "dien", "thoai", "game", "hom", "nay", "moi", "nhat", "pho", "bien", "thinh", "hanh",
        "kenh", "playlist", "danh", "sach", "phat", "tin", "tuc", "son", "chong", "tham"
    )
    private val recentWords = setOf("moi nhat", "hom nay", "gan day", "moi", "latest", "newest", "today", "recent")
    private val popularWords = setOf("pho bien", "thinh hanh", "trending", "popular", "most viewed", "nhieu view", "nhieu luot xem", "viral", "hot")
    private val musicWords = setOf("nhac", "bai hat", "remix", "music", "song", "lyrics", "audio", "mv")
    private val channelWords = setOf("kenh", "channel", "creator", "artist", "nghe si")
    private val playlistWords = setOf("playlist", "danh sach phat", "album")
    private val exactHints = setOf("iphone", "samsung", "google", "mrbeast", "taylor swift", "taylorswift", "son tung", "m tp")

    fun analyze(query: String): SearchQueryAnalysis {
        val trimmed = query.trim().replace(Regex("\\s+"), " ")
        val normalized = normalize(trimmed)
        val terms = tokenPattern.findAll(normalized).map { it.value }.toList()
        val intent = when {
            trimmed.contains('"') || trimmed.contains('\'') ||
                exactHints.any { normalized.contains(it) } ||
                Regex("[A-Z][a-z]+[A-Z][a-z]+").containsMatchIn(trimmed) -> SearchIntent.EXACT_ENTITY
            playlistWords.any(normalized::contains) -> SearchIntent.PLAYLIST
            channelWords.any(normalized::contains) -> SearchIntent.CHANNEL
            popularWords.any(normalized::contains) -> SearchIntent.POPULAR
            recentWords.any(normalized::contains) || Regex("\\b20\\d{2}\\b").containsMatchIn(trimmed) -> SearchIntent.RECENT
            musicWords.any(normalized::contains) -> SearchIntent.MUSIC
            else -> SearchIntent.GENERAL
        }
        val viTerms = terms.count { it in vietnameseIntentWords }
        val englishTerms = terms.count { it in englishWords }
        val language = when {
            viTerms > 0 && englishTerms > 0 -> QueryLanguage.MIXED
            viTerms > 0 || trimmed.any { it in "ăâđêôơư" } -> QueryLanguage.VIETNAMESE
            englishTerms > 0 -> QueryLanguage.ENGLISH
            else -> QueryLanguage.UNKNOWN
        }
        val vietnameseContext = language == QueryLanguage.VIETNAMESE || language == QueryLanguage.MIXED
        return SearchQueryAnalysis(trimmed, normalized, terms, intent, language, vietnameseContext)
    }

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace('đ', 'd').replace('Đ', 'd')
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim().replace(Regex("\\s+"), " ")

    private val englishWords = setOf(
        "latest", "newest", "today", "popular", "trending", "music", "song", "songs", "review",
        "phone", "mobile", "game", "games", "news", "channel", "playlist", "video", "best"
    )
}

data class SearchScoreBreakdown(
    val queryRelevance: Int,
    val languageRegion: Int,
    val freshness: Int?,
    val popularity: Int?,
    val personalRelevance: Int,
    val total: Int
)

data class RankedSearchVideo(val video: VideoItem, val score: SearchScoreBreakdown)

/** Ranks only candidates already returned by the active source. It never claims global YouTube order. */
object VideoSearchRanker {
    private val tokens = Regex("[\\p{L}\\p{N}]+")
    private val agePattern = Regex("(\\d+(?:[.,]\\d+)?)\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?|days?|weeks?|months?|years?|giay|phut|gio|ngay|tuan|thang|nam)", RegexOption.IGNORE_CASE)
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")

    fun rank(
        videos: List<VideoItem>,
        analysis: SearchQueryAnalysis,
        sort: SearchSortOrder = SearchSortOrder.RELEVANCE,
        timeRange: SearchTimeRange = SearchTimeRange.ANY,
        personalTerms: Set<String> = emptySet(),
        nowEpochMs: Long = System.currentTimeMillis()
    ): List<RankedSearchVideo> {
        val unique = videos.distinctBy { "${it.sourceType.name}:${it.id}" }
        val scored = unique.mapNotNull { video ->
            val item = score(video, analysis, unique, personalTerms, nowEpochMs)
            val publishedAt = publishedAt(video, nowEpochMs)
            if (!matchesTimeRange(publishedAt, timeRange, nowEpochMs)) return@mapNotNull null
            if (item.queryRelevance < MIN_QUERY_RELEVANCE) return@mapNotNull null
            RankedSearchVideo(video, item)
        }
        return when (sort) {
            SearchSortOrder.RECENT -> scored.sortedWith(compareByDescending<RankedSearchVideo> { publishedAt(it.video, nowEpochMs) ?: Long.MIN_VALUE }.thenByDescending { it.score.total })
            SearchSortOrder.POPULAR -> scored.sortedWith(compareByDescending<RankedSearchVideo> { it.video.viewCount.takeIf { count -> count >= 0L } ?: -1L }.thenByDescending { it.score.total })
            SearchSortOrder.PERSONALIZED -> scored.sortedWith(compareByDescending<RankedSearchVideo> { it.score.personalRelevance }.thenByDescending { it.score.total })
            SearchSortOrder.RELEVANCE -> scored.sortedByDescending { it.score.total }
        }
    }

    fun score(
        video: VideoItem,
        analysis: SearchQueryAnalysis,
        candidates: List<VideoItem> = listOf(video),
        personalTerms: Set<String> = emptySet(),
        nowEpochMs: Long = System.currentTimeMillis()
    ): SearchScoreBreakdown {
        val title = SearchQueryAnalyzer.normalize(video.title)
        val channel = SearchQueryAnalyzer.normalize(video.channelName)
        val titleTokens = tokens.findAll(title).map { it.value }.toSet()
        val channelTokens = tokens.findAll(channel).map { it.value }.toSet()
        val queryTokens = analysis.tokens.distinct()
        val covered = queryTokens.count { it in titleTokens || it in channelTokens }
        val coverage = if (queryTokens.isEmpty()) 0.0 else covered.toDouble() / queryTokens.size
        val phrase = analysis.normalizedQuery.isNotBlank() && title.contains(analysis.normalizedQuery)
        val channelPhrase = analysis.normalizedQuery.isNotBlank() && channel.contains(analysis.normalizedQuery)
        val queryScore = when {
            phrase -> 100
            channelPhrase -> 88
            queryTokens.isEmpty() -> 0
            else -> (coverage * if (covered == queryTokens.size) 84 else 74).toInt()
        }.coerceIn(0, 100)

        // Text is a weak contextual hint, not proof of a video's spoken language.
        val languageScore = when {
            analysis.intent == SearchIntent.EXACT_ENTITY || !analysis.isVietnameseContext -> 50
            title.contains("viet nam") || title.contains("tieng viet") || channel.contains("viet nam") -> 78
            video.title.any { it in "ăâđêôơưĂÂĐÊÔƠƯ" } -> 66
            else -> 50
        }
        val ageMs = video.publishedAtEpochMs?.let { (nowEpochMs - it).coerceAtLeast(0L) }
            ?: ageMillis(video.uploadedText)
        val freshness = ageMs?.let { age ->
            val day = age / DAY_MS.toDouble()
            when {
                day <= 1 -> 100
                day <= 7 -> 82
                day <= 30 -> 62
                day <= 365 -> 38
                else -> 18
            }
        }
        val knownViews = candidates.mapNotNull { it.viewCount.takeIf { count -> count >= 0L } }
        val maxLogViews = knownViews.maxOfOrNull { ln(it + 1.0) }?.takeIf { it > 0.0 }
        val popularity = video.viewCount.takeIf { it >= 0L }?.let { views ->
            if (maxLogViews == null) 50 else ((ln(views + 1.0) / maxLogViews) * 100).toInt().coerceIn(0, 100)
        }
        val videoTerms = (titleTokens + channelTokens)
        val normalizedPersonal = personalTerms.map(SearchQueryAnalyzer::normalize).flatMap { tokens.findAll(it).map(MatchResult::value).toList() }.toSet()
        val personal = if (normalizedPersonal.isEmpty()) 50 else {
            val matched = videoTerms.intersect(normalizedPersonal).size
            ((matched.toDouble() / normalizedPersonal.size.coerceAtMost(6)) * 100).toInt().coerceIn(0, 100)
        }

        val weights = weightsFor(analysis.intent)
        // Unknown metadata stays neutral and has no power to overwhelm relevance.
        val freshnessValue = freshness ?: 50
        val popularityValue = popularity ?: 50
        val weighted = queryScore * weights.query + languageScore * weights.language +
            freshnessValue * weights.freshness + popularityValue * weights.popularity + personal * weights.personal
        return SearchScoreBreakdown(queryScore, languageScore, freshness, popularity, personal, weighted.toInt().coerceIn(0, 100))
    }

    fun publishedAt(video: VideoItem, nowEpochMs: Long = System.currentTimeMillis()): Long? =
        video.publishedAtEpochMs?.coerceAtMost(nowEpochMs)
            ?: ageMillis(video.uploadedText)?.let { (nowEpochMs - it).coerceAtLeast(0L) }

    fun matchesTimeRange(publishedAtEpochMs: Long?, range: SearchTimeRange, nowEpochMs: Long): Boolean {
        if (range == SearchTimeRange.ANY) return true
        if (publishedAtEpochMs == null) return false
        val now = Instant.ofEpochMilli(nowEpochMs).atZone(zone)
        val published = Instant.ofEpochMilli(publishedAtEpochMs).atZone(zone)
        return when (range) {
            SearchTimeRange.ANY -> true
            SearchTimeRange.TODAY -> published.toLocalDate() == now.toLocalDate()
            SearchTimeRange.WEEK -> published.isAfter(now.minus(7, ChronoUnit.DAYS))
            SearchTimeRange.MONTH -> published.isAfter(now.minusMonths(1))
            SearchTimeRange.YEAR -> published.isAfter(now.minusYears(1))
        }
    }

    private fun ageMillis(uploadedText: String): Long? {
        val normalized = SearchQueryAnalyzer.normalize(uploadedText)
        if (normalized.contains("just now") || normalized.contains("vua xong") || normalized == "hom nay" || normalized == "today") return 0L
        if (normalized.contains("yesterday") || normalized.contains("hom qua")) return DAY_MS
        val match = agePattern.find(normalized) ?: return null
        val count = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val unit = match.groupValues[2]
        val multiplier = when {
            unit.startsWith("sec") || unit == "giay" -> 1_000L
            unit.startsWith("min") || unit == "phut" -> 60_000L
            unit.startsWith("hour") || unit.startsWith("hr") || unit == "gio" -> 3_600_000L
            unit.startsWith("day") || unit == "ngay" -> DAY_MS
            unit.startsWith("week") || unit == "tuan" -> 7 * DAY_MS
            unit.startsWith("month") || unit == "thang" -> 30 * DAY_MS
            unit.startsWith("year") || unit == "nam" -> 365 * DAY_MS
            else -> return null
        }
        return (count * multiplier).toLong().coerceAtLeast(0L)
    }

    private data class Weights(val query: Double, val language: Double, val freshness: Double, val popularity: Double, val personal: Double)
    private fun weightsFor(intent: SearchIntent) = when (intent) {
        SearchIntent.EXACT_ENTITY -> Weights(.75, .02, .05, .12, .06)
        SearchIntent.RECENT -> Weights(.50, .10, .25, .10, .05)
        SearchIntent.POPULAR -> Weights(.50, .10, .08, .27, .05)
        SearchIntent.CHANNEL, SearchIntent.PLAYLIST -> Weights(.68, .07, .05, .13, .07)
        SearchIntent.MUSIC -> Weights(.57, .13, .08, .15, .07)
        SearchIntent.GENERAL -> Weights(.55, .12, .10, .15, .08)
    }

    private const val MIN_QUERY_RELEVANCE = 15
    private const val DAY_MS = 86_400_000L
}

/** Related-video order is led by shared subject and channel, never raw popularity. */
object RelatedVideoRanker {
    fun rank(candidates: List<VideoItem>, current: VideoItem): List<VideoItem> =
        MusicRecommendationRanker.rank(candidates, current)
}
