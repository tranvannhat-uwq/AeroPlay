package com.aeroplay.app.data.source

import com.aeroplay.app.core.model.AudioTrack
import com.aeroplay.app.core.model.ContentSourceType
import com.aeroplay.app.core.model.DeliveryType
import com.aeroplay.app.core.model.StreamVariant
import com.aeroplay.app.core.model.VideoDetail

/** Verified public media and packaged artwork. Credits and upstream URLs: OPEN_MEDIA_ASSETS.md. */
internal object OpenMediaCatalog {
    private const val SINTEL = "https://storage.googleapis.com/shaka-demo-assets/sintel-mp4-only/"
    private const val TEARS = "https://storage.googleapis.com/wvmedia/clear/h264/tears/"
    private const val BUNNY = "https://test-streams.mux.dev/x36xhzz/"

    fun artwork(name: String) = "android.resource://com.aeroplay.app/drawable/open_media_$name"

    private fun variant(id: String, url: String, width: Int, height: Int, videoOnly: Boolean = false) =
        StreamVariant(
            id = id, resolutionLabel = "${height}p MP4 ($width×$height)",
            width = width, height = height, fps = 24, url = url,
            isVideoOnly = videoOnly,
            deliveryType = if (videoOnly) DeliveryType.VIDEO_ONLY else DeliveryType.PROGRESSIVE
        )

    private fun bunnyVariant(id: String, path: String, width: Int, height: Int) =
        variant(id, BUNNY + path, width, height).copy(
            resolutionLabel = "${height}p HLS ($width×$height)",
            formatMimeType = "application/x-mpegURL", deliveryType = DeliveryType.HLS
        )

    private fun movie(
        id: String, title: String, description: String, artworkName: String, duration: Long,
        variants: List<StreamVariant>, audio: List<AudioTrack> = emptyList(),
        channel: String = "Blender Studio Open Movies", channelUrl: String = "https://studio.blender.org"
    ) = VideoDetail(
        id = id, url = variants.first().url, title = title, description = description,
        channelName = channel, channelUrl = channelUrl,
        channelAvatarUrl = artwork(artworkName), thumbnailUrl = artwork(artworkName),
        durationSeconds = duration, streamVariants = variants, audioTracks = audio,
        uploadDateText = if (id == "demo_escapes") "Google Cast / Android CTS test clip" else "Creative Commons CC-BY",
        sourceType = ContentSourceType.DEMO_LOCAL
    )

    val videos: List<VideoDetail> = listOf(
        movie(
            "demo_bbb", "Big Buck Bunny – Blender Open Movie (CC-BY 3.0)",
            "Phim hoạt hình mở của Blender Foundation. Các mức chất lượng HLS là cùng bản phim, có sẵn âm thanh AAC.",
            "bbb", 634,
            listOf(
                bunnyVariant("bbb_720p", "url_0/193039199_mp4_h264_aac_hd_7.m3u8", 1280, 720),
                bunnyVariant("bbb_480p", "url_6/193039199_mp4_h264_aac_hq_7.m3u8", 848, 480),
                bunnyVariant("bbb_288p", "url_4/193039199_mp4_h264_aac_7.m3u8", 512, 288)
            )
        ),
        movie(
            "demo_sintel", "Sintel – Third Blender Open Movie (Fantasy Short Film)",
            "Phim ngắn giả tưởng mở của Blender Foundation. Video H.264 được ghép với luồng AAC của chính phim Sintel.",
            "sintel", 888,
            listOf(
                variant("sintel_546p", "${SINTEL}v-0720p-2500k-libx264.mp4", 1280, 546, true),
                variant("sintel_272p", "${SINTEL}v-0360p-0750k-libx264.mp4", 640, 272, true)
            ),
            listOf(AudioTrack("sintel_audio", "128 kbps AAC", 128, "audio/mp4", "mp4a.40.2", "en", "${SINTEL}a-eng-0128k-aac.mp4"))
        ),
        movie(
            "demo_tos", "Tears of Steel – Sci-Fi Open Movie (VFX & Live Action)",
            "Phim khoa học viễn tưởng mở của Blender Foundation. Dùng các luồng H.264/AAC không DRM trong bộ mẫu Media3.",
            "tos", 734,
            listOf(
                variant("tos_570p", "${TEARS}tears_h264_main_720p_8000.mp4", 1280, 570, true),
                variant("tos_380p", "${TEARS}tears_h264_main_480p_2000.mp4", 854, 380, true)
            ),
            listOf(AudioTrack("tos_audio", "128 kbps AAC", 128, "audio/mp4", "mp4a.40.2", "en", "${TEARS}tears_audio_eng.mp4"))
        ),
        movie(
            "demo_elephants", "Elephants Dream – First Open Movie Project",
            "Phim hoạt hình mở đầu tiên của Blender, lấy từ bản H.264/AAC được Blender công bố.",
            "elephants", 658,
            listOf(
                variant("ed_576p", "https://download.blender.org/ED/elephantsdream-1024-h264-st-aac.mov", 1024, 576),
                variant("ed_270p", "https://download.blender.org/ED/elephantsdream-480-h264-st-aac.mov", 480, 270)
            ),
            channel = "Orange Open Movie Project", channelUrl = "https://orange.blender.org"
        ),
        movie(
            "demo_escapes", "For Bigger Escapes – Google Cast Test Clip",
            "Clip kiểm thử Google Cast 15 giây, bản HEVC của Android CTS được đóng gói sẵn để kiểm thử chuyển bài và phát khi không có mạng.",
            "escapes", 15,
            listOf(variant("escapes_720p", "android.resource://com.aeroplay.app/raw/open_media_escapes", 1280, 720)
                .copy(resolutionLabel = "720p HEVC (1280×720)", codec = "hvc1", fps = 30)),
            channel = "Google Cast Test Media", channelUrl = "https://developers.google.com/cast"
        )
    )
}
