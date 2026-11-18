# Kế hoạch thêm bánh răng cài đặt video cho AeroPlay

Mốc khảo sát là AeroPlay 2.2.1. Hai ảnh tham chiếu quy định hai tầng menu. Giao diện sẽ dùng màu, kiểu chữ và khoảng cách của AeroPlay. Bản ghi này mô tả công việc dự kiến và tiêu chí để duyệt demo.

## 1. Cách người xem mở và đóng menu

Đặt bánh răng **Cài đặt phát** ở góc trên bên phải khung video, cả dọc và ngang. Chạm video để hiện bánh răng cùng các nút tạm dừng và chuyển bài. Dùng chung trạng thái điều khiển và thời gian tự ẩn 4 giây hiện có trong `PlayerScreen.kt`. Khi tạm dừng, đang kéo thanh tua hoặc đang thao tác trong menu, các nút vẫn dễ tiếp cận. Bánh răng có vùng chạm tối thiểu 48 dp, tên đọc cho TalkBack và tránh vùng khuyết màn hình.

Chạm bánh răng mở bảng từ cạnh dưới. Video tiếp tục ở đúng trạng thái trước khi mở. Mỗi hàng có biểu tượng, tên và giá trị hiện hành nếu có. Chọn **Tùy chọn khác** chuyển sang tầng hai trong cùng bảng. Nút Quay lại đưa người dùng về tầng trước, rồi đóng bảng trước khi thoát toàn màn hình. Vuốt xuống hoặc chạm nền đóng cả bảng. Khi đóng, bắt đầu lại thời gian 4 giây. Ở màn hình ngang hoặc khi tăng cỡ chữ, bảng cuộn được đến mục cuối. Vào PiP hoặc thu nhỏ trình phát sẽ đóng bảng.

## 2. Sáu mục ở bảng chính

