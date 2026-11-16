# Kế hoạch đề xuất nhạc cùng phong cách và playlist cho AeroPlay

Ngày lập: 16/11/2026.

Trạng thái: đã triển khai mã tính năng cốt lõi; phần build, kiểm thử và nghiệm thu chất lượng còn mở. Chưa build hoặc phát hành APK.

## Tình trạng hiện thực

Mã nguồn hiện có các phần sau; hành vi chưa được xác nhận bằng build, thiết bị hoặc đánh giá nghe thật:

- Nhận diện bản cùng ca khúc bằng cách chuẩn hóa tên và bỏ các nhãn lyrics, cover, karaoke, remix, live và metadata chất lượng; chỉ gộp khi có ít nhất hai từ nội dung chung để giảm nhầm những bài trùng tên ngắn.
- Xếp hạng video liên quan theo thể loại khi metadata cho biết thể loại; nếu không, dùng tín hiệu nhạc, thứ tự nguồn và độ đa dạng. Bỏ bài hiện tại, bản trùng có độ tin cậy cao và giới hạn hai video cùng kênh.
- Phát bài đơn tìm kiếm bằng hàng đợi radio cho truy vấn/bài có tín hiệu nhạc; tìm kiếm video thông thường vẫn giữ hàng đợi kết quả tìm kiếm.
- Dùng hàng đợi trình phát làm nguồn duy nhất cho danh sách Tiếp theo, nút Next và tự chuyển. Tải thêm ứng viên từ truy vấn nhạc ở nền; tùy chọn mới trong Cài đặt điều khiển tự chuyển radio.
- Bài nhạc nhận diện được từ trang chủ, tìm kiếm hoặc playlist cục bộ cũng mang ngữ cảnh nghe nhạc; danh sách phát của người dùng giữ thứ tự và chỉ nối radio sau khi hết danh sách nếu có đủ tín hiệu nhạc.
- Đề xuất tự động và các nhóm nhạc trang chủ áp dụng phản hồi Không quan tâm/Đã xem và kênh bị chặn.
- Thêm nhóm nhạc cùng phong cách, nhạc nhiều lượt xem và playlist thật trên trang chủ. Mục “nhiều lượt xem” dùng số lượt xem từ nguồn trả về.
- Thêm đọc playlist NewPipe có tối đa 80 video, mở sheet danh sách, phát từ đầu hoặc từ bài được chọn; cho phép nối radio sau playlist khi tùy chọn tự phát đang bật.

Các giới hạn còn lại cần xử lý hoặc xác nhận trước khi coi kế hoạch hoàn tất:

- Chưa có bộ fixture/đánh giá nghe thật; chất lượng nhận diện thể loại và gộp ca khúc chưa được đo.
- Nguồn chưa trả lượt nghe hoặc thể loại chuẩn cho mọi video. Nhóm phổ biến xếp theo view count trên tập đã tải, không phải bảng xếp hạng toàn nền tảng.
- Playlist được đọc tối đa bốn trang và 80 video; giao diện báo còn mục nhưng chưa tải tiếp theo yêu cầu khi kéo tới cuối.
- Mẫu bài bên trong playlist chưa được dùng để xếp hạng/đo độ trùng playlist. Playlist gợi ý hiện lọc sơ bộ theo tên và mô tả.
- Chưa kiểm tra trực tiếp Next từ thông báo, tai nghe, PiP, Android 8/API 26 và Android 15/API 35.
- Chưa chạy build hoặc test tự động cho thay đổi hiện tại.

## 1. Mục tiêu và phạm vi

Khi người dùng phát một bài hát, AeroPlay cần đề xuất và phát tiếp các ca khúc khác có phong cách phù hợp. Trang đề xuất cần có bài hát riêng lẻ, playlist cùng phong cách và nhóm bài nhiều lượt xem trong phong cách người dùng thích.

Các kết quả cần đạt:

- Hạn chế tình trạng nghe một bài rồi nhận toàn video cover, remix, lyrics hoặc đăng lại của chính bài đó.
- Có thể chuyển sang bài khác tên, khác nghệ sĩ nhưng cùng thể loại hoặc cách thể hiện phù hợp.
- Danh sách đang chờ phát, nút Tiếp theo và tự chuyển khi hết bài dùng cùng một thứ tự.
- Playlist được đề xuất có thể mở, xem các bài và phát được; không chỉ là thẻ thông tin.
- Bài nhiều lượt xem phải qua bước đánh giá phù hợp trước khi được ưu tiên.
- Giữ playlist người dùng chủ động chọn, lịch sử, yêu thích, vị trí phát và các nội dung ngoài âm nhạc hoạt động đúng.

Bản đầu tận dụng metadata và các nguồn đang có trong dự án. Chưa đưa phân tích âm thanh, BPM, mô hình AI bên ngoài, backend mới hoặc API trả phí vào phạm vi. Trải nghiệm hướng tới cách khám phá nhạc người dùng mong muốn; không cam kết tái tạo thuật toán hoặc dữ liệu cá nhân hóa của YouTube.

## 2. Hiện trạng đã kiểm tra

Các đường dẫn trong bảng dưới đây tính từ thư mục gốc dự án.

