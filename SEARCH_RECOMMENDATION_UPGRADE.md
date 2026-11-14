# AeroPlay 2.0 — Search và Recommendation

## Nguyên nhân đã xác nhận

- NewPipe trả kết quả video lên `SearchViewModel` gần như nguyên thứ tự nguồn; AeroPlay chưa có analyzer, scorer hay bộ lọc ngày/lượt xem.
- Tab Video/Kênh/Playlist chỉ ẩn loại kết quả trong UI; lựa chọn chưa được đưa vào NewPipe `SearchQueryHandler`.
- Phân trang khử trùng chỉ theo ID và không re-rank từng trang.
- Profile đề xuất chỉ lấy tiêu đề/kênh từ lịch sử. Khi chưa có lịch sử thì mục “Dành cho bạn” rỗng; yêu thích, playlist, từ khóa tìm kiếm, thời lượng xem, impression và phản hồi không được dùng.
- Hệ thống cũ không ghi nhận thời lượng xem tích lũy hoặc lần xem gần hoàn tất, nên không phân biệt rõ tải metadata, mở video và xem thật.
- Khởi tạo NewPipe hiện có đã đúng: một lần qua provider được đồng bộ, `Localization("vi", "VN")` và `ContentCountry("VN")`. Cấu hình Java 17, `minSdk 26` và `desugar_jdk_libs_nio` cũng đã có sẵn.

## Thay đổi

- Thêm `SearchQueryAnalyzer`: chuẩn hóa dấu/Unicode, ngôn ngữ ngữ cảnh và các intent GENERAL, RECENT, POPULAR, MUSIC, CHANNEL, PLAYLIST, EXACT_ENTITY. Truy vấn gốc vẫn được gửi nguyên văn sau trim.
- Thêm `VideoSearchRanker` với điểm giải thích được 0–100: liên quan truy vấn là tín hiệu chính và phải qua ngưỡng trước khi xét vùng/ngôn ngữ, ngày đăng, lượt xem và hồ sơ cá nhân.
- Dùng `DateWrapper` thật NewPipe trả về khi có; nếu thiếu, phân tích chuỗi ngày tương đối. Bộ lọc ngày dùng `Asia/Ho_Chi_Minh`. Metadata không có giữ trung lập trong điểm và không bị gán ngày giả.
- Truyền bộ lọc video/kênh/playlist vào `SearchQueryHandler`. Bộ lọc thời gian và “mới nhất/phổ biến” chạy cục bộ trên các trang đã tải; NewPipe 0.27.1 không triển khai tham số `sortFilter` trong URL tìm kiếm thông thường. Khi tải trang kế, AeroPlay nối các kết quả ở cuối để giữ thứ tự mục người dùng đã thấy.
- Lưu lựa chọn loại nội dung, sắp xếp và thời gian qua `SavedStateHandle`; khử trùng theo nguồn + ID, không làm mất continuation token.
- Mở rộng profile cục bộ: chủ đề/kênh từ lịch sử có trọng số giảm dần theo thời gian, thời lượng phát thực, lượt xem lại, tỷ lệ hoàn tất, yêu thích, playlist, tìm kiếm; impressions chỉ được lưu khi tối thiểu một nửa thẻ nằm trong màn hình đủ một giây; click được ghi riêng.
- Đề xuất lấy ứng viên từ video liên quan và một số tìm kiếm thật có giới hạn, cache 20 phút, tối đa hai video nền và hai truy vấn. Không tạo video giả hay gọi API tính phí.
- Thêm “Tiếp tục xem” theo vị trí phát thực; menu đề xuất gồm Không quan tâm, Không đề xuất kênh này, Đã xem, Xem sau. Phản hồi chỉ tác động feed đề xuất, không lọc kết quả tìm kiếm.
- Room migration 2→3 thêm số liệu thời lượng/hoàn tất, feedback, kênh bị ẩn và impressions; giữ lịch sử/yêu thích/playlist. Thao tác đặt lại cá nhân hóa xóa lịch sử xem/tìm kiếm và tín hiệu đề xuất, giữ yêu thích và playlist.
- Diagnostic query, intent, locale, candidate counts, điểm top 10 và lỗi chỉ được ghi trong Debug; Release không ghi log diagnostic này.
- Giữ `minSdk 26`, package và Media3; ranking video liên quan riêng ưu tiên trùng chủ đề/kênh.

## Xếp hạng và giới hạn

Điểm tìm kiếm mặc định thử nghiệm: liên quan 55%, ngữ cảnh 12%, mới 10%, phổ biến 15%, cá nhân hóa 8%; tỷ lệ đổi theo intent RECENT/POPULAR/EXACT_ENTITY. Không suy ra chắc chắn ngôn ngữ video từ ký tự tiêu đề. Lượt xem chỉ có tác dụng khi NewPipe trả số hợp lệ.

Mọi sort/filter là trên tập ứng viên đã tải, không phải thứ tự toàn YouTube. Các trang mới được nối phía sau; vì vậy sắp xếp mới nhất/phổ biến không được quảng bá như thứ tự toàn cục. NewPipe/YouTube có thể thay đổi giao thức, trả thiếu metadata hoặc giới hạn truy cập; AeroPlay không vượt CAPTCHA, đăng nhập, DRM hay giới hạn khu vực.

## Kiểm thử

Đã xác minh trên Android 8/API 26:

- Unit tests: 46 tests, 3 skipped, 0 thất bại.
- Instrumentation trên API 26: 38 tests, 13 skipped theo opt-in, 0 thất bại; các bài Room migration và UI không opt-in đều chạy.
- Luồng NewPipe thực: tìm kiếm video, trang kế tiếp và metadata YouTube thành công.
- Bài dò 10 truy vấn thực (`nhạc trẻ`, `nhạc remix 2026`, `hài việt nam`, `review điện thoại`, `game mobile`, `tin tức hôm nay`, `sơn chống thấm`, `Sơn Tùng M-TP`, `Taylor Swift`, `MrBeast`) thành công: cả 10 có ứng viên video thật, qua xếp hạng cục bộ và không trùng ID.
- Sau khi phát hành, bài kiểm tra giao diện Cài đặt gọi GitHub updater thật và nhận trạng thái release hợp lệ thành công.

Đây là xác nhận khả năng truy xuất/độ ổn định, không phải đánh giá rằng kết quả đứng đầu luôn đúng ý định. Chất lượng ngữ nghĩa cần nhãn từ người đánh giá và không được suy ra từ score ranker; chưa công bố Precision@10/NDCG.

## APK

- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release có chữ ký: `app/build/outputs/apk/release/app-release.apk` (minSdk 26).
- GitHub Release `v2.0.0` (versionCode 7) đã công khai và là bản mới nhất: [Tải AeroPlay 2.0.0](https://github.com/tranvannhat-uwq/AeroPlay/releases/tag/v2.0.0).
- Đã tải APK từ GitHub và đối chiếu SHA-256 với `update.json`; khớp: `39ea64fa934d7af7f29f61e0d84a7f6c9705959f44fbe838933cc7675c5594f9`. Release có APK, manifest, checksum và source ZIP.
