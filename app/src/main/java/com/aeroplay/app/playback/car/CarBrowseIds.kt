package com.aeroplay.app.playback.car

import java.util.Base64

/** Stable identifiers survive Android Auto reconnects and don't contain expiring media URLs. */
internal object CarBrowseIds {
    const val ROOT = "car/root"
    const val FAVORITES = "car/favorites"
    const val RECENT = "car/recent"
    const val PLAYLISTS = "car/playlists"
    const val DEVICE = "car/device"
    const val MUSIC = "car/music"
    fun track(source: String, id: String): String = "car/track/$source/" +
        Base64.getUrlEncoder().withoutPadding().encodeToString(id.toByteArray(Charsets.UTF_8))
    fun decodeTrack(value: String): Pair<String, String>? = runCatching {
        val parts = value.split('/')
        require(parts.size == 4 && parts[0] == "car" && parts[1] == "track")
        require(parts[2] in setOf("DEMO_LOCAL", "EXPERIMENTAL_EXTRACTOR", "DEVICE_LOCAL"))
        require(parts[3].length in 1..2048)
        val id = String(Base64.getUrlDecoder().decode(parts[3]), Charsets.UTF_8)
        require(id.isNotBlank() && track(parts[2], id) == value)
        parts[2] to id
    }.getOrNull()

    fun <T> page(items: List<T>, page: Int, pageSize: Int): List<T> {
        require(page >= 0 && pageSize > 0)
        val offset = page.toLong() * pageSize
        return if (offset >= items.size) emptyList() else items.drop(offset.toInt()).take(pageSize.coerceAtMost(100))
    }
}