| Vị trí | Hành vi hiện tại | Hệ quả cần xử lý |
| --- | --- | --- |
| `app/src/main/java/com/aeroplay/app/feature/search/SearchRanking.kt`, `RelatedVideoRanker` | Điểm chủ yếu từ từ trùng trong tiêu đề và cùng kênh; khử trùng theo nguồn + ID | Nhiều video của cùng một ca khúc vẫn được xem là các đề xuất khác nhau |
| `app/src/main/java/com/aeroplay/app/feature/search/SearchViewModel.kt`, `playVideo` | Đưa toàn bộ danh sách kết quả tìm kiếm vào hàng đợi | Tìm tên bài A rồi mở A có thể phát tiếp nhiều phiên bản A |
| `app/src/main/java/com/aeroplay/app/feature/player/PlayerScreen.kt` | Tự xếp hạng `relatedVideos` trong giao diện; nếu rỗng thì dùng hàng đợi | Danh sách hiển thị có thể khác thứ tự nút Tiếp theo thực sự sử dụng |
| `app/src/main/java/com/aeroplay/app/playback/controller/PlaybackControllerManager.kt` | `playNext()` lấy phần tử kế trong hàng đợi; khi hết bài chỉ gọi nếu hàng đợi còn phần tử | Chưa có cơ chế tự nối bài cùng phong cách khi hàng đợi hết |
| `app/src/main/java/com/aeroplay/app/data/repository/PersonalizedRecommendations.kt` | Đã dùng lịch sử, thời lượng xem, yêu thích, playlist cục bộ, tìm kiếm và phản hồi; cache ứng viên 20 phút | Có nền tảng cá nhân hóa để tái sử dụng, nhưng còn thiên về từ khóa tiêu đề/kênh |
| `app/src/main/java/com/aeroplay/app/feature/home/HomeViewModel.kt` | Kết quả cá nhân hóa là danh sách `VideoItem` | Chưa biểu diễn các nhóm playlist và nhạc phổ biến riêng |
| `app/src/main/java/com/aeroplay/app/data/source/ExperimentalExtractorSource.kt` | Có tìm kiếm playlist và ánh xạ `PlaylistItem`; video liên quan hiện lấy tối đa 20 mục | Cần thêm đọc nội dung và phân trang playlist, đánh giá tập ứng viên rộng hơn khi cần |
| `app/src/main/java/com/aeroplay/app/feature/search/SearchScreen.kt`, `PlaylistResultRow` | Hiển thị playlist; chưa có hành động mở/phát trong thành phần này | Cần nối tới luồng chi tiết playlist dùng chung |
| `app/src/main/java/com/aeroplay/app/core/model/DomainModels.kt` | `VideoItem` có tiêu đề/kênh/lượt xem; `VideoDetail` có mô tả; `PlaylistItem` chưa có danh sách bài | Không có nhãn thể loại, nghệ sĩ hoặc lượt nghe đáng tin cậy sẵn cho mọi mục |

Room hiện ở phiên bản 3. Bản kế hoạch này ưu tiên tái sử dụng dữ liệu hiện có, không yêu cầu migration chỉ để lưu cache đề xuất.

## 3. Hành vi sản phẩm

### 3.1. Mở bài hát từ tìm kiếm hoặc một thẻ đề xuất

1. Người dùng chọn bài A; ứng dụng bắt đầu tải và phát A theo luồng hiện tại.
2. Tạo phiên phát nhạc với A làm bài gốc; không tự lấy toàn bộ kết quả tìm kiếm làm danh sách phát nhạc.
3. Tải gợi ý ở nền, lọc các phiên bản của A, xếp hạng các bài khác và đưa vào phần hàng đợi tự động.
4. Danh sách dưới trình phát hiển thị đúng các bài chuẩn bị phát theo thứ tự.
5. Bấm Tiếp theo hoặc hết bài đều chuyển sang cùng bài B đứng đầu hàng đợi.
6. Sau khi chuyển sang B, bổ sung phần cuối hàng đợi khi cần; không liên tục đảo các mục người dùng đã nhìn thấy.

Ví dụ mong muốn: đang nghe một bản ballad Việt thì có thể nhận các bài ballad Việt khác từ nhiều nghệ sĩ, thay vì liên tiếp bản chính thức, lyrics, cover và reupload của bài hiện tại.

### 3.2. Người dùng chủ động chọn playlist hoặc danh sách có thứ tự

- Phát đúng thứ tự playlist từ vị trí người dùng chọn.
- Chỉ loại mục trùng ID do phân trang và bỏ qua mục nguồn xác nhận không khả dụng; không xếp lại các bài hợp lệ theo điểm đề xuất.
- Tải trang tiếp theo của playlist trước khi coi playlist đã hết. Lỗi phân trang phải có trạng thái thử lại, không bị coi là đã phát hết.
- Khi toàn bộ playlist đã hết, mặc định tiếp tục bằng nhạc cùng phong cách nếu xác định được đây là phiên nghe nhạc.
- Cung cấp công tắc “Tự phát tiếp cùng phong cách” để người dùng có thể dừng tại cuối playlist. Tắt công tắc vẫn cho phép xem và chọn gợi ý thủ công.
- Bài được người dùng thêm rõ ràng vào hàng đợi luôn được ưu tiên trước phần tự động; thay đổi gợi ý không được xóa các bài này.
- Các lựa chọn như phát danh sách yêu thích hoặc playlist cục bộ cũng được coi là danh sách người dùng chủ động chọn.

### 3.3. Trang đề xuất

| Nhóm hiển thị | Nội dung | Cách chọn |
| --- | --- | --- |
| Cùng phong cách bài đang nghe | Các bài khác phù hợp với bài hiện tại | Dùng ngữ cảnh hiện tại; nếu chưa phát bài thì dùng phiên nghe nhạc gần nhất đủ tin cậy |
| Playlist dành cho bạn | Playlist thật theo phong cách và sở thích | Xét tên, mô tả khi có và mẫu bài bên trong |
| Nhiều lượt xem trong phong cách bạn thích | Bài riêng lẻ phổ biến trong tập ứng viên phù hợp | Xếp theo lượt xem nguồn cung cấp sau khi lọc độ liên quan |
| Khám phá thêm | Bài hoặc playlist từ nghệ sĩ/kênh khác | Ưu tiên đa dạng nhưng vẫn trong phong cách phù hợp |

Giữ các nhóm Tiếp tục xem, Yêu thích và nội dung tổng quát đang có. Các nhóm nhạc được ẩn hoặc thu gọn khi không có dữ liệu phù hợp; không thay toàn bộ trang chủ thành trang nhạc nếu người dùng đang xem nội dung khác.

