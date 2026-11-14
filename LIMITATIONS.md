# LIMITATIONS – Giới Hạn Kỹ Thuật, Pháp Lý & Phân Phối

Tài liệu này công bố minh bạch các giới hạn kỹ thuật, rủi ro từ sự thay đổi của bên thứ ba, giấy phép mã nguồn mở và phạm vi sử dụng của ứng dụng **AeroPlay**.

---

## 1. Phạm Vi Sử Dụng & Hạn Chế Phân Phối

1. **Ứng dụng nguyên mẫu cá nhân (Personal Prototype):**
   - AeroPlay được xây dựng như một dự án kỹ thuật cá nhân nhằm kiểm chứng kiến trúc phát đa phương tiện hiện đại trên Android (Media3, MediaSessionService, PiP, Room, Jetpack Compose).
   - **Không mặc định đủ điều kiện phát hành lên Google Play Store:** Việc bật chế độ trích xuất luồng phát không chính thức và phát nền từ nguồn bên thứ ba có thể vi phạm chính sách Thiết bị và Mạng của Google Play nếu phân phối thương mại/công khai.
2. **Không cam kết chặn quảng cáo 100%:**
   - AeroPlay hoàn toàn **không tự chèn bất kỳ quảng cáo nào** của riêng ứng dụng vào trải nghiệm người dùng.
   - Tuy nhiên, AeroPlay **không hứa hẹn chặn hoàn toàn quảng cáo YouTube** trong mọi kịch bản và không can thiệp, sửa đổi hay chặn quảng cáo trên ứng dụng YouTube chính thức hoặc trình phát YouTube IFrame/API chính thức.

---

## 2. Rủi Ro Từ Thay Đổi Của YouTube (Experimental Extractor Source)

Chế độ **Experimental Extractor Source** dựa trên thư viện mã nguồn mở `NewPipeExtractor` (`v0.27.1`). Do không sử dụng API trả phí có khóa xác thực chính thức, chế độ này có các giới hạn cố hữu:

1. **Thay đổi cấu trúc trang & thuật toán giải mã chữ ký:**
   - Khi YouTube cập nhật mã JavaScript của trình phát hoặc cấu trúc phản hồi InnerTube, bộ giải mã trong `NewPipeExtractor` có thể trả về lỗi `ParsingException` hoặc `ExtractionException` cho đến khi cộng đồng NewPipe phát hành bản vá mới.
2. **Giới hạn IP, CAPTCHA (HTTP 429) & Chống Bot (PoToken / HTTP 403):**
   - Trong một số mạng hoặc khu vực, máy chủ nguồn có thể yêu cầu xác thực `reCAPTCHA` (`ReCaptchaException`) hoặc từ chối kết nối luồng phát.
   - Tuân thủ nguyên tắc kỹ thuật & pháp lý, AeroPlay **không sử dụng kỹ thuật vượt CAPTCHA tự động, không giả mạo token tài khoản người dùng và không tạo URL giả**. Khi xảy ra lỗi, ứng dụng hiển thị nguyên nhân kỹ thuật rõ ràng và cho phép người dùng chuyển sang **Nguồn Chuẩn Mở (Demo / Open Media Source)**.
3. **Nội dung giới hạn độ tuổi, khu vực địa lý, riêng tư hoặc có DRM:**
   - Các video yêu cầu đăng nhập xác minh độ tuổi (`AgeRestrictedContentException`), bị chặn theo quốc gia (`GeographicRestrictionException`), video hội viên trả phí (`PaidContentException`) hoặc nội dung bảo vệ bằng **DRM (Widevine)** sẽ **không được phát**. AeroPlay không thực hiện bất kỳ hành vi giải mã hay vượt rào DRM nào.

---

## 3. Bảo Mật, Quyền Riêng Tư & Quyền Hệ Thống

AeroPlay tuân thủ nguyên tắc tối thiểu hóa quyền (Least Privilege):
- **Các quyền hệ thống sử dụng:** `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` `POST_NOTIFICATIONS` (trên Android 13+) và `REQUEST_INSTALL_PACKAGES` cho cập nhật APK từ GitHub có xác nhận của Android.
- **Tuyệt đối KHÔNG sử dụng:**
  - Quyền Root hoặc can thiệp hệ thống.
  - `AccessibilityService` (Dịch vụ trợ năng).
  - `VpnService` hoặc proxy chặn gói tin hệ thống.
  - `SYSTEM_ALERT_WINDOW` (Vẽ đè lên ứng dụng khác – AeroPlay chỉ dùng Android Picture-in-Picture API chuẩn).
  - Không thu thập, không lưu trữ cookie đăng nhập, mật khẩu hay token tài khoản của người dùng.

---

## 4. Giấy Phép Thư Viện & Nghĩa Vụ GPLv3

- Thư viện `com.github.teamnewpipe:NewPipeExtractor` được phát hành theo giấy phép **GNU General Public License v3.0 (GPLv3)**.
- Theo điều khoản copyleft của GPLv3:
  - Khi phân phối bản thực thi (APK) của ứng dụng có liên kết với `NewPipeExtractor`, bạn bắt buộc phải giữ nguyên thông báo bản quyền, đính kèm văn bản giấy phép GPLv3 (xem file [LICENSE](LICENSE)) và cung cấp mã nguồn tương ứng theo đúng điều khoản của GPLv3.
- Các thư viện còn lại (`AndroidX Media3`, `Jetpack Compose`, `Room`, `DataStore`, `Hilt`, `OkHttp`, `Coil`) sử dụng giấy phép **Apache License 2.0**.

---

## 5. Các Chức Năng Chưa Mở Rộng Trong Phiên Bản Nguyên Mẫu (MVP)

- **Đồng bộ đám mây đa thiết bị:** Toàn bộ lịch sử xem, vị trí phát, danh sách yêu thích và playlist chỉ lưu cục bộ trong Room Database trên điện thoại để đảm bảo chi phí hạ tầng 0đ và quyền riêng tư tuyệt đối.
- **Tải video ngoại tuyến (Offline Video Downloader):** AeroPlay không cung cấp tính năng tải xuống tệp video từ YouTube lưu vào bộ nhớ máy nhằm tuân thủ quy định bản quyền nội dung.
- **Bình luận trực tiếp (Live Chat) & Đăng ký kênh (Channel Subscriptions):** Phiên bản hiện tại tập trung vào trải nghiệm tìm kiếm, phát video/nhạc nền, PiP và quản lý playlist cá nhân.
