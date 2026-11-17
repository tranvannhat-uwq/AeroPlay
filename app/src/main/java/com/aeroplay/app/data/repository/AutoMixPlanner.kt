package com.aeroplay.app.data.repository

import com.aeroplay.app.core.model.AutoMix
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.VideoItem

/** Bounded, deterministic grouping over real source candidates; no extra network requests. */
object AutoMixPlanner {
    fun build(
        candidates: List<VideoItem>,
        source: ContentSourceType,
        seed: VideoItem? = null,
        seedDescription: String = "",
        fromMusicSearch: Set<String> = emptySet(),
        preferred: List<VideoItem> = emptyList()
    ): List<AutoMix> {
        val available = (preferred.take(80) + candidates.take(160)).filter { it.sourceType == source && !it.isLive }
            .distinctBy(RecommendationRanking::stableKey)
        if (!source.isExperimental) {
            val videos = MusicRecommendationRanker.diversify(available, 25)
            return if (videos.size < 3) emptyList() else listOf(AutoMix(
                "${source.name}:discovery", if (source == ContentSourceType.DEVICE_LOCAL) "Khám phá trên thiết bị" else "Khám phá Chuẩn Mở",
                "Kết hợp từ danh mục hiện tại", videos, isMusic = false))
        }
        val current = seed?.takeIf { it.sourceType == source && !it.isLive }
        // Broad music searches often return hour-long compilations. Keep ordinary songs unless
        // the listener explicitly seeded a long-form performance/mix.
        val maximumDuration = current?.durationSeconds?.takeIf { it > 1_200 }?.times(2)?.coerceAtMost(14_400) ?: 1_200L
        val music = available.filter { it.durationSeconds in 30..maximumDuration }.filter {
            RecommendationRanking.stableKey(it) in fromMusicSearch ||
                MusicStyleAnalyzer.analyze(it.title, it.channelName).isLikelyMusic ||
                (current != null && it.id == current.id)
        }
        val result = mutableListOf<AutoMix>()
        fun add(key: String, title: String, subtitle: String, pool: List<VideoItem>) {
            val videos = MusicRecommendationRanker.diversify(pool, 25)
            // Do not manufacture a playlist from a single song and alternate uploads.
            if (videos.size < 3) return
            val keys = videos.map(RecommendationRanking::stableKey).toSet()
            if (result.any { it.videos.map(RecommendationRanking::stableKey).toSet() == keys && it.videos == videos }) return
            result += AutoMix("${source.name}:$key", title, subtitle, videos)
        }
        val ranked = current?.let {
            listOf(it) + MusicRecommendationRanker.rank(music, it, 25, seedDescription, forceMusic = true)
        } ?: music
        val hasPersonalMusic = current != null || preferred.any { item -> music.any { it.id == item.id && it.sourceType == item.sourceType } }
        add("for-you", if (hasPersonalMusic) "Bản phối của bạn" else "Bản phối khám phá",
            if (hasPersonalMusic) "Từ bài đang nghe và sở thích gần đây" else "Khám phá nhạc từ nguồn hiện tại", ranked)
        val genres = (current?.let { MusicStyleAnalyzer.analyze(it.title, it.channelName, seedDescription).genres }.orEmpty() +
            music.flatMap { MusicStyleAnalyzer.analyze(it.title, it.channelName).genres })
            .distinct().take(3)
        for (genre in genres) {
            val pool = music.filter { genre in MusicStyleAnalyzer.analyze(it.title, it.channelName,
                if (it.id == current?.id) seedDescription else "").genres }
            val label = when (genre) { "edm" -> "EDM"; "rnb" -> "R&B"; "lofi" -> "Lofi"; else -> genre.replaceFirstChar(Char::uppercase) }
            add("genre:$genre", "Bản phối $label", "Các bài khác nhau cùng phong cách", pool)
        }
        add("popular", "Bản phối nhiều lượt xem", "Bài phù hợp, xếp theo lượt xem từ nguồn",
            ranked.filter { it.viewCount > 0 }.sortedByDescending { it.viewCount })
        return result.take(5)
    }
}
