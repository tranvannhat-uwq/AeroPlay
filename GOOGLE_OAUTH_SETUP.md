# Google OAuth và YouTube Data API — AeroPlay

## Trạng thái cấu hình hiện tại

Mã nguồn dùng Credential Manager để nhận diện tài khoản Google và Google `AuthorizationClient` để xin quyền YouTube riêng, theo từng ngữ cảnh. Ứng dụng chỉ gửi scope:

```text
https://www.googleapis.com/auth/youtube.readonly
```

Không cần API key để đọc dữ liệu riêng tư; không có client secret trong ứng dụng. Web Client ID không phải bí mật, nhưng cần dùng đúng OAuth client loại **Web application** của project.

## Tạo thông tin xác thực

1. Tạo một Google Cloud project dành riêng cho API client AeroPlay.
2. Bật **YouTube Data API v3**.
3. Hoàn tất trang OAuth consent/Google Auth Platform: tên ứng dụng, thông tin hỗ trợ và phạm vi `youtube.readonly`. Trong trạng thái **Testing**, thêm địa chỉ Google cần thử vào danh sách test users. Trước khi phát hành cho người dùng ngoài nhóm thử nghiệm, kiểm tra yêu cầu xác minh OAuth của Google cho trạng thái hiện hành của scope và sản phẩm.
4. Tạo một OAuth client loại **Android** với package `com.aeroplay.app` và SHA-1 của từng chứng thư ký APK.
5. Tạo một OAuth client loại **Web application**. Dùng **Client ID** của Web application làm `youtubeOAuthWebClientId`; đây là server client ID mà Credential Manager dùng để tạo Google ID token. Không dùng Android client ID ở thuộc tính này. Không cần nhúng Web client secret vào app.

### SHA-1 trên máy hiện tại

`signingReport` của repository trả về:

```text
Debug SHA-1:  F1:09:FE:32:A1:27:1A:9C:89:5C:0A:FE:3C:6D:D3:3E:FB:E3:A7:FC
Release SHA-1: F1:09:FE:32:A1:27:1A:9C:89:5C:0A:FE:3C:6D:D3:3E:FB:E3:A7:FC
```

Hai giá trị giống nhau vì cấu hình ký release hiện tại dùng cùng chứng thư trên máy này. Nếu thay keystore/chứng thư hoặc phát hành qua Google Play App Signing, lấy SHA-1 của chứng thư thực tế tương ứng và khai báo thêm trong Google Cloud.

Để kiểm tra lại trên Windows:

```powershell
.\gradlew.bat signingReport
```

## Build APK có bật tài khoản YouTube

Trong thư mục dự án, nhập Web Client ID tại prompt dưới đây. Client ID là định danh công khai và được nhúng vào `BuildConfig` trong APK vì Credential Manager cần nó; nó không phải client secret. Giá trị chỉ được truyền làm Gradle property cho lần build này, không ghi vào mã nguồn, file Gradle properties, Git hoặc log. **Không nhập Client Secret, access token hay refresh token.**

```powershell
$youtubeWebClientId = Read-Host "Web OAuth Client ID (.apps.googleusercontent.com)"
if ($youtubeWebClientId -notmatch '^[A-Za-z0-9._-]+\.apps\.googleusercontent\.com$') {
  throw "Web Client ID format is invalid."
}
.\gradlew.bat assembleDebug `
  "-PyoutubeOAuthWebClientId=$youtubeWebClientId" `
  -PyoutubeAccountSyncEnabled=true `
  -PyoutubeApiOnlyMode=true