Người dùng mới chưa có lịch sử vẫn được xem nhóm nhạc phổ biến lấy từ các truy vấn nhạc tổng quát theo ngữ cảnh ngôn ngữ cấu hình, với nhãn rõ ràng. Không gọi nhóm đó là đã cá nhân hóa.

## 4. Nhận diện ca khúc và phong cách

### 4.1. Tách định danh ca khúc khỏi phiên bản video

Tạo `SongIdentityResolver` với kết quả gồm tên bài chuẩn hóa, nghệ sĩ nếu nhận diện được, dấu nhận diện ca khúc, nhãn phiên bản và mức độ tin cậy.

- Chuẩn hóa Unicode, dấu câu, khoảng trắng và chữ hoa/thường; dùng đối chiếu không dấu như tín hiệu phụ.
- Nhận diện nhãn official MV/audio, lyrics, cover, karaoke, remix, slowed, sped up, live và reupload để so sánh tên bài gốc.
- Giữ các nhãn remix/acoustic/live riêng để đánh giá cách thể hiện, dù chúng không thuộc tên bài gốc.
- Không mặc định tên kênh là tên ca sĩ; kênh tuyển tập có thể đăng nhiều nghệ sĩ.
- Chỉ loại cứng cùng ca khúc khi có đủ bằng chứng. Hai bài khác nhau có cùng tên hoặc tên quá ngắn phải được xử lý thận trọng.
- Khi không đủ bằng chứng, giảm ưu tiên mục nghi trùng thay vì loại toàn bộ chỉ vì trùng một vài từ.
- Không thay đổi tiêu đề gốc hiển thị hoặc khóa ID đang dùng để lưu lịch sử/yêu thích.

Áp dụng khử trùng hai lớp: nguồn + ID cho video, sau đó định danh ca khúc cho các đề xuất tự động. Khử trùng theo ca khúc không áp dụng để sửa nội dung playlist người dùng đã chọn.

### 4.2. Tạo hồ sơ phong cách có mức độ tin cậy

Tạo `MusicStyleAnalyzer` để tổng hợp:

| Tín hiệu | Cách dùng | Giới hạn |
| --- | --- | --- |
| Nhãn ballad, acoustic, bolero, lofi, EDM, remix, rap… trong metadata | Bằng chứng trực tiếp khi nhãn nằm trong ngữ cảnh âm nhạc | Không xem mọi từ trong tiêu đề là thể loại |
| Mô tả và thông tin nghệ sĩ nếu nguồn có | Bổ sung khi tên bài không nói lên phong cách | Không yêu cầu tải chi tiết mọi ứng viên |
| Các video liên quan đã lọc trùng ca khúc | Suy ra quan hệ giữa các bài; giữ nguồn và thứ tự làm tín hiệu | Quan hệ nguồn trả về không chứng minh hai bài cùng thể loại |
| Lịch sử nghe gần đây, yêu thích và playlist đã lưu | Bổ sung sở thích khi dữ liệu bài hiện tại yếu | Không để sở thích cũ lấn át lựa chọn hiện tại |
| Ngôn ngữ/vùng và cách thể hiện | Tín hiệu phụ để giữ ngữ cảnh | Không suy chắc ngôn ngữ bài hát từ tên kênh hoặc bảng chữ cái |

Phân biệt thể loại, cách thể hiện và ngữ cảnh như “chill”, “buồn”, “sôi động”. Không tự gán BPM, âm sắc hoặc tâm trạng chắc chắn nếu metadata không hỗ trợ.

Phải có nhánh xử lý nội dung không phải nhạc. Khi độ tin cậy âm nhạc thấp, giữ luồng gợi ý video theo chủ đề, không tự tìm ballad/remix cho tin tức, phim hoặc hướng dẫn.

## 5. Thu thập ứng viên

Tạo `MusicRecommendationRepository` dùng chung cho trình phát và trang chủ, có nguồn gốc ứng viên rõ ràng.

Thứ tự lấy dữ liệu cho phiên nghe:

1. Dùng lại `VideoDetail.relatedVideos` đã tải cho bài đang phát; không trích xuất lại cùng metadata chỉ để lấy danh sách này.
2. Lọc cùng ca khúc, mục bị chặn và mục vừa phát; giữ lại quan hệ nguồn để xếp hạng.
3. Khi chưa có đủ bài phù hợp, tìm tối đa hai truy vấn bổ sung theo phong cách/ngôn ngữ và nghệ sĩ nếu biết.
4. Nếu metadata phong cách yếu, có thể đọc liên quan của tối đa một ứng viên đã qua lọc để mở rộng quan hệ. Không mở rộng đệ quy.
5. Gộp, khử trùng, kiểm tra mức phù hợp và xếp hạng trên toàn bộ tập đã tải.

Truy vấn minh họa: `ballad Việt`, `acoustic Việt`, `nhạc bolero`, hoặc tên nghệ sĩ đã nhận diện đi kèm phong cách. Không dùng nguyên tên bài hiện tại làm truy vấn chính để tìm bài kế tiếp. Không suy ra tên nghệ sĩ từ một kênh tuyển tập chưa xác minh.

Các tìm kiếm hệ thống phục vụ đề xuất phải đi trực tiếp qua lớp nguồn/repository nội bộ, không lưu vào lịch sử tìm kiếm của người dùng. Khóa cache và ngữ cảnh phải bao gồm nguồn đang chọn.

## 6. Lọc, xếp hạng và giữ độ đa dạng

### 6.1. Lọc trước khi chấm điểm

