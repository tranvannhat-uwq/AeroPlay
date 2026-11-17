# AeroPlay 2.1.0 — rà soát nhận diện

## Tài sản gốc

Sao chép nguyên bản ảnh người dùng cung cấp, không vẽ lại, không đổi màu hoặc cắt chữ.

| Đầu vào | Resource | SHA-256 file gốc/resource nguồn |
| --- | --- | --- |
| `nav.png` (2172 × 724, có alpha) | `drawable-nodpi/brand_wordmark.png` | `7C59BFB5187732C7B88A85559D73CDEB5DDE7177813BC1A78E7B0A0BF9C36FA3` |
| `bieutuong.png` (1254 × 1254) | `drawable-nodpi/brand_app_icon.png` | `134E3DCCD6DFD34D615DA16E5C954751880A226D3EA252C45E98C7CE29AC9DBD` |

Header dùng logo nhỏ, nền trong suốt, không khung/viền. Giao diện tối hiển thị PNG logo ngang nguyên bản ở 144 × 48 dp; giao diện sáng dùng icon gốc cùng chữ AeroPlay native màu tương phản để không phải đặt chữ trắng trong một thẻ xanh đêm. Các file ảnh gốc không bị chỉnh sửa. Launcher dùng dấu Play/dải sóng được tách riêng trên nền gradient phủ kín; xem `docs/branding/launcher-mark.md` để biết asset và prompt. Icon đơn sắc cho themed launcher/thông báo là silhouette play và luồng gió, vì Android cần tài nguyên đơn sắc cho các vị trí này.

Màn khởi động Android 12+ dùng `brand_splash_icon.xml` riêng thay vì lấy adaptive launcher đã bị mask. Inset 28% giữ trọn ảnh vuông trong vùng an toàn tròn. Không sửa pixel hay cắt lại PNG gốc của splash. Tham chiếu: https://developer.android.com/develop/ui/views/launch/splash-screen (canvas 288 dp, vùng an toàn đường kính 192 dp khi không có icon background).

## Phạm vi đã chỉnh

| Khu vực | Thực hiện |
| --- | --- |
| Launcher thường/round, Android 13+ themed icon | Dấu Play/dải sóng riêng, nền gradient xanh–tím phủ kín, không có viền đen; silhouette cho themed icon |
| Khởi động trước/sau Android 12 | Splash xanh đêm, icon mới; Android 12+ có logo chữ |
| Theme sáng/tối/hệ thống | Đầy đủ role Material3, nền/viền/chữ/container/inverse/error; không dùng dynamic wallpaper colors để tránh lệch nhận diện |
| Thành phần dùng chung | GlassBackground/GlassSurface, navigation, video card, loading, empty/error/offline, source pill, nút/chip/slider/dialog |
| Trang chủ | Logo nhỏ và nút nguồn trên cùng thanh đầu trang; bỏ banner và dòng nhãn phụ, ô tìm kiếm cách 8 dp; thanh đầu tự xuống dòng nếu thiếu chiều rộng |
| Tìm kiếm | Header icon mới; video dạng hàng ngang với thumbnail 120 dp tỉ lệ 16:9, tiêu đề tối đa 2 dòng, metadata gọn; thao tác yêu thích/playlist trong menu ba chấm; khoảng cách hàng 6 dp và skeleton cùng bố cục. Bộ lọc/trạng thái trống theo theme |
| Thư viện | Header mới, nút thao tác tách dòng để không ép tiêu đề; tab, lịch sử, yêu thích, playlist, nội dung thiết bị và dialog dùng theme chung |
| Cài đặt | Header mới, nhóm chất lượng/tốc độ/theme tự xuống dòng; giới thiệu dùng logo ngang; mục cập nhật dùng icon mới |
| Trình phát | Scrim xanh đêm, điều khiển cyan/chữ tương phản; yêu thích tím, chế độ âm thanh cyan; portrait/landscape/mini player |
| Thông báo media | Small icon mới; ảnh bìa bài hát vẫn theo nội dung đang phát |
| Màn hình tài khoản/nhúng tùy chọn | Theo MaterialTheme hoặc brand token; tiếp tục tắt bằng hai feature flags trong bản này |

Màu lỗi đỏ, cảnh báo hổ phách và xác minh xanh lá vẫn biểu thị trạng thái, không dùng làm màu trang trí. Logo YouTube chính thức không đổi màu. Ảnh bìa nội dung, giao diện cấp quyền/installer của Android và giao diện do đầu xe Android Auto vẽ không bị nhuộm màu.

Rà mã nguồn `app/src/main` không còn bảng màu trang trí Coral/Obsidian cũ; màu ngoài Theme.kt chỉ còn native resource màu thương hiệu, icon đơn sắc và backing logo YouTube.

## Bảo toàn hành vi và dữ liệu

- Package `com.aeroplay.app`, Room schema/migration và kho cài đặt không đổi.
- Không thay NewPipe, Media3, PiP, Android Auto, logic giữ màn hình sáng hoặc thuật toán đề xuất.
- Thay đổi service chỉ thêm small icon của notification provider.
- Cờ build `youtubeAccountSyncEnabled=false`, `youtubeApiOnlyMode=false`.
- Bản demo dùng APK Debug trên giả lập riêng, chọn nguồn Open Media và clip kiểm thử có giấy phép; đây không phải dữ liệu tài khoản YouTube.
- Không cài APK Debug đè lên điện thoại đang dùng bản Release vì khác chữ ký. Cài bản cập nhật chính thức cùng chữ ký khi phát hành để giữ dữ liệu.

