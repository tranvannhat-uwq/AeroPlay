# AeroPlay

Ứng dụng Android native phát video và âm thanh bằng Kotlin, Jetpack Compose Material 3 và Media3. Hỗ trợ Android 8.0 trở lên.

- [Tải APK từ GitHub Releases](https://github.com/tranvannhat-uwq/AeroPlay/releases/latest)
- [Cập nhật trực tiếp trong ứng dụng và cách phát hành bản mới](GITHUB_RELEASES.md)
- [Nguồn và ghi công tài nguyên media](OPEN_MEDIA_ASSETS.md)

## Tính năng

- Giao diện Liquid Glass sáng, vuốt thu video thành cửa sổ nổi và vuốt lên để mở rộng.
- Gợi ý Dành cho bạn dựa trên lịch sử xem và nguồn nội dung đang dùng.
- Thư viện video từ thiết bị, chọn từ Ảnh/Tệp hoặc chia sẻ sang AeroPlay, phát ngoại tuyến và phát nền.
- Nguồn YouTube qua NewPipe Extractor và catalog Chuẩn Mở riêng.
- Tìm kiếm, chọn chất lượng, phát video/audio, hàng đợi, mini player, phát nền và PiP.
- Lịch sử, vị trí xem dở, yêu thích và playlist lưu cục bộ bằng Room.
- Cập nhật bằng GitHub Releases: kiểm tra bản mới, tải APK trong nền, kiểm tra hash/certificate và mở trình cài Android.

Nguồn YouTube phụ thuộc khả năng trích xuất tại thời điểm truy cập. Ứng dụng không vượt CAPTCHA, DRM, đăng nhập hoặc giới hạn truy cập. Catalog Chuẩn Mở gồm phim/clip thật; các ảnh được đóng gói để không phụ thuộc máy chủ ảnh bên ngoài.

## Cài và cập nhật

Tải APK trong Releases và cài đè phiên bản trước để giữ dữ liệu. Từ phiên bản 1.1.0, vào **Cài đặt → Cập nhật ứng dụng** để cập nhật các phiên bản tiếp theo. Ứng dụng tự kiểm tra tối đa một lần mỗi ngày; vẫn cần xác nhận cài đặt của Android.

Không cần tài khoản GitHub hoặc API key trên điện thoại. Kho cập nhật mặc định là `tranvannhat-uwq/AeroPlay` và có thể đổi trong Cài đặt. APK cập nhật phải cùng chữ ký với ứng dụng đang cài.

## Build

Cần JDK 17 hoặc 21 và Android SDK 35. Dùng `JAVA_HOME` và `ANDROID_HOME` theo môi trường của bạn; `local.properties` không được đưa vào Git.

```sh
./gradlew testDebugUnitTest assembleDebug
```

APK debug ở `app/build/outputs/apk/debug/`. Build release cần cấu hình signing riêng hoặc các GitHub Actions Secrets như [hướng dẫn phát hành](GITHUB_RELEASES.md). Khóa ký, mật khẩu, log, cache và APK không nằm trong repository.

Chạy kiểm thử mạng/UI Android khi có thiết bị hoặc emulator:

```sh
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.aeroplayLive=true
```

Các bài mạng thật cần truy cập các nhà cung cấp tương ứng; chúng không thay bằng dữ liệu demo khi lỗi. Một số bài integration cập nhật cần fixture APK riêng như ghi trong mã test.

## Công nghệ

Kotlin 2.0.21 · Compose BOM 2024.10.01 · Media3 1.5.0 · NewPipe Extractor 0.27.1 · OkHttp 4.12.0 · Room 2.6.1 · Hilt 2.52.

Java/Kotlin target 17, core library desugaring `desugar_jdk_libs_nio`, minSdk 26, target/compileSdk 35. Chi tiết lớp ứng dụng nằm trong [ARCHITECTURE.md](ARCHITECTURE.md).

## Giấy phép

Mã nguồn được cung cấp theo [GNU GPLv3](LICENSE), phù hợp với NewPipe Extractor. Giấy phép và ghi công phim/ảnh/clip được ghi riêng trong [OPEN_MEDIA_ASSETS.md](OPEN_MEDIA_ASSETS.md) và đóng gói trong APK.