- Loại video hiện tại, ID trùng, cùng ca khúc ở mức tin cậy cao và mục đã phát trong cửa sổ gần nhất của phiên.
- Áp dụng Không quan tâm, Đã xem và Không đề xuất kênh này cho nội dung tự động; không chặn kết quả người dùng chủ động tìm hoặc playlist đã chọn.
- Không tự phát live stream, karaoke hoặc clip không phải nhạc khi phiên hiện tại là nghe ca khúc thông thường. Chỉ mở rộng loại nội dung khi người dùng đã chủ động chọn ngữ cảnh đó.
- Thời lượng chỉ là tín hiệu phụ. Một video dài không tự động bị kết luận là sai thể loại; ưu tiên bài đơn trong hàng đợi bài hát, đưa tuyển tập dài vào nhóm phù hợp riêng.
- Ứng viên phải có bằng chứng phù hợp từ metadata, quan hệ nguồn hoặc ngữ cảnh; lượt xem lớn không đủ để vượt bước lọc.

### 6.2. Điểm đề xuất ban đầu

Trọng số dưới đây là cấu hình thử nghiệm để hiệu chỉnh bằng đánh giá thực tế, chưa phải kết quả chất lượng đã được xác minh.

| Thành phần | Tỷ trọng dự kiến | Nguyên tắc |
| --- | --- | --- |
| Phù hợp phong cách, cách thể hiện và ngữ cảnh ngôn ngữ | 40% | Bằng chứng mạnh có trọng số cao; không thưởng vì trùng tên bài |
| Quan hệ với bài hiện tại/bài gốc của phiên | 30% | Ưu tiên nguồn liên quan đã lọc; giữ ngữ cảnh gốc để giảm trôi phong cách sau nhiều lần chuyển bài |
| Sở thích và hành vi nghe | 20% | Tận dụng thời lượng nghe, mức hoàn tất, yêu thích và playlist hiện có |
| Độ phổ biến | Tối đa 10% | Chuẩn hóa log của lượt xem hợp lệ; thiếu dữ liệu thì không bịa số |

Khi thiếu tín hiệu phải giảm mức tin cậy và dùng quy tắc dự phòng rõ ràng, không tự cộng tối đa phần điểm bị thiếu. Cùng nghệ sĩ/cùng kênh chỉ hỗ trợ, không tự vượt ngưỡng phù hợp.

### 6.3. Chọn danh sách sau khi chấm điểm

- Trong 10 gợi ý đầu, ưu tiên 10 ca khúc khác nhau khi có đủ ứng viên đủ điều kiện.
- Mặc định tối đa 2 video/kênh trong 10 mục đầu; giới hạn nghệ sĩ áp dụng riêng khi nhận diện đủ tin cậy, tránh đánh đồng kênh với nghệ sĩ.
- Cố gắng có ít nhất 3 nghệ sĩ khi tập ứng viên phù hợp cho phép.
- Không lặp các ca khúc trong 30 bài gần nhất của phiên; nếu tập ứng viên quá nhỏ thì trả ít hơn thay vì tự nới để phát lại ngay.
- Với lịch sử cũ ngoài phiên hiện tại, dùng phạt điểm có giới hạn thay vì loại vĩnh viễn các bài người dùng thích nghe lại.
- Nếu chưa đủ bài, mở rộng nguồn trong ngân sách; hết ngân sách thì hiển thị ít hơn. Không chèn bài không phù hợp để đủ 10 mục.

Nhóm “Nhiều lượt xem…” dùng cùng bộ lọc, sau đó ưu tiên `viewCount`. Đây là thứ tự trong tập ứng viên đã tải, không phải bảng xếp hạng toàn YouTube hoặc số lượt nghe từ nền tảng khác. Không gọi là “đang thịnh hành” nếu chưa có dữ liệu tốc độ tăng lượt xem.

## 7. Playlist được đề xuất

### 7.1. Nguồn và mô hình dữ liệu

- Dùng tìm kiếm playlist thật theo phong cách để lấy ứng viên.
- Thêm `RemotePlaylistRepository` và lớp nguồn đọc chi tiết playlist, danh sách bài, token trang tiếp theo và trạng thái hết trang.
- Kiểm tra API thực tế trong dependency NewPipe đang cài trước khi triển khai; không mặc định mọi URL dạng mix/radio đều có thể phân trang như playlist thường.
- Tách playlist từ nguồn khỏi `UserPlaylist` cục bộ. Mở hoặc phát playlist đề xuất không tự tạo dữ liệu playlist trong thư viện người dùng.
- Chỉ hỗ trợ mix/radio đặc biệt khi adapter đã được kiểm chứng; nếu không hỗ trợ thì báo trạng thái rõ ràng, không tạo danh sách giả.

### 7.2. Đánh giá playlist

1. Lọc sơ bộ bằng tên, nguồn, mô tả khi có và kênh tải lên.
2. Trong mỗi lần tải nhóm playlist, đọc tối đa hai playlist ứng viên tốt nhất; đánh giá mẫu tối đa sáu bài đầu khả dụng từ trang đầu.
3. Loại playlist có mẫu chủ yếu là các phiên bản cùng một ca khúc, có nội dung không phải nhạc hoặc không có bài khả dụng.
4. Khi có ít nhất năm bài trong mẫu, ngưỡng khởi đầu là ít nhất 60% phù hợp phong cách. Mẫu nhỏ hoặc thiếu metadata phải có mức tin cậy thấp hơn, không được công bố như đã kiểm chứng toàn bộ playlist.
5. Hạn chế hai playlist gần giống nhau. Đối chiếu các bài đã tải; nếu phần giao nhau chiếm ít nhất 70% mẫu nhỏ hơn và mỗi mẫu có tối thiểu năm bài thì giữ playlist phù hợp hơn.
6. Không suy ra tổng lượt nghe playlist bằng cách cộng lượt xem các video. Playlist không có số lượt xem đáng tin cậy thì không hiển thị con số này.

### 7.3. Giao diện và thao tác

