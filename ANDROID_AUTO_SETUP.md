# AeroPlay trên Android Auto — bản 2.0.6

Ứng dụng chạy trên điện thoại Android và cung cấp thư viện âm thanh cho Android Auto trên màn hình xe. Đây không phải APK cài riêng lên Android Automotive OS và không phải tính năng chiếu video lên xe.

## Cài và kết nối

1. Cài đè APK 2.0.6 lên AeroPlay hiện tại, cùng package/chữ ký; không gỡ app hoặc xóa dữ liệu.
2. Mở AeroPlay trên điện thoại trước khi kết nối. Tạo playlist hoặc thêm bài vào **Yêu thích** cục bộ để có nội dung khi vào xe. Nội dung **Trên thiết bị** dùng các file đã được nhập vào AeroPlay; bản này không tự quét toàn bộ nhạc điện thoại.
3. Cập nhật Android Auto trên điện thoại; kết nối điện thoại với đầu xe hỗ trợ Android Auto qua USB hoặc kết nối không dây của xe.
4. Trong Android Auto trên điện thoại, vào **Customize launcher / Tùy chỉnh trình chạy** và bật AeroPlay nếu nó xuất hiện. Trên màn hình xe, mở launcher và chọn AeroPlay.
5. Chọn Yêu thích, Nghe gần đây, Playlist, Trên thiết bị hoặc Khám phá nhạc. Bản này phát âm thanh, hiển thị tên bài/kênh/ảnh; không hiển thị video trong giao diện xe.

## Nếu AeroPlay cài từ GitHub chưa hiện

Theo tài liệu kiểm thử media app của Android, APK cài ngoài Google Play có thể cần **Unknown sources** trong Developer settings của Android Auto:

1. Trên điện thoại, mở **Cài đặt → Android Auto** (có thể tìm “Android Auto” trong Cài đặt).
2. Kéo xuống **Version / Phiên bản**, mở thông tin phiên bản rồi chạm phần phiên bản/quyền 10 lần; xác nhận bật chế độ nhà phát triển.
3. Mở menu ba chấm → **Developer settings / Cài đặt nhà phát triển** → **Unknown sources / Nguồn không xác định**.
4. Ngắt/kết nối lại xe, kiểm tra Tùy chỉnh trình chạy và danh sách ứng dụng media. Tên mục có thể khác theo phiên bản Android Auto.

Không cần mở Google Sign-In trong AeroPlay: tích hợp Google/YouTube Data API vẫn tắt. Nguồn trực tuyến dùng NewPipe hiện có; quyền truy cập video/luồng còn phụ thuộc mạng và nhà cung cấp.

## Ứng dụng nhạc mặc định

Chọn AeroPlay trực tiếp trong launcher/media của Android Auto để bắt đầu nghe. Android Auto cũng có vùng ứng dụng media dùng gần đây. Nếu bật tùy chọn **Start music automatically** của Android Auto, kiểm tra hành vi kết nối lại thực tế với đầu xe; ứng dụng không tự sửa lựa chọn này.

“Dịch vụ nhạc mặc định” cho Assistant/Gemini là cấu hình khác do Google quản lý, không phải quyền Android mà AeroPlay có thể tự đăng ký. Chỉ chọn AeroPlay làm mặc định nếu nó có trong danh sách được Google cung cấp. Hỗ trợ callback tìm/phát theo truy vấn đã được thêm; câu lệnh “Phát [tên bài] trên AeroPlay” chỉ hoạt động nếu trợ lý nhận diện và chuyển yêu cầu tới ứng dụng. Chưa xác nhận nhận diện giọng nói/default provider với Assistant/Gemini trên thiết bị thật.

## Phạm vi thư viện và hàng đợi

- Đọc dữ liệu Room hiện có, không đổi schema hoặc xóa yêu thích/playlist/lịch sử.
- Tối đa 100 mục mỗi thư mục, 50 kết quả mỗi truy vấn; có xử lý pagination và request không phân trang của MediaBrowser cũ.
- Sau khi chọn bài từ xe, phân giải luồng mới cho bài đó; chuẩn bị bài kế tiếp trong nền. Nút Next có thể chưa khả dụng trong lúc phân giải bài tiếp theo. Luồng lỗi sẽ được bỏ qua khi chuẩn bị bài kế tiếp; lỗi bài được chọn trả về MediaSession/Android Auto.
- Chọn bài từ xe tắt track video trong ExoPlayer. Nếu HLS không có rendition audio riêng, có thể vẫn cần tải các segment chứa cả hình và tiếng; giao diện xe chỉ phát âm thanh.
- Phiên phát xe dùng chung service/ExoPlayer với điện thoại; các lệnh Play/Pause/Next/Previous và thông tin bài được đồng bộ qua MediaSession. Khi bắt đầu từ xe, màn hình điện thoại không tự bật video toàn màn hình.
- Có callback phát tiếp từ lịch sử khi host yêu cầu resumption. Không lưu URL media đã ký vào thư viện để dùng sau khi khởi động lại.

## Kiểm thử

Đã biên dịch MediaLibraryService, manifest media và test Android `AndroidAutoLibraryTest`. Unit tests kiểm tra ID bền vững/nguồn riêng biệt, từ chối ID không hợp lệ, pagination, request legacy `Int.MAX_VALUE` và chống tràn số. Build/certificate/APK được kiểm tra trong quy trình release.

Kết quả trên máy ngày 2026-11-17: build Release có ký thành công; 56 unit test, 0 failures/errors, 3 live-network tests bỏ qua; `compileDebugAndroidTestKotlin` thành công. Không chạy test thiết bị, do đó chưa xác nhận cold-start/browse/âm thanh thực tế trên Android Auto.

Chưa chạy `connectedDebugAndroidTest` do chưa có thiết bị ADB. Chưa chạy Desktop Head Unit, Assistant/Gemini hoặc đầu xe thật. Cần thử:

1. Khởi động lạnh: đóng màn hình AeroPlay, kết nối xe, mở AeroPlay từ launcher xe.
2. Thư viện trống và có dữ liệu; duyệt mục và mở playlist.
3. Chọn bài, nghe có âm thanh, Pause/Play, Next/Previous, nút vô lăng và thông tin bài.
4. Đổi bài từ điện thoại rồi từ xe; không phát hai luồng cùng lúc hoặc tự bật video trên xe.
5. Tìm kiếm; thử câu lệnh có và không có tên bài nếu trợ lý nhận diện AeroPlay.
6. Ngắt/kết nối lại, khóa điện thoại, mất mạng, bài bị gỡ/không truy cập, cuộc gọi, rút USB/Bluetooth.
7. Kiểm tra hồi quy video/toàn màn hình/giữ sáng/PiP và phát nền trên điện thoại.

Nguồn chính thức: [Android Auto với MediaLibraryService](https://developer.android.com/media/implement/surfaces/cars), [kiểm thử và Unknown sources](https://developer.android.com/training/cars/testing), [Desktop Head Unit](https://developer.android.com/training/cars/testing/dhu), [điều khiển giọng nói](https://developer.android.com/media/implement/assistant), [nghe media trên Android Auto](https://support.google.com/androidauto/answer/6348327).