$youtubeWebClientId = $null
```

APK được tạo tại `app/build/outputs/apk/debug/app-debug.apk`.

`app/build.gradle.kts` đọc ba Gradle properties này và ghi kết quả vào các trường `BuildConfig.YOUTUBE_ACCOUNT_SYNC_ENABLED`, `YOUTUBE_API_ONLY_MODE` và `YOUTUBE_OAUTH_WEB_CLIENT_ID`; app đọc các trường đó khi chạy. Build dừng nếu bật tài khoản nhưng không bật API-only hoặc ID không đúng dạng. Nếu ID trống, feature tài khoản tự tắt. `youtubeApiOnlyMode=true` khóa nguồn NewPipe và chặn phát YouTube qua Media3.

Không đưa ID tùy chỉnh, secret, token hay file signing vào Git. Nếu muốn đặt thuộc tính trong file local, dùng một file không được commit và xác nhận `.gitignore` bỏ qua file đó; không chia sẻ file có thông tin xác thực.

## Chi phí và quota

YouTube Data API giới hạn theo quota units thay vì tính tiền theo từng request trong tài liệu hiện hành. Quota mặc định hiện được công bố là 10.000 units/ngày cho phần lớn endpoint và mỗi trang dữ liệu cần một request; mức thực tế của project có thể thay đổi, hãy xem **Google Cloud Console → APIs & Services → YouTube Data API v3 → Quotas**. Việc bật Billing cho project có thể tính phí nếu project còn dùng API/service khác có tính phí; kiểm tra trang Billing của project trước khi bật dịch vụ khác. [Quota và đơn vị method](https://developers.google.com/youtube/data/determine_quota_cost?hl=en) · [Google APIs và billing](https://support.google.com/googleapi/answer/6158867)

## Checklist kiểm thử trên điện thoại thật

Trước khi cài, xác nhận Android OAuth Client trong Google Cloud có package `com.aeroplay.app` cùng SHA-1 của APK ký cuối. SHA-1 debug hiện tại được ghi ở trên. Thêm tài khoản Google cần thử vào test users nếu consent screen đang ở Testing. Cấu hình Cloud không thể được đọc/đối chiếu tự động từ repository này; cần kiểm tra đúng project và hai OAuth clients trong Cloud Console.

1. Cài APK Debug đã build trên điện thoại có Google Play services. Cài đè app hiện tại; không gỡ app, không dùng **Clear storage/data**. APK cần cùng application ID và chứng thư ký với bản đang cài để Android cho phép cập nhật và giữ dữ liệu Room.
2. Mở **Cài đặt → Tài khoản YouTube**, đọc thông báo quyền riêng tư/điều khoản, rồi chấp nhận.
3. Nhấn **Đăng nhập Google**, chọn tài khoản đúng. Xác nhận màn hình Google chỉ yêu cầu quyền đọc YouTube `youtube.readonly`; hoàn tất consent.
4. Kiểm tra tên/ảnh kênh. Nếu Google account có nhiều channel, chọn channel cần xem.
5. Nhấn **Làm mới dữ liệu YouTube**; kiểm tra một video đã thích mà bạn nhận ra.
6. Mở từng playlist; kiểm tra tên playlist và danh sách video trong đó.
7. Kiểm tra danh sách kênh đăng ký có các kênh đã đăng ký.
8. Nhấn **Làm mới dữ liệu YouTube** lần nữa; xác nhận trạng thái thời gian cập nhật và dữ liệu được tải lại.
9. Nhấn **Đăng xuất**; xác nhận hồ sơ và cache YouTube biến mất khỏi màn hình. Đăng nhập lại rồi làm mới để kiểm tra có thể cấp quyền lại.
10. Mở một video từ danh sách; xác nhận AeroPlay mở màn hình trình phát YouTube nhúng; chạm Play trong khung để phát. Thử khóa màn hình/chuyển ứng dụng: video phải dừng. Quay lại tải lại khung nhúng, thử Back để về AeroPlay. Video không cho nhúng có nút mở YouTube. Trong chế độ này video YouTube không phát bằng NewPipe/Media3; thử một nguồn media khác để đảm bảo trình phát chung vẫn hoạt động.

Nếu gặp lỗi OAuth, log Debug chỉ ghi tên lớp exception và mã trạng thái Google nếu có; không ghi email, nội dung exception, token hay dữ liệu tài khoản. Mã lỗi `10` thường cần kiểm tra lại package/SHA-1 và loại Android OAuth Client; lỗi consent cần kiểm tra Web Client ID, scope, project đang bật YouTube Data API v3 và danh sách test users. Không gửi log chứa thông tin tài khoản công khai.

Checklist này là quy trình kiểm thử; đăng nhập, dữ liệu và thu hồi quyền chỉ được xác nhận sau khi hoàn thành trực tiếp trên điện thoại/tài khoản thật.