- Thẻ playlist có ảnh bìa, tên, kênh, số bài nếu nguồn có; số bài chưa biết thì ẩn, không hiển thị giá trị âm.
- Chạm thẻ mở màn hình hoặc sheet chi tiết, có trạng thái tải/lỗi và nút Phát.
- Cho phát từ bài đầu hoặc từ bài người dùng chọn; danh sách dài tải dần theo trang.
- Giữ thứ tự nguồn và vị trí hiện tại; không phát lại từ đầu khi trang sau về.
- Bỏ qua mục nguồn xác nhận đã xóa/riêng tư/không khả dụng với giới hạn hữu hạn, tránh vòng lặp tự chuyển vô tận.
- Dùng chung luồng mở playlist cho thẻ trang chủ và kết quả tìm kiếm, tránh triển khai hai cơ chế khác nhau.

## 8. Hàng đợi và trạng thái phát thống nhất

Mở rộng `PlaybackQueue` để phân biệt rõ nguồn gốc hàng đợi, ví dụ `SINGLE_ITEM_RADIO`, `USER_PLAYLIST`, `REMOTE_PLAYLIST`, `EXPLICIT_LIST`, `GENERAL_VIDEO`.

Lưu thêm ngữ cảnh cần thiết: bài gốc của phiên, bài hiện tại, phần người dùng đã chọn, phần tự động, lịch sử bài đã phát và trạng thái phân trang nếu là playlist từ nguồn. Dùng enum/model, không nhận diện chế độ bằng chuỗi `queueTitle`.

Quy tắc điều phối:

- `PlaybackControllerManager` giữ quyền chọn bài và cập nhật trạng thái; UI không gọi ranker để tạo thứ tự riêng.
- `PlayerViewModel` cung cấp một `UpNextState` từ hàng đợi thật, bao gồm danh sách, trạng thái tải và lỗi gợi ý độc lập với lỗi phát.
- `PlayerScreen` hiển thị các bài sắp phát từ cùng snapshot; playlist có thể ghi nhãn riêng phần “Tiếp theo trong playlist” và phần “Tự phát tiếp”.
- Khi hàng đợi tự động còn ba bài hoặc ít hơn, tải bổ sung có giới hạn. Không xóa phần lịch sử để nút Trước vẫn hoạt động.
- Kết quả tải phải khớp ID phiên, lần chọn bài và phiên bản hàng đợi. Kết quả cũ của A không được ghi đè sau khi người dùng đã chọn B hoặc playlist khác.
- Bảo vệ thao tác chuyển bài bằng xử lý tuần tự hoặc mutex. Nút Tiếp theo và sự kiện hết bài cùng đến chỉ được tiêu thụ một mục hàng đợi.
- Nếu chưa có bài tiếp theo, có thể đợi tác vụ đang tải trong giới hạn thời gian; không tự phát bất ngờ khi người dùng đã dừng hoặc chọn nội dung khác.
- Khi đóng trình phát hoặc tắt tự phát tiếp, hủy ý định tự chuyển còn chờ và hủy tải không còn cần thiết.
- Đối chiếu đường đi lệnh Next từ mini player, PiP, thông báo và tai nghe; những đường đã hỗ trợ phải sử dụng cùng điều phối, không giả định hàng đợi Kotlin tự đồng bộ với Media3.

Không đưa logic đề xuất chỉ vào vòng đời màn hình, vì tự chuyển bài phải hoạt động khi đang phát nền và màn hình trình phát không được mở.

## 9. Kiến trúc và các điểm cần thay đổi

Tên lớp/file mới dưới đây là dự kiến. Tất cả nằm dưới `app/src/main/java/com/aeroplay/app/` nếu không ghi chú khác.

| Thành phần | Trách nhiệm dự kiến |
| --- | --- |
| `data/repository/SongIdentityResolver.kt` | Chuẩn hóa và so sánh định danh ca khúc, giữ nhãn phiên bản |
| `data/repository/MusicStyleAnalyzer.kt` | Nhận diện nhạc, phong cách và mức độ tin cậy |
| `data/repository/MusicRecommendationRanking.kt` | Lọc, chấm điểm và chọn danh sách đa dạng; hàm thuần có thể kiểm thử |
| `data/repository/MusicRecommendationRepository.kt` | Thu thập ứng viên, cache, ngân sách và gợi ý theo phiên |
| `data/repository/RemotePlaylistRepository.kt` | Chi tiết, các trang bài hát và cache playlist từ nguồn |
| `data/repository/PersonalizedRecommendations.kt` | Tái sử dụng hồ sơ/phản hồi hiện có; phối hợp nhóm nhạc mà vẫn giữ đề xuất video tổng quát |
| `data/repository/Repositories.kt`, `RepositoryModule.kt` | Khai báo và cung cấp dependency mới; giữ UI độc lập lớp Extractor |
| `data/source/ExperimentalExtractorSource.kt` | Đọc playlist; giữ nguồn và thứ tự ứng viên liên quan; xử lý lỗi/hủy |
| `core/model/DomainModels.kt` hoặc các file model riêng | Hồ sơ nhạc, kết quả theo nhóm, mô hình playlist từ nguồn và ngữ cảnh hàng đợi |
| `playback/controller/PlaybackControllerManager.kt` | Chuyển bài thống nhất, nối hàng đợi, hủy yêu cầu cũ và xử lý phát nền |
| `feature/player/PlayerViewModel.kt`, `PlayerScreen.kt` | Dùng trạng thái Up Next chung; hiển thị gợi ý và tự phát tiếp |
| `feature/search/SearchViewModel.kt`, `SearchScreen.kt` | Mở bài nhạc đơn bằng phiên radio; mở playlist bằng luồng dùng chung |
| `feature/home/HomeViewModel.kt`, `HomeScreen.kt` | Nhóm bài cùng phong cách, playlist và nhạc nhiều lượt xem; trạng thái riêng từng nhóm |
| Thành phần chi tiết playlist trong `feature/` và điều hướng liên quan | Xem, phân trang và phát playlist từ nguồn |
| `core/designsystem/` | Thẻ playlist và nhóm nội dung dùng chung |
| Model/repository cài đặt và `feature/settings/` nếu cần | Lưu lựa chọn Tự phát tiếp cùng phong cách bằng DataStore |

