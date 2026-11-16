# Giới hạn và ranh giới chính sách YouTube

## Có thể đọc qua API đã cấp quyền

- Thông tin channel mà YouTube Data API trả về cho `channels.list(mine=true)`.
- Video đã thích qua `videos.list(myRating=like)`.
- Playlist sở hữu/được API trả về qua `playlists.list(mine=true)` và từng playlist item qua `playlistItems.list`.
- Subscriptions qua `subscriptions.list(mine=true)`.

Đây là dữ liệu thật chỉ sau khi người dùng đăng nhập, chấp nhận thông báo trong app và Google cấp quyền `youtube.readonly`. Không có danh sách mẫu thay thế khi tài khoản trống/lỗi.

## Không được hỗ trợ trong tính năng này

- YouTube Watch History và Watch Later mặc định: không có endpoint công khai dùng ở đây. AeroPlay giữ lịch sử xem và Xem sau riêng, không đọc cookie, endpoint nội bộ, `WL`, hay dữ liệu từ Google Takeout.
- Like/Unlike, sửa playlist, thêm/xóa playlist item, Subscribe/Unsubscribe: không gọi API ghi, không hàng đợi thao tác và không đồng bộ ngược.
- Phát video/âm thanh YouTube bằng luồng do NewPipe lấy hoặc Media3 trong chế độ API-only. API này chỉ cung cấp dữ liệu tài khoản/metadata; OAuth token không được chuyển vào NewPipe, resolver hoặc player. Mở video là hành động riêng của người dùng: từ 2.0.3, trình phát YouTube nhúng chính thức trong WebView của AeroPlay xử lý phát. Video chặn nhúng có nút mở app YouTube/trình duyệt.
- Quota vượt hạn mức, account không có channel API trả về, hoặc tài nguyên không được quyền đọc sẽ báo trạng thái/lỗi và giữ cache còn hạn dùng khi có thể.

## Xung đột với trình phát hiện có

YouTube Developer Policies hạn chế những hành vi làm thay đổi/né quảng cáo hoặc tính toàn vẹn playback, phát nền YouTube trong client, tách audio, cũng như dùng phương thức không được tài liệu hóa để truy cập dữ liệu. Vì thế việc đặt OAuth vào module khác không đủ để làm tổ hợp đó phù hợp.

Code NewPipe/Media3 vẫn ở repository và APK mặc định giữ chế độ cũ khi API account sync tắt. `youtubeApiOnlyMode=true` là build mode riêng: ẩn/khóa nguồn NewPipe, chặn metadata và playback YouTube trong Media3, vẫn giữ Media3 cho nguồn khác. Bản account-enabled bắt buộc API-only mode; không tuyên bố rằng APK đã qua kiểm toán hoặc được YouTube chấp thuận.

## Quyền riêng tư, thương hiệu và phân phối

- Màn hình yêu cầu chấp nhận thông báo quyền riêng tư trước đăng nhập, có liên kết tới YouTube Terms, Google Privacy Policy, trang thu hồi quyền và kênh liên hệ.
- Authorized API data được cache cục bộ tối đa 30 ngày rồi xóa/làm mới; đăng xuất xóa dữ liệu đã lưu và yêu cầu Google revoke.
- Trang API có logo YouTube chính thức liên kết tới YouTube. Video/kênh mở bằng hành động người dùng trong app YouTube/trình duyệt, không giả làm player chính thức.
- Việc đăng ký Cloud credentials, OAuth consent/testing users, OAuth verification, quota audit, privacy disclosures và mọi yêu cầu YouTube áp dụng khi phân phối đều thuộc cấu hình/chấp thuận của chủ dự án; code build thành công không phải bằng chứng đã được duyệt.

Tham khảo: [YouTube Developer Policies](https://developers.google.com/youtube/terms/developer-policies), [YouTube Branding Guidelines](https://developers.google.com/youtube/terms/branding-guidelines), [YouTube Data API Authentication](https://developers.google.com/youtube/data/guides/authentication), [Credential Manager](https://developers.google.com/identity/android-credential-manager), [AuthorizationClient](https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/AuthorizationClient).

## Trình phát chính thức từ 2.0.3

Trang chủ/tìm kiếm đọc metadata qua YouTube Data API; video dùng URL embed chính thức và Referer HTTPS theo application ID. WebView không nhận OAuth token, không lấy media URL và không ẩn quảng cáo hoặc điều khiển YouTube. Đóng/ra nền sẽ unload WebView để dừng phát. Không hỗ trợ phát nền, audio-only hay PiP cho trình phát YouTube này. Khả năng phát từng video phải kiểm thử trên thiết bị thật; chủ video có thể chặn nhúng.
