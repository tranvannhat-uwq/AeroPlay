# Widget AeroPlay

Nhánh triển khai: `codex/pearl-glass-widgets`. APK để xem thử nằm tại `app/build/outputs/apk/debug/app-debug.apk`.

## Thêm và chỉnh widget

1. Nhấn giữ chỗ trống trên màn hình chính Android, mở **Tiện ích** và chọn **AeroPlay · Trình phát**.
2. Kích thước mặc định là **4×2**. Nhấn giữ widget và kéo tay nắm để thu về **2×2** hoặc tăng lên **4×4**. Launcher quyết định số ô và kích thước thực. AeroPlay chọn bố cục theo chiều rộng và chiều cao mà launcher báo.
3. Trong AeroPlay, vào **Cài đặt → Giao diện widget** để chọn **Tự động theo hệ thống**, **Pearl Glass Light** hoặc **Neon Glass Dark**. Có thể chỉnh độ đục mong muốn, tương phản Cao và màu nhấn Hệ thống, Cyan hoặc Violet. Cài đặt áp dụng ngay cho các widget đã thêm. Nền có chữ giữ độ đục tối thiểu để đọc được.

## Bố cục và hành vi

| Cỡ | Nội dung |
| --- | --- |
| 2×2 | Logo, ảnh bìa vuông và nút Phát/Tạm dừng ở giữa. Chạm ảnh bìa để mở trình phát. |
| 4×2 | Ảnh bìa, tiêu đề, tên kênh hoặc nghệ sĩ, tiến trình và ba nút phát. Dòng thời gian hoặc metadata phụ có thể ẩn ở kích thước rất thấp. |
| 4×4 | Ảnh bìa lớn, thông tin bài, chất lượng nếu đã biết, tiến trình, yêu thích, bài trước/sau, phát/tạm dừng, lặp bài và mục Tiếp theo lấy từ hàng chờ. |

Widget dùng MediaSession và hàng chờ hiện có. Album artwork lấy từ metadata và dùng bộ nhớ đệm Coil. Khi đang phát và launcher có widget, tiến trình được làm mới tối đa một lần mỗi 30 giây, ngoài các sự kiện như đổi bài, tua và tạm dừng. Chạm thanh tiến trình để mở trình phát. Lệnh phát ngẫu nhiên trong widget chưa có vì hàng chờ hiện tại không có công tắc đảo thứ tự tương thích; chức năng phát ngẫu nhiên trong thư viện nằm ở luồng riêng.

## Lớp kính và chữ

Widget dùng một lớp kính thống nhất, có alpha điều chỉnh độc lập, phản chiếu cyan/tím nhẹ và viền 1 dp. Nút và tag chất lượng có bề mặt riêng. Nền 2×2 có thể trong suốt hoàn toàn khi giảm độ đục về 0%. Ở bố cục có chữ, độ đục thực tế được nâng khi cần để bảo vệ khả năng đọc. Tương phản Cao đậm hơn và giảm phản chiếu.

Chữ dùng cặp màu của chế độ kính, với navy và xanh xám ở chế độ sáng, trắng và trắng xanh ở chế độ tối. Mức alpha tối thiểu được tính với nền trắng hoặc đen bất lợi và có dự phòng cho phản chiếu, thay cho việc đoán độ sáng tại vị trí widget từ một màu trung bình. Tùy chọn Hệ thống dùng màu nhấn Dynamic Color trên Android 12 trở lên nếu đủ tương phản với thanh tiến trình. Các nút chính giữ 48–60 dp. Bố cục ẩn thông tin phụ và nút phụ khi launcher báo thiếu diện tích hoặc tăng cỡ chữ.

AppWidget hiển thị alpha thật bằng RemoteViews. Drawable mô phỏng phản chiếu và chiều sâu. Launcher vẫn hiển thị hình nền nguyên gốc qua lớp kính, không có hiệu ứng khúc xạ hoặc làm mờ nền động.

## Tệp chính

- `playback/widget/PlaybackWidget.kt`, `WidgetPublisher.kt`, `WidgetAppearance.kt` cập nhật RemoteViews, kết nối trạng thái phát và lưu diện mạo widget.
- `res/layout/playback_widget_small.xml`, `playback_widget.xml`, `playback_widget_expanded.xml` và `widget_glass_layers.xml` chứa ba bố cục và lớp kính dùng chung. Màu, icon, viền và gradient nằm trong `res/drawable*` và `res/values*`.
- `res/xml/playback_widget_info.xml` cấu hình widget, preview và resize. Receiver hiện có trong AndroidManifest tiếp tục dùng cùng định danh.
- `SettingsScreen.kt` chứa phần tùy chỉnh. `AeroPlayApplication.kt` cập nhật mode Theo hệ thống khi cấu hình Android đổi. `MainActivity.kt`, `PlayerScreen.kt` nhận thao tác mở trình phát/hàng chờ. `AeroPlayMediaService.kt` gửi trạng thái phiên phát sang widget.
- `PlaybackWidgetSizeTest.kt` kiểm tra ngưỡng kích thước và nội suy alpha 0–255.

## Kiểm thử

- APK Debug 2.2.6 build thành công. Unit suite có 74 trường hợp, 71 chạy qua và 3 bỏ qua, bao gồm kiểm tra tương phản tối thiểu trên màu nền bất lợi và bố cục ở font scale lớn.
- Pixel Launcher trên emulator Android 15: thêm widget qua trình chọn, kéo giữa ba cỡ, kiểm tra widget rảnh và có phiên phát Media3, ảnh bìa, tiêu đề dài có ellipsis, mốc thời gian và bài tiếp theo. Đã thử Phát, Tạm dừng, Bài sau và Bài trước sau khi bỏ nền chữ. Đã thử Yêu thích, Lặp lại, mở hàng chờ và đổi chế độ kính trong quá trình phát triển.
- Emulator Android 8.0: cài được APK Debug và hệ thống đăng ký widget. Chưa kéo giãn và thử các nút điều khiển trên launcher ở phiên bản này.
- Kiểm tra bản 2.2.6 trên Pixel Launcher với hình nền thiên nhiên, chuyển sáng/tối theo hệ thống và cỡ chữ 100%, 130%, 150%. Đã kiểm tra 4×4, 4×2 và kích thước trung gian. Ảnh launcher bản mới có tiền tố `adaptive-2.2.6-` trong `app/build/verification/widgets/`.
- Còn cần kiểm thử trên điện thoại thật, các launcher OEM, thêm nhiều widget đồng thời, khởi động lại thiết bị, mất mạng kéo dài, mức dùng pin và RAM.