Không gộp khóa playlist từ nguồn vào bảng playlist cục bộ. Phản hồi video và kênh tiếp tục dùng Room hiện có. Nếu sau này thêm phản hồi riêng cho playlist thì lập migration và kiểm thử riêng, không ngầm đổi khóa hoặc ý nghĩa của dữ liệu cũ.

## 10. Hiệu năng, ngân sách mạng và cache

Các con số sau là giới hạn khởi đầu cần đo trên thiết bị, không phải cam kết tốc độ mạng.

| Công việc | Giới hạn dự kiến |
| --- | --- |
| Mở bài hiện tại | Không chờ lấy đề xuất xong mới phát |
| Bổ sung ứng viên cho một bài | Tối đa hai truy vấn video và một lượt đọc liên quan bổ sung ngoài metadata đã có |
| Tải nhóm playlist trên trang chủ | Tối đa hai truy vấn playlist và hai lượt đọc trang đầu để đánh giá mẫu |
| Phân trang playlist | Chỉ tải khi mở playlist hoặc sắp phát hết trang đã có |
| Số tác vụ metadata/tìm kiếm nền đồng thời | Tối đa hai cho toàn bộ bộ điều phối đề xuất; ưu tiên nhu cầu phát trước trang chủ |
| Đợt lấy gợi ý cho trình phát | Khoảng tám giây; dùng phần kết quả hợp lệ đã về khi hết hạn |
| Đợt tải các nhóm trang chủ | Khoảng mười lăm giây; các nhóm hoàn thành độc lập |
| Cache ứng viên | Hai mươi phút, tối đa hai mươi ngữ cảnh theo LRU; không lưu URL luồng phát như dữ liệu bền vững |
| Lỗi giới hạn tần suất | Dừng mở rộng, dùng cache và cooldown trước khi thử lại; không retry liên tục |

Ngân sách phải tính cả retry bên trong lớp nguồn, không chỉ đếm số lời gọi ở repository. Các giới hạn là trần, không bắt buộc dùng hết.

Khóa cache gồm nguồn, bài/phong cách, loại nội dung, ngôn ngữ ngữ cảnh và phiên bản thuật toán. Cache lưu tập ứng viên; áp dụng lại phản hồi và quy tắc đa dạng khi sử dụng. Không quan tâm, chặn kênh hoặc đặt lại cá nhân hóa phải có hiệu lực ngay cả khi còn cache.

Gộp các yêu cầu trùng đang chạy. Thay đổi tiến trình phát mỗi 500 ms không được kích hoạt tìm kiếm lại; chỉ đổi bài, nguồn hoặc ngữ cảnh có ý nghĩa mới tải theo debounce/cache. Khi rời trang chủ, hủy các tải phục vụ riêng giao diện; giữ tác vụ cần cho phát nền.

Metadata thiếu, hết thời gian hoặc mạng lỗi: giữ danh sách hợp lệ đã có, trả ít mục hoặc trạng thái thử lại. Không chèn dữ liệu demo vào nhóm YouTube. Không để lỗi nhóm playlist trở thành lỗi dừng bài đang phát.

## 11. Trình tự triển khai và đầu ra

### Giai đoạn 1 — Định danh ca khúc và phân tích ngữ cảnh

- [ ] Tạo bộ dữ liệu mẫu có nhãn cho trùng ca khúc, bài trùng tên, phong cách và nội dung không phải nhạc.
- [ ] Xây `SongIdentityResolver` và `MusicStyleAnalyzer` cùng kiểm thử.
- [ ] Ghi rõ trường hợp chưa xác định được, không tự điền nhãn chắc chắn.
- [ ] Đầu ra: model/hàm thuần và bộ kiểm thử cho bước lọc, chưa đổi luồng phát.

### Giai đoạn 2 — Thu thập và xếp hạng bài kế tiếp

- [ ] Xây repository ứng viên, tái sử dụng metadata hiện có, bổ sung truy vấn và cache có giới hạn.
- [ ] Kết nối hồ sơ nghe/phản hồi hiện tại, khử trùng ca khúc và ràng buộc độ đa dạng.
- [ ] Giữ luồng đề xuất tổng quát cho nội dung không phải nhạc.
- [ ] Đầu ra: danh sách có giải thích tín hiệu và nguồn gốc trong Debug, sẵn sàng tích hợp hàng đợi.

### Giai đoạn 3 — Thống nhất hàng đợi và tự phát tiếp

- [ ] Bổ sung loại hàng đợi và ngữ cảnh phiên; phân biệt bài người dùng chọn với phần tự động.
- [ ] Sửa mở bài từ tìm kiếm/trang đề xuất; nối bài khi sắp hết hàng đợi.
- [ ] Đồng bộ UI, nút Next, hết bài, mini player và các lệnh media đã hỗ trợ.
- [ ] Thêm công tắc tự phát tiếp; kiểm tra phát nền và hủy yêu cầu cũ.
- [ ] Đầu ra: nghe liên tiếp nhiều bài khác nhau; playlist chủ động vẫn đúng thứ tự.

### Giai đoạn 4 — Đọc và phát playlist từ nguồn

- [ ] Kiểm chứng adapter playlist với dependency hiện tại.
- [ ] Thêm model trang, repository, trạng thái phân trang và giao diện chi tiết.
- [ ] Nối thẻ playlist tìm kiếm và trang chủ vào cùng luồng mở/phát.
- [ ] Đầu ra: playlist thật mở được, phát được và đi qua nhiều trang mà không lặp bài do phân trang.

