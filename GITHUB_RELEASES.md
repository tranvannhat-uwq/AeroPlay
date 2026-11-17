# Phát hành và cập nhật AeroPlay qua GitHub

Kho chính thức: https://github.com/tranvannhat-uwq/AeroPlay

## Cách dùng trên điện thoại

Cài AeroPlay 1.1.0 trở lên để dùng tính năng cập nhật. Vào **Cài đặt → Cập nhật ứng dụng → Kiểm tra cập nhật**. Khi có bản mới, chọn **Tải cập nhật**, chờ ứng dụng kiểm tra APK, rồi chọn **Cài đặt**. Android 8+ có thể yêu cầu bật “Cho phép từ nguồn này” cho AeroPlay và xác nhận cài đặt.

Ứng dụng cũng tự kiểm tra khi mở, tối đa mỗi 24 giờ. Kiểm tra thủ công luôn có thể chạy lại. APK được tải bằng DownloadManager vào thư mục riêng của ứng dụng; bản tải tiếp tục trong nền và được khôi phục theo dõi sau khi mở lại. Trước khi mở trình cài đặt, AeroPlay kiểm tra kích thước, SHA-256, package `com.aeroplay.app`, versionCode, minSdk và certificate phải khớp bản đang cài. Không cần tài khoản GitHub hoặc token trong ứng dụng.

## Phát hành phiên bản tiếp theo

1. Sửa `version.properties`: tăng `VERSION_CODE` (bắt buộc lớn hơn trước) và đặt `VERSION_NAME`, ví dụ `1.1.1`.
2. Cập nhật nội dung trong `RELEASE_NOTES.md` và commit mã nguồn.
3. Tạo tag trùng versionName rồi push:

```shell
git tag v1.1.1
git push origin main
git push origin v1.1.1
```

Workflow `.github/workflows/release.yml` tự chạy unit tests, build release đã ký, kiểm tra certificate, tạo manifest, đóng gói source từ tag, upload đủ assets vào một draft Release rồi mới công bố. Điện thoại chỉ đọc release ổn định đã công bố qua `/releases/latest`.

Mỗi Release có:

- `AeroPlay-v1.1.1.apk`: APK cài được.
- `update.json`: versionCode, versionName, minSdk, tên APK, kích thước và SHA-256.
- `SHA256SUMS.txt`: checksum để kiểm tra thủ công.
- `AeroPlay-source-v1.1.1.zip`: mã nguồn tương ứng, gồm giấy phép và ghi công tài nguyên.

Không chỉ upload APK vào một thư mục của repository: updater đọc **Releases** và cần cả APK lẫn `update.json` cùng release. Không sửa tay versionCode hoặc SHA-256 trong manifest. Script `scripts/create-release.mjs` đọc và kiểm tra APK thật trước khi tạo manifest.

## Chữ ký và GitHub Actions

Khóa ký tương thích với APK đã gửi trước đã được lưu riêng ở `.private/aeroplay-release.p12`; cấu hình local ở `.private/signing.properties`. Hai tệp này được Git bỏ qua và không nằm trong source ZIP. Hãy lưu bản sao riêng của chúng; không upload vào repository public.

GitHub Actions cần các secrets: `AEROPLAY_KEYSTORE_BASE64`, `AEROPLAY_KEYSTORE_PASSWORD`, `AEROPLAY_KEY_ALIAS`, `AEROPLAY_KEY_PASSWORD`. Workflow khôi phục khóa vào thư mục tạm của runner rồi xóa sau build; từ 2.0.4, bản phát hành tắt tài khoản YouTube và API-only mode để dùng NewPipe + Media3. Fingerprint công khai được ghim trong `signing-certificate-sha256.txt` để không phát hành nhầm APK khác khóa. Không tạo khóa mới nếu còn cần cập nhật đè lên bản người dùng đang cài.

Build local bằng JDK 17 hoặc 21, Android SDK 35, không cần OAuth Client ID cho bản phát trực tiếp:

```shell
./gradlew testDebugUnitTest assembleRelease \
  -PyoutubeAccountSyncEnabled=false -PyoutubeApiOnlyMode=false
node scripts/create-release.mjs
```

Trước lệnh Node, đặt `ANDROID_HOME` trỏ đến Android SDK. Node 22+ được hỗ trợ. Thư mục `dist/` nhận APK và manifest. Build debug không cần khóa release.

Nếu fork repo, workflow dùng tên kho của fork khi build; có thể truyền `-PgithubRepository=owner/repo`. Người dùng cũng có thể đổi kho trong Cài đặt, nhưng APK khác certificate sẽ bị từ chối.

## Tài liệu nền tảng

- [GitHub Releases REST API](https://docs.github.com/en/rest/releases/releases#get-the-latest-release)
- [GitHub Actions secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)
- [Android cho phép nguồn cài đặt](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls())
- [Android FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider)
