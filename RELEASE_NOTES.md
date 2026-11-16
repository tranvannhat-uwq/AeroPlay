AeroPlay 2.0.5

- Giữ màn hình sáng khi xem video trong trình phát đầy đủ (dọc/ngang), kể cả lúc chờ tải tiếp, để tránh tự tắt theo timeout của điện thoại.
- Nhả giữ sáng khi tạm dừng, phát xong, đóng/thu nhỏ trình phát, chuyển audio-only, PiP hoặc ra nền. Không thay đổi cài đặt timeout hoặc dùng wake lock.

Cài đè để giữ dữ liệu. Cần thử trên điện thoại với timeout 1 phút: phát hơn 2 phút không chạm màn hình, rồi Pause và xác nhận timeout hoạt động trở lại.

AeroPlay 2.0.4

- Khôi phục phát video YouTube trực tiếp trong AeroPlay bằng NewPipe + Media3, hàng đợi, chọn chất lượng, phát nền và PiP.
- Nguồn mặc định khi nâng cấp là YouTube (NewPipe); vẫn có thể tự chọn Nguồn mở hoặc video từ thiết bị.
- Tắt tích hợp Google Sign-In/YouTube Data API và màn hình tài khoản trong bản phát hành này theo lựa chọn của người dùng. Lịch sử, yêu thích và playlist cục bộ được giữ nguyên; dữ liệu tài khoản YouTube không tiếp tục đồng bộ.
- Trình phát nhúng chỉ dành cho cấu hình API-only, không được dùng trong bản này. Nguồn trích xuất vẫn phụ thuộc khả năng truy cập tại từng thời điểm.

Cài đè bản cũ để giữ dữ liệu. Android 8 trở lên.

AeroPlay 2.0.3

- Video đã thích, video trong playlist tài khoản và video YouTube trong thư viện mở trình phát YouTube chính thức ngay trong AeroPlay.
- Trang chủ và tìm kiếm dùng YouTube Data API với tài khoản đã cấp quyền; hiển thị nguồn YouTube, không tự thay thế bằng catalog Nguồn mở. Có thể chọn Nguồn mở riêng trong Cài đặt.
- Trình phát nhúng giữ điều khiển của YouTube, dừng khi khóa màn hình/chuyển ứng dụng. Video bị chủ sở hữu chặn nhúng vẫn cần mở bằng YouTube.
- Không dùng OAuth token để lấy luồng media. Khả năng phát nhúng trên điện thoại thật cần được xác nhận sau cài đặt.

Cài đè để giữ dữ liệu. Android 8 trở lên.

AeroPlay 2.0.2

- Khi chế độ API-only chặn phát YouTube trong AeroPlay, thẻ lỗi có nút mở đúng video bằng ứng dụng YouTube hoặc trình duyệt.

Cài đè bản cũ để giữ dữ liệu nếu chữ ký APK khớp. Android 8 trở lên.

AeroPlay 2.0.1

- Đề xuất radio nhạc theo phong cách bài đang phát; giảm lặp cùng ca khúc/kênh và bổ sung gợi ý bài phổ biến, playlist khi nguồn trả dữ liệu.
- Tích hợp tài khoản YouTube chỉ đọc: video đã thích, playlist, video trong playlist và kênh đăng ký; làm mới/xóa cache khi đăng xuất.
- Bản tài khoản dùng YouTube Data API và khóa phát YouTube qua NewPipe/Media3; mở video bằng YouTube hoặc trình duyệt. Nguồn media khác vẫn dùng trình phát AeroPlay.
- Đăng nhập hiện cần OAuth Consent Screen ở trạng thái phù hợp và tài khoản thử nghiệm được khai báo. Luồng Google và dữ liệu tài khoản thật chưa được kiểm thử trên điện thoại trong quá trình tạo bản này.

Cài đè bản cũ để giữ dữ liệu nếu chữ ký APK khớp. Android 8 trở lên.

AeroPlay 2.0.0

- Tìm kiếm hiểu truy vấn tiếng Việt có dấu/không dấu và nhận diện ý định mới nhất, phổ biến, âm nhạc, kênh, playlist, tên riêng.
- Xếp hạng cục bộ theo mức khớp trước; dùng ngày đăng và lượt xem thật khi NewPipe cung cấp. Có lọc nội dung, sắp xếp và lọc khoảng thời gian trên kết quả đã tải.
- Phân trang giữ thứ tự những mục đã xem và loại video trùng theo nguồn + ID.
- Đề xuất học trên thiết bị từ lịch sử, thời lượng xem, video xem gần hết, yêu thích, playlist, tìm kiếm và phản hồi; thêm Tiếp tục xem, Không quan tâm, chặn kênh, Đã xem, Xem sau.
- Cập nhật Room không xóa dữ liệu cũ; Đặt lại lịch sử cá nhân hóa giữ nguyên yêu thích và playlist.
- Bản này không dùng máy chủ hoặc API trả phí. Nguồn YouTube vẫn phụ thuộc khả năng truy cập của NewPipe.

Cài đè bản cũ để tiếp tục dùng lịch sử, yêu thích, danh sách phát và cài đặt hiện có. Android 8 trở lên.

AeroPlay 1.2.1

- Khi xem ngang, video phủ toàn màn hình và ẩn thanh trạng thái, thanh điều hướng; vuốt từ cạnh để hiện thanh tạm thời. Quay dọc hoặc thu nhỏ trình phát sẽ khôi phục thanh hệ thống.
- Nút điều khiển tự ẩn sau 4 giây khi video đang phát; chạm video để hiện lại.
- Ô tìm kiếm giữ một dòng và chiều cao ổn định khi chưa nhập.
- Chọn video trực tiếp từ Ảnh/Thư viện ảnh của thiết bị. Hướng dẫn nhập video tự thu gọn sau lần thêm đầu tiên.

Cài đè bản cũ để giữ lịch sử, yêu thích, danh sách phát, video trong thư viện và cài đặt. Android 8 trở lên.

AeroPlay 1.2.0

- Giao diện Liquid Glass sáng với thanh điều hướng ổn định và chuyển cảnh mềm mại.
- Vuốt xuống trên video để thu thành cửa sổ nổi có hình ảnh đang phát. Chạm hoặc vuốt lên để mở rộng.
- Mục Dành cho bạn gợi ý theo tên video, kênh, số lần xem và thời điểm xem.
- Thêm nhiều video từ Ảnh/Tệp hoặc chia sẻ sang AeroPlay. Hỗ trợ phát ngoại tuyến, phát nền và khi tắt màn hình.
- Video từ thiết bị có ảnh thu nhỏ, tìm kiếm, đổi tên, phát tất cả và xóa bản sao trong AeroPlay.
- Sửa bố cục Thư viện và hiển thị khi tăng cỡ chữ.

Cài đè để tiếp tục dùng lịch sử, yêu thích, danh sách phát và cài đặt hiện có. Android 8 trở lên.

Nguồn Chuẩn Mở gợi ý trong danh mục phim có sẵn. Nguồn Thử nghiệm lấy nội dung tương tự từ YouTube qua NewPipe.