### Giai đoạn 5 — Các nhóm trên trang đề xuất

- [ ] Tạo trạng thái và dữ liệu riêng cho bài cùng phong cách, playlist, bài nhiều lượt xem và khám phá.
- [ ] Thêm đánh giá mẫu playlist, giảm trùng giữa các playlist và giữa các nhóm.
- [ ] Xử lý người dùng mới, thiếu dữ liệu và tải lỗi từng nhóm.
- [ ] Đầu ra: các nhóm hoạt động độc lập, bấm bài/playlist dẫn tới đúng chế độ phát.

### Giai đoạn 6 — Đánh giá và hoàn thiện

- [ ] Chạy kiểm thử tự động, thiết bị và đánh giá nghe thử theo mục 12–13.
- [ ] Hiệu chỉnh trọng số, ngưỡng tin cậy và ngân sách theo kết quả thực tế.
- [ ] Cập nhật tài liệu kiến trúc, giới hạn và release notes theo hành vi đã xác minh.
- [ ] Tạo APK kiểm thử và ghi nhận hash khi bước triển khai được thực hiện; việc phát hành là bước riêng sau khi kiểm tra đạt.

Các giai đoạn phụ thuộc nhau theo thứ tự trên. Có thể kiểm thử bộ xếp hạng trước khi hoàn thành UI; không coi việc chỉ đổi giao diện là đã xử lý xong lỗi chuyển bài.

## 12. Kế hoạch kiểm thử

### 12.1. Kiểm thử tự động bằng dữ liệu cố định

- Các phiên bản official/lyrics/cover/remix khác ID vẫn được nhận diện cùng ca khúc khi đủ bằng chứng.
- Tiếng Việt có dấu/không dấu, ký tự đặc biệt, tên rất ngắn, hai bài trùng tên và các bản liên khúc không bị gộp sai hàng loạt.
- Cùng phong cách nhưng khác tên/nghệ sĩ được ưu tiên hơn video giống tên nhưng sai ngữ cảnh.
- Bài nổi tiếng không phù hợp không vượt ngưỡng chỉ nhờ lượt xem; `viewCount` âm/thiếu không thành dữ liệu giả.
- Giới hạn kênh, ca khúc và cửa sổ 30 bài được áp dụng khi có đủ dữ liệu; tập nhỏ trả ít mục.
- Feedback, chặn kênh và đặt lại cá nhân hóa có hiệu lực với cache còn sống.
- Playlist được phân trang giữ đúng thứ tự; lỗi trang sau không bị coi là hết danh sách.
- Kết quả cũ A về sau B, tải lỗi một phần và hai sự kiện chuyển bài cùng đến không làm đổi sai bài.
- Tắt tự phát tiếp, dừng hoặc đóng player trong lúc đợi sẽ ngăn tự chuyển muộn.
- Các truy vấn đề xuất không xuất hiện trong lịch sử tìm kiếm người dùng.

Mở rộng các test liên quan hiện có: `RecommendationRankingTest`, `RecommendationProfileTest`, `SearchRankingTest`, `PersonalizedRecommendationsTest` và nhóm player UI. Thêm test riêng cho playlist từ nguồn và điều phối hàng đợi, ưu tiên hành vi quan sát được thay vì chỉ kiểm tra công thức nội bộ.

### 12.2. Kiểm tra trên thiết bị/emulator

- Android API 26 và API 35: phát bài đơn, Next/Previous, hết bài, mini player, audio-only, PiP và phát nền.
- Chuyển nhanh nhiều bài, đổi nguồn, mở playlist khác, tắt màn hình và thao tác Next ở các bề mặt media đã hỗ trợ.
- Mạng chậm, mất mạng, nguồn trả rỗng, giới hạn tần suất, playlist bị xóa hoặc có bài không khả dụng.
- Dữ liệu người dùng đang có vẫn còn: lịch sử, yêu thích, playlist cục bộ và vị trí phát.

