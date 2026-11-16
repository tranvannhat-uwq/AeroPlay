# Kiến trúc tài khoản YouTube AeroPlay

## Luồng đăng nhập và quyền

1. Android Credential Manager + Google ID option xác thực danh tính Google. ID token chỉ dùng để lấy email/tên/ảnh đại diện; không dùng ID token như access token YouTube.
2. Khi cần đọc dữ liệu, Google AuthorizationClient yêu cầu riêng scope `youtube.readonly` và có thể mở consent screen.
3. Access token chỉ được giữ trong bộ nhớ cho thao tác hiện tại. YouTubeDataApiClient gửi token trong header Bearer tới `https://www.googleapis.com/youtube/v3/` qua HTTPS. Token không được lưu Room/DataStore, đưa vào URL hoặc log.
4. Repository phân vùng cache theo định danh tài khoản và YouTube channel ID. Google email và channel ID được lưu riêng; app không xem chúng là cùng một định danh.

## API chỉ đọc

Mọi request của YouTubeDataApiClient là HTTP GET:

| Dữ liệu | API request |
| --- | --- |
| Kênh được cấp quyền | `channels.list(mine=true)` |
| Video đã thích | `videos.list(myRating=like)` |
| Playlist của tài khoản | `playlists.list(mine=true)` |
| Video trong playlist | `playlistItems.list(playlistId=...)` |
| Kênh đăng ký | `subscriptions.list(mine=true)` |

API client tự đi hết pagination tới tối đa 100 trang mỗi nhóm dữ liệu. Nếu còn `nextPageToken` sau giới hạn an toàn, thao tác báo lỗi thay vì âm thầm hiển thị thư viện thiếu. Playlist item được tải khi người dùng mở playlist và có thể làm mới riêng từng playlist để không gọi API hàng loạt không cần thiết.

Không có endpoint `videos.rate`, `playlists.insert/update/delete`, `playlistItems.insert/delete` hay `subscriptions.insert/delete`; không có outbox, retry cho thao tác ghi, hoặc conflict resolver.

## Cache và xóa dữ liệu

- Likes, playlist, playlist item, subscription và channel metadata có timestamp cache.
- Authorized API data bị xóa khi quá 30 ngày; ứng dụng dọn cache khi khởi động và trước lần refresh.
- Cache playlist item được dùng lại tối đa 24 giờ; người dùng có thể tải lại theo playlist.
- Thumbnail YouTube trong màn hình này tải khi hiển thị nhưng tắt Coil memory/disk cache, tránh giữ ảnh API ngoài TTL của dữ liệu.
- Access token không được lưu lại. Khi API trả 401, app xóa cache của tài khoản và channel metadata, sau đó yêu cầu cấp quyền lại.
- Đăng xuất yêu cầu Google revoke, xóa hồ sơ Google/channel và mọi cache đã lưu cho tất cả channel của tài khoản. Nếu yêu cầu revoke thất bại, cache cục bộ vẫn bị xóa và giao diện báo trạng thái đó.
- Chấp nhận quyền riêng tư được lưu riêng theo phiên bản chính sách; đăng xuất không xóa quyết định đã chấp nhận.
- Không gửi dữ liệu tài khoản lên máy chủ AeroPlay.

## Giao diện và phát nội dung

Trang Cài đặt → Tài khoản YouTube hiển thị profile, likes, playlist, playlist items và subscriptions do API trả về. Trang này có logo YouTube chính thức liên kết tới YouTube. Người dùng chạm video/kênh sẽ mở ứng dụng YouTube hoặc trình duyệt thông qua `ACTION_VIEW`.

Lịch sử xem và Xem sau hiện tại vẫn là các tính năng cục bộ AeroPlay; không hiển thị chúng như dữ liệu YouTube. Watch History và Watch Later mặc định không được truy cập qua API triển khai này.

## Cờ tương thích chính sách

`youtubeApiOnlyMode=true` khóa nguồn NewPipe/YouTube, truy vấn NewPipe, lấy metadata YouTube cho Media3 và phát URL YouTube qua Media3. Người dùng vẫn có thể mở video qua YouTube chính thức; các nguồn cục bộ/nguồn được phép vẫn dùng trình phát AeroPlay.

Mặc định repository không bật tài khoản API trong APK chưa cấu hình. Bản mặc định vẫn giữ hành vi ứng dụng trước đây và NewPipe; đây không phải xác nhận rằng cách phát YouTube không chính thức được YouTube chấp thuận. Chỉ build account sync cùng API-only mode mới bật hai thành phần tài khoản/API.
