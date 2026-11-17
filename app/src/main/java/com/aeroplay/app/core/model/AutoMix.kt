package com.aeroplay.app.core.model

/** A generated snapshot, not a YouTube account playlist or a persisted user playlist. */
data class AutoMix(
    val id: String,
    val title: String,
    val subtitle: String,
    val videos: List<VideoItem>,
    val isMusic: Boolean = true
)