## Xác minh

- Unit: 57 test được phát hiện (54 pass, 3 test mạng trực tiếp skip theo cấu hình mặc định), 0 failure/error; gồm tương phản 11 cặp màu foreground/background cho mỗi theme (>= 4.5:1).
- Android 15 / API 35: launcher resource inflate/render, có monochrome; điều hướng và fullscreen đã qua. MediaBrowser Android Auto root/browse cũng qua trên giả lập, không đại diện cho đầu xe thật.
- Visual audit tự động dùng màn hình Compose thật, chụp sáng/tối: home, search, search empty, favorites, playlists, create dialog, settings, about, licenses, player portrait/landscape, quality dialog và mini player.
- Audit sáng/tối ở font scale 1.0 và 1.3 đã qua trên API 35. Cửa sổ giả lập được mở để người dùng xem trực tiếp trước khi cập nhật. Ảnh chụp chờ thêm khung hình render sau khi Compose semantics đã ổn định để tránh lưu ảnh của trạng thái trước đó.
- Sau thay đổi kết quả tìm kiếm dạng hàng: audit sáng/tối và 2 bài SearchLayoutUiTest đều pass trên API 35 (3/3); xác nhận hàng gọn, menu yêu thích cập nhật dữ liệu và mở dialog playlist không khởi động phát video. Ảnh `search-rows-search-compact-light.png`/`dark.png` cho thấy 3 kết quả trọn vẹn và một phần mục tiếp theo ở kích thước giả lập này. Chưa đo hiệu năng/tốc độ tìm trên điện thoại thật.
- Bổ sung danh sách kết hợp tự động: 63 unit test được phát hiện (60 pass, 3 live test skip), 0 failure/error. `AutoMixDeviceTest` pass cả thay hàng đợi khi chọn lại clip đang tải sẵn và lấy ứng viên YouTube thật. Kiểm tra mạng với truy vấn MV/audio đã sinh 2 bản phối, mỗi danh sách 15 bài; số lượng thay đổi theo dữ liệu nguồn. Kiểm tra này xác nhận dữ liệu/ghép danh sách, không xác nhận phát mọi MV.
- Đã mở xem bản phối thật trên giả lập API 35 qua giao diện: ảnh ghép, số bài, danh sách cuộn và nút Phát bản phối. Hai cờ Google/YouTube API tiếp tục false; chỉ nguồn NewPipe của bản demo giả lập được chọn để xem nhạc thật. Chưa cài lên điện thoại, chưa phát hành.
- Sửa icon bị cắt: 2/2 `BrandResourcesDeviceTest` pass trên API 35, gồm kiểm tra theme dùng resource splash riêng và toàn bộ pixel ảnh nằm trong vùng an toàn tròn. Đã chụp splash native khi cold start (`.private/brand-review/splash-fixed-2.png`) và render launcher (`launcher-fixed.png`): giữ trọn viền/góc ảnh gốc. Chưa kiểm tra launcher OEM trên điện thoại thật.
- Thẻ bản phối: giữ cùng chiều rộng/tỉ lệ ảnh, cố định vùng chữ bằng 2 dòng tiêu đề + 1 dòng số bài + 2 dòng mô tả; phần chữ tự co giãn theo font scale, không khóa chiều cao dp. Build Debug và ảnh chụp trực tiếp API 35 (`.private/brand-review/mix-cards-equal.png`) xác nhận hai thẻ có cùng đáy dù mô tả dài/ngắn khác nhau.
- Launcher cuối cùng không dùng tile vuông thu nhỏ trên nền Midnight. Bản dựng dùng foreground alpha riêng + gradient phủ kín; 2/2 BrandResourcesDeviceTest pass, gồm kiểm tra mép icon không có nền đen. Ảnh render `.private/brand-review/launcher-no-ring.png` thay cho preview launcher cũ. Splash vẫn giữ ảnh vuông gốc riêng.
- Ảnh kiểm tra cục bộ: `.private/brand-review/api35/aeroplay-brand-review/`. Các lần test chưa đạt do bộ kiểm thử thao tác phần tử ngoài vùng cuộn/quyền thông báo đã được sửa; không coi các lần đó là pass.
- Kiểm tra bản phát hành 2.1.0: 60 unit test pass (3 bài mạng tùy chọn không chạy), lint không có lỗi và 11 bài trên giả lập Android 15 đều pass. Bộ kiểm tra bao gồm giao diện sáng/tối, icon, tìm kiếm, hàng đợi bản phối, toàn màn hình, MediaBrowser và lệnh tìm nhạc qua phiên âm thanh.
- Còn cần kiểm thử trước phát hành: thiết bị Android cũ, splash/OEM launcher/theme icon thực tế, điện thoại thật và đầu xe thật; các luồng mạng YouTube không được xác nhận lại bởi bài kiểm tra nội dung offline.
