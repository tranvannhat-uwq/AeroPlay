# Widget AeroPlay

Nhánh triển khai: `codex/pearl-glass-widgets`. APK để xem thử nằm tại `app/build/outputs/apk/debug/app-debug.apk`.

## Thêm và chỉnh widget

1. Nhấn giữ chỗ trống trên màn hình chính Android, mở **Tiện ích** và chọn **AeroPlay · Trình phát**.
2. Kích thước mặc định là **4×2**. Nhấn giữ widget và kéo tay nắm để thu về **2×2** hoặc tăng lên **4×4**. Launcher quyết định số ô và kích thước thực. AeroPlay chọn bố cục theo chiều rộng và chiều cao mà launcher báo.
3. Trong AeroPlay, vào **Cài đặt → Giao diện widget** để chọn **Pearl Glass Light**, **Neon Glass Dark** hoặc **Theo hệ thống**, rồi chỉnh **Độ đục nền kính** từ 0% đến 100%. Mặc định là 28%. Cài đặt áp dụng cho các widget đã thêm.

## Bố cục và hành vi

| Cỡ | Nội dung |
| --- | --- |
| 2×2 | Logo, ảnh bìa vuông và nút Phát/Tạm dừng ở giữa. Chạm ảnh bìa để mở trình phát. |
| 4×2 | Ảnh bìa, tiêu đề, tên kênh hoặc nghệ sĩ, tiến trình và ba nút phát. Dòng thời gian hoặc metadata phụ có thể ẩn ở kích thước rất thấp. |
| 4×4 | Ảnh bìa lớn, thông tin bài, chất lượng nếu đã biết, tiến trình, yêu thích, bài trước/sau, phát/tạm dừng, lặp bài và mục Tiếp theo lấy từ hàng chờ. |

Widget dùng MediaSession và hàng chờ hiện có. Album artwork lấy từ metadata và dùng bộ nhớ đệm Coil. Khi đang phát và launcher có widget, tiến trình được làm mới tối đa một lần mỗi 30 giây, ngoài các sự kiện như đổi bài, tua và tạm dừng. Chạm thanh tiến trình để mở trình phát. Lệnh phát ngẫu nhiên trong widget chưa có vì hàng chờ hiện tại không có công tắc đảo thứ tự tương thích; chức năng phát ngẫu nhiên trong thư viện nằm ở luồng riêng.

## Lớp kính và chữ

Nền cũ dùng gradient có alpha khoảng 86–93% rồi chồng thêm lớp phản chiếu. Widget mới dùng một ảnh nền drawable với alpha điều chỉnh độc lập, lớp phản chiếu cyan/tím nhẹ và viền 1 dp. Mức 0% bỏ độ phủ của bề mặt và lớp phản chiếu, viền vẫn đánh dấu mép widget. Nút và tag chất lượng có bề mặt riêng để dễ nhận biết.

Tên ứng dụng, tiêu đề, tên kênh, thời gian và chữ trong mục Tiếp theo nằm trực tiếp trên kính. Trên hình nền sáng, tiêu đề dùng navy `#142653`, nghệ sĩ dùng `#465A7B`, các mốc thời gian dùng `#516584`. Android 8.1 trở lên cho phép đọc màu chủ đạo do hệ thống báo để chọn chữ sáng khi nền tối. Android 8.0 dùng màu chữ theo chế độ kính. Màu chủ đạo toàn màn hình là một ước lượng, nên hình nền có độ sáng thay đổi mạnh ngay sau chữ có thể cần chỉnh độ đục hoặc chọn chế độ khác.

AppWidget hiển thị alpha thật bằng RemoteViews. Drawable mô phỏng phản chiếu và chiều sâu. Launcher vẫn hiển thị hình nền nguyên gốc qua lớp kính, không có hiệu ứng khúc xạ hoặc làm mờ nền động.

## Tệp chính

- `playback/widget/PlaybackWidget.kt`, `WidgetPublisher.kt`, `WidgetAppearance.kt` cập nhật RemoteViews, kết nối trạng thái phát và lưu diện mạo widget.
- `res/layout/playback_widget_small.xml`, `playback_widget.xml`, `playback_widget_expanded.xml` và `widget_glass_layers.xml` chứa ba bố cục và lớp kính dùng chung. Màu, icon, viền và gradient nằm trong `res/drawable*` và `res/values*`.
- `res/xml/playback_widget_info.xml` cấu hình widget, preview và resize. Receiver hiện có trong AndroidManifest tiếp tục dùng cùng định danh.
- `SettingsScreen.kt` chứa phần tùy chỉnh. `AeroPlayApplication.kt` cập nhật mode Theo hệ thống khi cấu hình Android đổi. `MainActivity.kt`, `PlayerScreen.kt` nhận thao tác mở trình phát/hàng chờ. `AeroPlayMediaService.kt` gửi trạng thái phiên phát sang widget.
- `PlaybackWidgetSizeTest.kt` kiểm tra ngưỡng kích thước và nội suy alpha 0–255.

## Kiểm thử

- APK Debug build thành công. Unit suite có 72 trường hợp, 69 chạy qua và 3 bỏ qua.
- Pixel Launcher trên emulator Android 15: thêm widget qua trình chọn, kéo giữa ba cỡ, kiểm tra widget rảnh và có phiên phát Media3, ảnh bìa, tiêu đề dài có ellipsis, mốc thời gian và bài tiếp theo. Đã thử Phát, Tạm dừng, Bài sau và Bài trước sau khi bỏ nền chữ. Đã thử Yêu thích, Lặp lại, mở hàng chờ và đổi chế độ kính trong quá trình phát triển.
- Emulator Android 8.0: cài được APK Debug và hệ thống đăng ký widget. Chưa kéo giãn và thử các nút điều khiển trên launcher ở phiên bản này.
- Kiểm tra độ đục 0%, 28% và 100%. Một điểm không có chữ hoặc nút tại cùng vị trí cho kết quả `#100F19` ở 0%, `#4D4C57` ở 28% và `#EEEEFD` ở 100%, tương ứng khoảng 28% pha màu ở mức mặc định. Đã kiểm tra bằng cả hình nền sáng và tối trên launcher.
- Ảnh chụp launcher lưu trong `app/build/verification/widgets/`. Ảnh có tiền tố `final-` là bản đã bỏ nền chữ.
- Còn cần kiểm thử trên điện thoại thật, các launcher OEM, thêm nhiều widget đồng thời, khởi động lại thiết bị, mất mạng kéo dài, mức dùng pin và RAM.
