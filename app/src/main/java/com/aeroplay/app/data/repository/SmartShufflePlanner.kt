package com.aeroplay.app.data.repository

import com.aeroplay.app.core.model.VideoItem
import java.util.PriorityQueue
import kotlin.random.Random

object SmartShufflePlanner {
    private data class ArtistBucket(val videos: ArrayDeque<VideoItem>, var tie: Int)

    fun plan(items: List<VideoItem>, current: VideoItem? = null, random: Random = Random.Default): List<VideoItem> {
        val unique = items.filter { it.url.isNotBlank() }.distinctBy(RecommendationRanking::stableKey)
        val finite = unique.filterNot { it.isLive }.ifEmpty { unique }
        val buckets = finite.shuffled(random).groupBy { RecommendationRanking.normalize(it.channelName).ifBlank { it.id } }
        val pending = PriorityQueue<ArtistBucket>(compareByDescending<ArtistBucket> { it.videos.size }.thenBy { it.tie })
        buckets.values.forEach { pending.add(ArtistBucket(ArrayDeque(it), random.nextInt())) }
        val result = mutableListOf<VideoItem>()
        var previous: ArtistBucket? = null
        while (pending.isNotEmpty()) {
            val bucket = pending.remove()
            result += bucket.videos.removeFirst()
            previous?.takeIf { it.videos.isNotEmpty() }?.let { pending.add(it) }
            bucket.tie = random.nextInt()
            previous = bucket
        }
        previous?.let { result.addAll(it.videos) }
        if (result.size > 1 && current != null && RecommendationRanking.stableKey(result.first()) == RecommendationRanking.stableKey(current)) {
            val index = result.indexOfFirst { RecommendationRanking.stableKey(it) != RecommendationRanking.stableKey(current) }
            java.util.Collections.swap(result, 0, index)
        }
        return result
    }
}
