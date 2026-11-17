# Danh sách kết hợp tự động

## Hành vi

- Trang chủ có mục **Bản phối dành cho bạn** với ảnh ghép từ nội dung thật trong danh sách.
- Khi chưa có lịch sử/bài nhạc phù hợp, hiển thị **Bản phối khám phá** từ ứng viên của nguồn, không gán nhạc phổ biến thành sở thích đã biết của người nghe.
- AeroPlay tự nhóm lại khi lịch sử, yêu thích, playlist cục bộ, bài đang phát hoặc kết quả đề xuất thay đổi. Kéo làm mới Trang chủ để yêu cầu tải lại gợi ý theo cơ chế hiện có.
- Tối đa 5 bản phối: sở thích/bài hiện tại, tối đa 3 thể loại và các bài phù hợp có nhiều lượt xem. Mỗi bản phối có 3–25 bài sau khi lọc. Danh sách không đủ 3 bài khác nhau không hiển thị.
- Thông tin thể loại lấy từ tiêu đề, tên kênh và mô tả; đây là suy luận bằng quy tắc, không phân tích âm thanh và không sử dụng thuật toán/tài khoản cá nhân hóa của YouTube.
- Ưu tiên bài khác tên, loại các phiên bản được nhận diện là cùng bài, tối đa 2 bài mỗi kênh; giới hạn kênh là gần đúng cho nghệ sĩ, vì một nghệ sĩ có thể xuất hiện trên nhiều kênh.
- Tôn trọng kênh bị chặn và phản hồi Không quan tâm/Đã xem theo hệ thống đề xuất hiện có. Không trộn nội dung giữa các nguồn. Không bổ sung dữ liệu giả để lấp đầy danh sách.
- Lượt xem lấy từ metadata nguồn, chỉ xếp phổ biến khi có số đếm dương; không gọi là bảng xếp hạng toàn YouTube.
- Tìm ứng viên nhạc ưu tiên MV/audio riêng lẻ; bản phối thông thường lọc video quá 20 phút để tránh đầy tuyển tập dài. Nếu người nghe chủ động chọn nội dung dài, ngưỡng thời lượng được nới theo bài đó.
- Khi chọn nguồn Chuẩn Mở, mục hiển thị **Danh sách kết hợp / Khám phá Chuẩn Mở**, chứa video trong danh mục thật; không gán phim kiểm thử thành nhạc.

## Mở và phát

Bấm thẻ để xem nội dung, chọn một bài hoặc **Phát bản phối**. Hàng đợi là snapshot của danh sách vừa mở, không thay đổi trong lúc đang nghe khi gợi ý Trang chủ cập nhật. Nếu bài chọn đang phát/tạm dừng, thay hàng đợi và tiếp tục cùng player thay vì bỏ qua danh sách hoặc khởi tạo player thứ hai.

Các bản phối nhạc dùng cơ chế tiếp tục radio sau cuối danh sách khi cài đặt tự phát bài tương tự cho phép. Chuẩn Mở phát danh sách hữu hạn. Đây là các danh sách sinh động trong ứng dụng, không tự tạo hàng loạt playlist Room, không ghi playlist lên YouTube; schema và dữ liệu hiện có không thay đổi.

## Tải và lỗi

Tái sử dụng ứng viên và cache 20 phút của phần đề xuất; việc tạo/mở thẻ bản phối không phát sinh truy vấn tìm kiếm mới. Bộ ghép giới hạn số ứng viên xử lý. Khi nguồn lỗi, có thể ghép từ dữ liệu cục bộ phù hợp đã có; khi thiếu ứng viên thì không hiện thẻ. Các video cần kết nối mạng vẫn cần nguồn phát khả dụng, kể cả khi danh sách ghép từ lịch sử đã lưu.

Hai cờ tài khoản/API-only tiếp tục tắt. Các luồng chính thức bị khóa theo cờ hiện có; không sử dụng OAuth để lấy media.

## Kiểm thử

- `AutoMixPlannerTest`: 6 bài kiểm tra dữ liệu thuật toán: trùng bài/phiên bản, thể loại xung đột, giới hạn kênh, lượt xem thật, nguồn/livestream, tuyển tập dài và nội dung Chuẩn Mở.
- `AutoMixDeviceTest`: kiểm tra mở thẻ, chọn lại clip có sẵn và thay hàng đợi/resume trên player thực; kiểm tra nguồn YouTube trực tiếp cần tham số instrumentation `liveMix=true`.
- `BrandVisualAuditTest`: ảnh Trang chủ và các màn hình sáng/tối trên API 35.

Trạng thái phát hành: demo cục bộ trước khi cập nhật. Kết quả kiểm thử thực tế được ghi trong `BRAND_REFRESH_AUDIT.md`; không coi build thành công là bằng chứng phát nhạc YouTube hoặc chạy trên điện thoại/đầu xe thật.