| Mục | Hành vi dự kiến và điều kiện nghiệm thu |
|---|---|
| Chất lượng | Dùng lại danh sách luồng và logic chọn chất lượng của AeroPlay. Hiện độ phân giải thực tế. Nếu nguồn hỗ trợ chọn tự động, phân biệt lựa chọn tự động với mức hiện tại. Với tệp chỉ có một mức, hiển thị mức đó. Đổi chất lượng giữ vị trí, trạng thái tạm dừng/phát, tốc độ và phụ đề còn tương thích. |
| Tốc độ phát | Dùng lại bảy mức hiện có từ 0,5× đến 2×. Hiển thị dấu chọn và giá trị đang áp dụng. Thử đổi trong lúc phát lẫn lúc tạm dừng. |
| Phụ đề | Cho chọn Tắt hoặc ngôn ngữ thực có trong nguồn hay tệp. AeroPlay đã đọc danh sách phụ đề từ NewPipe. Bổ sung ánh xạ định dạng được hỗ trợ, gắn phụ đề vào nguồn Media3 và hiển thị lời đúng thời điểm trên lớp video. Kiểm tra tua, đổi chất lượng và chuyển bài. Khi thiếu track hợp lệ, ghi “Video này chưa có phụ đề”. [Tài liệu Media3 về media item và phụ đề](https://developer.android.com/media/media3/exoplayer/media-items) |
| Khóa màn hình | Khóa thao tác chạm trong trình phát để tránh tua, chuyển bài hoặc thu nhỏ ngoài ý muốn. Chạm khi đang khóa chỉ hiện nút **Mở khóa**. Nhấn nút rồi xác nhận để trở lại. Thử mở khóa bằng TalkBack, sau khi xoay máy và sau khi quay về từ nền. Đây là khóa thao tác của AeroPlay. |
| Trên đường đi | Đề xuất diễn giải thành chế độ nghe trên điện thoại với bìa nhạc, tiêu đề, nút phát/dừng và chuyển bài lớn. Tận dụng đường phát âm thanh hiện có và ưu tiên luồng chỉ âm thanh khi nguồn có. Nếu vẫn cần tải luồng video, thể hiện điều đó trong lựa chọn. Có nút trở về xem video đúng vị trí. Đây là lựa chọn thiết kế của AeroPlay vì ảnh chỉ thể hiện tên mục. |
| Tùy chọn khác | Mở bảy mục bên dưới. Khi quay lại, khôi phục vị trí cuộn và tiêu điểm của bánh răng hoặc hàng vừa chọn. |

## 3. Bảy mục trong Tùy chọn khác

| Mục | Hành vi dự kiến và điều kiện nghiệm thu |
|---|---|
| Cho video lặp lại | Mặc định tắt. Khi bật, phát lại bài hiện tại từ đầu ở cuối bài, kể cả lúc phát nền. Người dùng vẫn có thể bấm Tiếp. Đề xuất tự tắt lặp khi chủ động chọn bài khác. Với livestream đang trực tiếp, vô hiệu hóa và giải thích. |
| Chế độ môi trường xung quanh | Hiệu ứng ánh màu dịu quanh khung video. Phương án demo lấy bảng màu từ ảnh bìa đang có, chuyển màu nhẹ khi đổi bài. Muốn màu bám cảnh quay phải thử lấy mẫu thưa và đo trên điện thoại trước. Tắt vẽ khi vào nền, PiP hoặc chỉ nghe âm thanh. Tôn trọng giảm chuyển động. |
| Âm lượng ổn định | Mục tiêu là giảm chênh lệch giữa đoạn nhỏ và đoạn lớn. Thử bộ nén và giới hạn đỉnh gắn vào phiên âm thanh của player trong service. Đo mức âm trước/sau trên cùng clip và nghe thử qua loa, tai nghe, Bluetooth. Xử lý khi đổi thiết bị hoặc phiên âm thanh. Chỉ cho bật khi khởi tạo hiệu ứng thành công. Trên API 26–27 cần kiểm tra giải pháp tương thích riêng vì DynamicsProcessing có từ API 28. [Tài liệu Android về DynamicsProcessing](https://developer.android.com/reference/android/media/audiofx/DynamicsProcessing) |
| Hẹn giờ ngủ | Đề xuất các mốc 5, 15, 30, 45, 60 phút, **Hết bài này** và **Hủy hẹn giờ**. Hiện số phút còn lại. Service quản lý một hạn chót theo đồng hồ đơn điệu để tiếp tục đếm khi khóa máy hoặc đổi bài. Hết giờ thì tạm dừng, lưu vị trí và hủy hẹn. “Hết bài này” gắn với bài đã chọn, hủy khi người dùng bỏ qua bài. Khi bài kết thúc tự nhiên, hẹn ngủ được ưu tiên trước lặp và tự phát bài tiếp. |
| Nghe nhạc trên YouTube Music | Chỉ hiện cho video có mã YouTube hợp lệ. Chạm để mở liên kết bài đó bằng YouTube Music. Nếu thiếu ứng dụng, cho người dùng chọn mở web hoặc ở lại. Khi chuyển thành công, tạm dừng AeroPlay để tránh hai nguồn âm thanh. Nếu lỗi mở, giữ phiên nghe. |
| Xem ở chế độ thực tế ảo | Kiểm tra metadata chiếu hình trước. Làm thử với clip 360° đã biết, điều khiển góc nhìn bằng kéo và cảm biến, thoát về video đúng vị trí. Xem 360° trên điện thoại và xem qua kính hai mắt có tiêu chí riêng. Bản dành cho kính cần thêm dựng hình hai mắt, hiệu chỉnh và kiểm thử với kính thật. Chỉ mở tùy chọn tương ứng với khả năng đã kiểm chứng. [Thành phần hiển thị video cầu của Media3](https://developer.android.com/reference/androidx/media3/exoplayer/video/spherical/SphericalGLSurfaceView) |
| Trợ giúp và phản hồi | Giải thích các mục theo ngữ cảnh hiện tại, kèm đường dẫn phản hồi của dự án sau khi xác minh kênh tiếp nhận. Người dùng xem nội dung phản hồi, phiên bản ứng dụng và thông tin thiết bị trước khi tự gửi. |

## 4. Thứ tự triển khai

1. **Dựng menu và bản demo đầu tiên.** Trong `PlayerScreen.kt`, dùng một trạng thái điều hướng cho bảng chính, bảng phụ và các màn chọn. Kết nối chất lượng và tốc độ vào các lệnh hiện có của `PlayerViewModel.kt`. Kiểm tra chạm, che khuất, sáng/tối, màn hình ngang và tự ẩn trên luồng phát thật. Các lối tắt hiện có dùng chung lệnh và giá trị chọn.
2. **Hoàn thiện hành vi phát.** Phụ đề đi qua phần xây dựng nguồn phát và lớp hiển thị trong `PlayerVideoSurface.kt`. Lặp và hẹn ngủ đi qua `PlaybackControllerManager.kt` và `AeroPlayMediaService.kt`. Rà các nhánh kết thúc bài, radio tải muộn, playlist và Android Auto để một sự kiện kết thúc chỉ dẫn đến một hành động. Kiểm tra trao đổi trạng thái qua MediaSession khi giao diện tái kết nối.
3. **Hoàn thiện tương tác trên điện thoại.** Khóa chạm, Trên đường đi, mở YouTube Music và trợ giúp. Tách trạng thái bảng/khóa chạm theo màn hình, lặp/hẹn ngủ theo phiên phát và sở thích lâu dài theo DataStore. Đề xuất nhớ lựa chọn ánh màu và âm lượng ổn định, khởi tạo cả hai ở trạng thái tắt. Khi kết thúc service, giải phóng hiệu ứng, cảm biến và bộ hẹn giờ.
4. **Thử tính năng phụ thuộc thiết bị.** Dựng ba bản thử độc lập cho ánh màu, xử lý âm thanh và VR. Kiểm tra mức tiêu thụ CPU, khung hình bị bỏ và hiện tượng vỡ tiếng so với cùng video khi tắt tính năng. Bản demo ghi rõ phiên bản Android, thiết bị và loại video đã xác nhận. Tính năng thiếu điều kiện hiển thị lý do ngay tại hàng tương ứng.
5. **Duyệt demo trước khi cập nhật.** Bàn giao APK thử, ảnh menu dọc/ngang và kết quả theo từng mục. Sau khi duyệt demo mới đóng gói bản cập nhật có chữ ký tương thích.

## 5. Các tình huống phải kiểm tra

- **Giao diện** — chạm hiện/ẩn rồi đợi qua 4 giây, mở bảng lâu hơn 4 giây, chuyển tầng, Back, xoay ngang, chữ lớn, TalkBack và mở khóa sau khi xoay. Tránh để thao tác chọn hàng xuyên xuống video.
- **Nội dung** — thử video có và thiếu phụ đề, nhiều mức chất lượng, một tệp trên thiết bị, livestream, audio-only và mẫu 360°. Kiểm tra nguồn thiếu metadata hoặc hết khả dụng ngay khi menu đang mở.
- **Kết thúc bài** — thử hết bài bình thường, lặp, hẹn hết bài, lặp cùng hẹn hết bài, đổi bài bằng tay, và trường hợp radio trả kết quả sau khi hẹn ngủ đã dừng phát.
- **Vòng đời** — đổi chất lượng khi đang tạm dừng, phát nền và tắt màn hình tới hạn ngủ, PiP rồi trở về, kết nối lại Bluetooth/Android Auto. Xác nhận chỉ một phiên phát và thời điểm phát tiếp khớp với vị trí trước thao tác.
- **Hồi quy** — chạy lại kiểm thử điều khiển phát, toàn màn hình/giữ màn hình sáng, mini player, phát ngẫu nhiên, bản phối và Trang chủ. Trên điện thoại thật, xác minh thêm âm thanh, cảm biến và thao tác khi khóa máy. Mỗi mục có kết quả đạt, lỗi cần sửa hoặc điều kiện còn chờ thử.

## Trạng thái demo — 18/11/2026

### Đã triển khai

- Thêm bánh răng 48 dp vào lớp điều khiển trong video, cùng nhịp tự ẩn 4 giây; bảng dùng `ModalBottomSheet`, có nút đóng, Back về tầng cha, cuộn được và tự đóng khi thu nhỏ/PiP.
- Nối Chất lượng và Tốc độ phát với các lệnh phát hiện tại. Menu chất lượng giữ lối chọn hiện có. Đổi luồng giữ vị trí, trạng thái phát/tạm dừng và tốc độ; phụ đề còn tương thích được gắn lại vào `MediaItem`.
- Bổ sung lựa chọn Tắt và các track VTT, SRT, TTML được Media3 hỗ trợ. Khi video demo không có caption, hàng hiển thị “Video này chưa có phụ đề”.
- Bổ sung Khóa màn hình với nút mở khóa và hộp xác nhận; “Trên đường đi” đổi sang giao diện nghe lớn và dùng resolver âm thanh hiện có.
- Bổ sung Lặp lại, ánh màu dịu từ ảnh bìa, Âm lượng ổn định bằng DynamicsProcessing trên API 28+, hẹn giờ 5/15/30/45/60 phút, hết video hiện tại và hủy, liên kết YouTube Music có kiểm tra khả năng mở, cùng trang Trợ giúp và phản hồi.
- Hẹn giờ chạy bằng thời gian đơn điệu trong trình phát và media service. Khi bật hẹn “hết video hiện tại”, chế độ lặp bị tắt để không giữ bài chạy vô hạn.
- Sửa điều hướng Back từ Chất lượng/Tốc độ/Phụ đề về menu chính; Hẹn giờ/Trợ giúp trở về Tùy chọn khác. Dùng một vùng nhấn duy nhất cho từng công tắc để tránh xử lý hai lần.
- VR đang tắt có giải thích vì nguồn hiện tại không có metadata 360° được xác minh. API 26–27 không bật Âm lượng ổn định và nêu rõ cần Android 9 trở lên.

### Kết quả kiểm thử hiện có

- `:app:testDebugUnitTest` và `:app:assembleDebug`: đã qua sau các chỉnh sửa cuối cùng.
- `PlayerControlsUiTest`: đã qua trên emulator API 35.
- `PlayerSettingsUiTest` trên API 35 đã kiểm tra mở bảng, Back, lặp và hẹn giờ/hủy. Lượt hoàn chỉnh sau cùng trên API 26 còn kiểm tra khóa/mở khóa, “Trên đường đi”, đổi ngang và mở bảng lại; API 26 xác nhận dòng giải thích DynamicsProcessing không hỗ trợ.
- APK khởi động trên API 26 mà không có lỗi `AndroidRuntime`.
- Sau khi ký bản 2.2.2, cài nâng cấp trực tiếp từ APK release 2.2.1 sang APK release 2.2.2 thành công trên emulator API 26; bản cài có cùng fingerprint release. Bước này xác nhận khả năng nâng version/certificate, chưa đo dữ liệu người dùng thật.
- Ảnh demo dọc/ngang được lưu trong `app/build/demo-evidence/`.

### Chưa xác nhận trên thiết bị thật

- Chưa đo/ nghe thử tác động thực tế của nén âm lượng trên loa, tai nghe và Bluetooth; chưa kiểm tra đổi thiết bị âm thanh.
- Video demo không có phụ đề nên chỉ xác nhận trạng thái thiếu caption, chưa kiểm chứng đồng bộ caption thật khi tua/đổi chất lượng.
- Không có video 360° đã biết để kiểm chứng; mục VR vì vậy tiếp tục bị khóa.
- Chưa kiểm tra mở YouTube Music trên máy có/cài thiếu YouTube Music, Android Auto/Bluetooth, TalkBack, chữ cỡ lớn, PiP và vòng đời khóa màn hình trên điện thoại thật.

APK dùng để duyệt demo là bản Debug ký bằng khóa debug của máy build, version `2.2.1 (16)`; không dùng để ghi đè bản đang cài. Bản phát hành tiếp theo phải được workflow GitHub ký bằng khóa release gốc và có versionCode cao hơn.
