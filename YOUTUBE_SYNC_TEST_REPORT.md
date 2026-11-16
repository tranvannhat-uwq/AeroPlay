# AeroPlay — tài khoản YouTube chỉ đọc

## Đã triển khai

- Đăng nhập danh tính Google bằng Credential Manager; xin scope `youtube.readonly` tăng dần bằng AuthorizationClient.
- Đọc kênh, video đã thích, playlist do tài khoản sở hữu, video trong playlist và kênh đăng ký bằng YouTube Data API v3. Chỉ dùng GET; không có API ghi, hàng đợi thao tác hoặc đồng bộ ngược.
- Cache Room phân vùng theo tài khoản/kênh; phân trang, mở nội dung playlist theo nhu cầu, hạn cache 30 ngày, timeout, retry có giới hạn, hủy request, báo lỗi quyền/quota/mạng và xóa cache khi đăng xuất hoặc quyền bị thu hồi.
- Màn hình Tài khoản YouTube trong Cài đặt, thông báo quyền riêng tư, làm mới, chọn kênh, đăng xuất/thu hồi quyền và mở video/kênh bằng ứng dụng YouTube hoặc trình duyệt. Lịch sử xem và Xem sau vẫn là dữ liệu AeroPlay cục bộ.
- Chế độ API-only chặn nguồn NewPipe và mọi đường phát YouTube qua Media3 khi bật tài khoản; vẫn giữ Media3 và nguồn khác. Không chuyển OAuth token sang extractor hoặc trình phát.

## Kiểm thử/build cục bộ

Đã chạy trên Windows bằng JDK 17 portable, vì Java mặc định trên máy chỉ có JRE:

```text
gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest
```

Kết quả: **BUILD SUCCESSFUL**. Unit tests: **51 tổng, 0 lỗi, 0 thất bại, 3 bỏ qua**. Ba bài bỏ qua cần bật kiểm thử extractor mạng thật; các kiểm thử API mới dùng MockWebServer, không truy cập tài khoản Google thật. APK debug và APK kiểm thử Android đều biên dịch thành công.

APK debug: `app/build/outputs/apk/debug/app-debug.apk` — **28,921,569 bytes**.

Bản APK này dùng cấu hình mặc định: `YOUTUBE_ACCOUNT_SYNC_ENABLED=false`, do chưa có Web OAuth Client ID. Màn hình sẽ báo cần cấu hình; **đây chưa phải APK có thể đăng nhập tài khoản**. Hướng dẫn tạo credential và build bản bật tính năng ở [GOOGLE_OAUTH_SETUP.md](GOOGLE_OAUTH_SETUP.md).

Đã build riêng một lần với cờ tài khoản/API-only bật và Web Client ID giả chỉ để xác nhận nhánh cấu hình này biên dịch thành công; sau đó build lại APK mặc định. Không dùng APK validation đó để đăng nhập hoặc phân phối.

## Chưa kiểm chứng trên thiết bị/tài khoản thật

- Máy hiện tại không có `adb`, emulator hoặc thiết bị Android được kết nối; không chạy được Room instrumentation/UI test trên thiết bị.
- Chủ dự án chưa cung cấp Google Cloud OAuth Web Client ID, test user hoặc tài khoản thử. Vì vậy chưa xác minh giao diện chọn tài khoản/consent trực tiếp, đăng nhập thật, dữ liệu likes/playlist/subscriptions, quota trực tiếp và thu hồi quyền thực tế.
- Không tuyên bố sản phẩm đã được YouTube phê duyệt. APK mặc định vẫn tắt tính năng tài khoản và giữ hành vi phát NewPipe hiện có. Muốn bật tài khoản phải build riêng ở API-only mode để khóa phát YouTube không chính thức.
