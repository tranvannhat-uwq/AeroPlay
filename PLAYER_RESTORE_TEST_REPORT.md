# AeroPlay 2.0.4 — Khôi phục phát trực tiếp

Theo lựa chọn của người dùng ngày 2026-11-16: tắt Google/YouTube Data API, khôi phục NewPipe + Media3. Hai Gradle properties trong workflow phát hành đều là false. Không đổi Room schema hoặc xóa lịch sử, yêu thích, playlist cục bộ.

## Đã kiểm tra trên máy phát triển

- Build Release đã ký thành công, certificate khớp fingerprint ghim của ứng dụng.
- 52 test chạy với `-Paeroplay.liveTests=true`: không lỗi, không bỏ qua.
- Kiểm tra mạng thật với NewPipe: trang chủ có video; tìm kiếm có kết quả và tải thêm được trang kế tiếp.
- Video mẫu công khai Big Buck Bunny (`aqz-KE-bpKQ`) trả metadata, 9 lựa chọn video và 5 luồng audio tại thời điểm kiểm tra.
- Test lần theo HLS master → media playlist → dữ liệu media thực và đọc được bytes, không chỉ dừng ở manifest. Không sử dụng OAuth token hoặc cookie tài khoản.
- Trong quá trình mở rộng test, request Range từng làm cắt URL ký trong manifest rồi gây HTTP 403; test đã được sửa để đọc manifest đầy đủ như trình phát HLS. Lần chạy cuối thành công.

## Còn cần kiểm tra trên điện thoại

- Cài đè 2.0.4 và thấy nguồn YouTube (NewPipe).
- Tìm/chọn video, phát hình và tiếng ngay trong trình phát AeroPlay; không mở WebView hoặc app YouTube.
- Chuyển chất lượng, tua, Next và radio nhạc cùng phong cách.
- Chuyển app, khóa màn hình, tai nghe và PiP.
- Lịch sử, yêu thích và playlist cục bộ còn nguyên sau cập nhật.

Không có điện thoại kết nối ADB trong phiên này. Kiểm tra HTTP/media bytes trên máy không xác nhận giải mã Media3, phát nền hoặc PiP trên thiết bị thật. Kết quả mạng là tại thời điểm kiểm tra, không đảm bảo tất cả video/khu vực/thời điểm.
