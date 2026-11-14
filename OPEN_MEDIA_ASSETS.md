# Nguồn media và ảnh đóng gói — AeroPlay 1.0.1

Nguồn Chuẩn Mở là catalog kiểm thử độc lập với NewPipe. Không tạo lượt xem, lượt thích, số người đăng ký hoặc dấu xác minh giả. Ảnh được đóng gói trong `res/drawable-nodpi` để không còn phụ thuộc bucket ảnh đã trả HTTP 403.

| Nội dung | Video / audio công khai | Ảnh và ghi công |
|---|---|---|
| Big Buck Bunny | [Mux/hls.js clear HLS](https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8): chọn các playlist thật 1280×720, 848×480, 512×288; cùng bản phim dài khoảng 634 giây | [Google Cast sample artwork](https://github.com/googlecast/CastVideos-chrome/blob/master/imagefiles/bunny.jpg); © Blender Foundation, CC BY 3.0 |
| Sintel | [Shaka clear MP4 catalog](https://storage.googleapis.com/shaka-demo-assets/sintel-mp4-only/dash.mpd), các file H.264 1280×546 / 640×272 và AAC tiếng Anh cùng phim | [Shaka icon](https://storage.googleapis.com/shaka-asset-icons/sintel.png); © Blender Foundation, CC BY 3.0 |
| Tears of Steel | [Media3 clear DASH sample](https://storage.googleapis.com/wvmedia/clear/h264/tears/tears.mpd), các file H.264 1280×570 / 854×380 và AAC tiếng Anh cùng phim | [Shaka icon](https://storage.googleapis.com/shaka-asset-icons/tears_of_steel.png); © Blender Foundation, CC BY 3.0 |
| Elephants Dream | [Blender H.264/AAC 1024×576 MOV](https://download.blender.org/ED/elephantsdream-1024-h264-st-aac.mov), [480×270 MOV](https://download.blender.org/ED/elephantsdream-480-h264-st-aac.mov); cùng thời lượng thực 658.325 giây | [Blender cover](https://download.blender.org/ED/cover.jpg); © Orange Open Movie Project / Blender Foundation, CC BY 2.5 |
| For Bigger Escapes | [Android CTS 720p HEVC 15s](https://android.googlesource.com/platform/cts/+/refs/heads/main/tests/tests/mediacujtest/smalltest/res/raw/ForBiggerEscapes_720p_hevc_15s.mp4), đóng gói `res/raw/open_media_escapes.mp4` | Frame thực tại giây thứ 3 của clip; © Google LLC. [CTS attribution](https://android.googlesource.com/platform/cts/+/refs/heads/main/tests/tests/mediacujtest/smalltest/README.md), [AOSP Apache 2.0 license](https://android.googlesource.com/platform/cts/+/refs/heads/main/NOTICE) |

Danh mục Shaka: [demo/common/assets.js](https://github.com/shaka-project/shaka-player/blob/main/demo/common/assets.js). Các nguồn DASH được chọn đều là **clear**, không dùng các mẫu Widevine/FairPlay hoặc license server. Kích thước và thời lượng được kiểm tra bằng Android MediaMetadataRetriever; kiểm thử phát dùng Media3 qua MediaSession.

Không có AAC riêng cho Big Buck Bunny và Elephants Dream trong các nguồn đang dùng: audio-only sử dụng fallback HLS/MOV có âm thanh, nên vẫn tải dữ liệu video. Sintel và Tears of Steel có AAC riêng, không gán clip khác làm audio. Bản For Bigger Escapes của Android CTS có cùng nội dung test Google Cast, không phải clip thiên nhiên như tiêu đề cũ đã ghi nhầm.

Bản MP4 Big Buck Bunny từ W3C 853×480 và bản Elephants Dream 720×405 trả HTTP 200 nhưng decoder Goldfish của emulator API 35 từ chối cấu hình; không dùng hai bản đó trong catalog bàn giao. Các luồng thay thế có kích thước chẵn và được kiểm tra bằng phát Media3 thực tế. Cấu hình decoder của player giữ nguyên.

Giữ các ID `demo_*` và nhận diện URL bucket cũ để mục lịch sử/yêu thích vẫn mở đúng phim. URL không thuộc catalog trả lỗi rõ ràng thay vì phát nhầm Big Buck Bunny.