Lệnh dự kiến khi đã triển khai, từ thư mục gốc trên PowerShell:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.aeroplayLive=true
```

Bài mạng thật phụ thuộc thiết bị/mạng/nguồn. Kết quả chạy được không đồng nghĩa với đề xuất đúng phong cách; đánh giá ngữ nghĩa cần thực hiện riêng.

### 12.3. Đánh giá chất lượng bằng mẫu nghe thật

Chọn tối thiểu 20 bài gốc, bao phủ ballad Việt, bolero, acoustic, remix/EDM, lofi/instrumental và nhạc quốc tế. Bổ sung ca khúc ít metadata, tên trùng và ít lượt xem để tránh chỉ đánh giá các trường hợp dễ.

Với mỗi bài, lưu snapshot 10 gợi ý đầu và nhãn người đánh giá: đúng phong cách, khác ca khúc, nghệ sĩ nếu biết, cùng kênh, lý do sai. Playlist lưu mẫu bài đã kiểm tra và đánh giá riêng. Snapshot có thời điểm kiểm tra vì kết quả nguồn có thể thay đổi.

So sánh trước/sau trên cùng tập ứng viên cố định để đo tác dụng xếp hạng; đánh giá luồng trực tiếp để đo thêm chất lượng thu thập ứng viên. Không trộn hai kết quả thành một tuyên bố chung về thuật toán.

## 13. Tiêu chí nghiệm thu

| Hạng mục | Điều kiện đạt |
| --- | --- |
| Chống lặp ca khúc | Fixture có đủ ứng viên phù hợp trả tối đa 10 ca khúc khác nhau; loại mọi biến thể của bài hiện tại được nhận diện chắc chắn |
| Bài trùng tên | Các fixture hai ca khúc khác nhau trùng tên vẫn giữ được bài hợp lệ; trường hợp thiếu dữ liệu có trạng thái tin cậy thấp |
| Cùng phong cách | Mục tiêu ban đầu trung bình ít nhất 7/10 gợi ý được người đánh giá xác nhận phù hợp trên tập mẫu đủ ứng viên; báo riêng các mẫu thiếu dữ liệu |
| Độ đa dạng | Tối đa 2 video/kênh trong 10 mục đầu; có ít nhất 3 nghệ sĩ khi nguồn có đủ ứng viên và nhận diện đáng tin cậy |
| Thứ tự chuyển bài | Bài đứng đầu Up Next chính là bài phát khi bấm Next hoặc hết bài; không nhảy hai bài khi hai sự kiện cùng tới |
| Playlist | Mở, phát từ bài được chọn và phân trang đúng thứ tự; không gán lỗi tải trang thành hết playlist |
| Nhạc phổ biến | Chỉ xếp các bài đã qua lọc bằng lượt xem hợp lệ trong tập tải được; UI không ghi lượt nghe hoặc top toàn nền tảng |
| Trạng thái cũ | Không có kết quả A ghi đè phiên B; dừng/đóng/tắt tự phát tiếp không gây phát muộn |
| Hiệu năng | Không thêm bước chờ đề xuất vào đường bắt đầu phát; đo thời gian và số request trên API 26/35, nằm trong ngân sách đã cấu hình |
| Hồi quy | Các kiểm thử liên quan đạt; playlist cục bộ, dữ liệu người dùng và luồng video không phải nhạc vẫn hoạt động |

Mốc 7/10 là mục tiêu nghiệm thu để đánh giá và hiệu chỉnh, chưa phải chất lượng đã đạt. Khi tập ứng viên không đủ thì ghi số mục thực tế và mức độ phủ, không loại các trường hợp khó khỏi báo cáo mà không giải thích.

## 14. Giới hạn và những việc cần xác minh khi triển khai

- Metadata không đủ để chắc chắn mọi ca khúc thuộc thể loại nào. Cần nhánh tin cậy thấp và đánh giá bằng nghe thử.
- Nguồn có thể không trả video liên quan, lượt xem hoặc nội dung playlist đầy đủ. Không cam kết luôn có đúng 10 bài hoặc mọi playlist đều phát được.
- Chống trùng dựa metadata có thể nhầm bài trùng tên hoặc bỏ sót tiêu đề biến dạng; phải ưu tiên tránh loại sai và bổ sung fixture từ lỗi thực tế.
- Phổ biến được hiểu là nhiều lượt xem trong tập bài phù hợp đã tải; chưa có bảng xếp hạng lượt nghe hoặc xu hướng thời gian thực.
- Việc cần đổi giao thức điều khiển MediaSession phải được xác định bằng kiểm tra đường Next thực tế; không suy từ việc nút Next trong UI hoạt động.
- Thuật toán không được tự lưu những bài/playlist đề xuất vào thư viện hoặc thay đổi dữ liệu người dùng.
- Phân tích âm thanh, embedding, dịch vụ metadata bên ngoài và bộ tuyển tập tự tạo là các hướng mở rộng sau, chỉ cân nhắc khi bản dựa trên nguồn hiện có được đánh giá chưa đủ tốt.

## 15. Hồ sơ bàn giao sau triển khai

- Danh sách thay đổi thực tế theo các hạng mục đã hoàn thành và hạng mục còn thiếu.
- Kết quả test tự động, thiết bị, mẫu đánh giá phong cách và số liệu request/thời gian tải.
- Các giới hạn còn tồn tại với ca khúc thiếu metadata và playlist đặc biệt.
- APK kiểm thử, phiên bản và checksum nếu đã build; trạng thái phát hành phải ghi riêng và có bằng chứng.

Các checkbox theo giai đoạn chỉ được đánh dấu hoàn tất khi đầu ra và bước xác nhận của giai đoạn đã đạt; phần mã đã làm được ghi ở mục 16, còn nghiệm thu tiếp tục giữ mở.

## 16. Nhật ký triển khai ngày 16/11/2026

- Đã thêm `SongIdentityResolver`, `MusicStyleAnalyzer`, `MusicRecommendationRanker` và `MusicSuggestionRepository` trong `app/src/main/java/com/aeroplay/app/data/repository/MusicRecommendations.kt`.
- Bài đơn có tín hiệu nhạc được mở bằng radio; video thường từ tìm kiếm vẫn giữ hàng đợi kết quả. Hàng đợi phát radio được bổ sung từ video liên quan và có thể tìm thêm một truy vấn phong cách khi nguồn liên quan chưa đủ.
- Đã chuyển phần Tiếp theo của player sang hiển thị chính xác các mục còn lại trong hàng đợi. Next, Previous và tự chuyển giữ lại loại hàng đợi.
- Đã thêm tùy chọn DataStore “Tự phát tiếp nhạc cùng phong cách”. Khi playlist nguồn kết thúc, radio chỉ được nối nếu đó là luồng playlist nhạc được đề xuất hoặc truy vấn có ngữ cảnh nhạc.
- Trang chủ đã có nhóm nhạc phù hợp/ngẫu nhiên, nhạc có nhiều lượt xem và playlist theo kết quả tìm kiếm nhạc. Nhóm phổ biến chỉ xếp view count trên tập ứng viên nguồn đã trả.
- Ngữ cảnh radio được truyền từ hàng đợi đang phát sang nhóm gợi ý trang chủ; các gợi ý bài nhạc áp dụng danh sách Không quan tâm/Đã xem và kênh bị chặn.
- Đã thêm đọc playlist NewPipe có giới hạn 80 video, phân trang hữu hạn, dialog xem/phát từ bài được chọn và adapter playlist phim Blender của nguồn Demo.
- Các kiểm thử và build vẫn chưa chạy. Việc kết nối media session ngoài UI, đánh giá bằng mẫu nghe, xử lý phân trang playlist vượt giới hạn và kiểm tra API 26/35 còn chờ.
