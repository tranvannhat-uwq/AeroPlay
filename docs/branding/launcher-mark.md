# Adaptive launcher AeroPlay

Asset: `app/src/main/res/drawable-nodpi/brand_launcher_mark.png` (1254 × 1254, alpha).

SHA-256: `B3D485299D351F5E28D38BD38384F556B86D2B69861C1C6D249AC1598B7974C0`.

Tạo bằng công cụ **imagegen tích hợp**, chế độ chỉnh sửa với nền trong suốt. Đầu vào là `brand_app_icon.png` gốc; không ghi đè ảnh gốc. Đầu ra được sao chép vào resource dự án, không phụ thuộc đường dẫn công cụ tạo ảnh.

Prompt đã dùng:

```text
Use case: background-extraction. Edit target: the supplied AeroPlay square icon. Create a production Android adaptive-icon FOREGROUND layer on a square transparent canvas. Keep only the central luminous right-facing play triangle and the three flowing cyan air/ribbon strokes sweeping through it. Preserve the existing glossy cyan, electric blue, white highlight and violet gradient identity and silhouette of this central emblem. REMOVE the entire outer rounded-square tile, its enclosing neon border, all corner flourishes belonging to that tile, and ALL navy/black background. No enclosing circle, square, badge, plate or dark matte. The output must have real alpha transparency around and behind the isolated emblem. Center the whole emblem optically, scaled so that all its visible strokes and soft glow fit within a centered safe CIRCLE whose diameter is 64 percent of the square canvas; leave transparent padding outside this safe circle. This padding is solely for Android adaptive icon masking: do not paint it. Clean antialiased edges, bright legible silhouette at small sizes, no lettering, no added shapes. Output ONLY the isolated play-and-airflow emblem, no mockup.
```

Đầu ra giữ kích thước ảnh gốc; khoảng an toàn cuối cùng được điều chỉnh bằng drawable Android, không dựa vào kích thước chủ thể mà prompt yêu cầu. `ic_launcher_foreground.xml` dùng inset 20% cho dấu Play/dải sóng trong suốt; `ic_launcher_background.xml` vẽ gradient cyan/xanh dương/tím phủ kín mask launcher. Không đặt ảnh tile vuông nhỏ lên nền đen. Splash tiếp tục dùng `brand_app_icon.png` và vùng đệm riêng, không thay bằng adaptive launcher.

Kiểm tra API 35: adaptive icon inflate/render có monochrome, hơn 200 màu; các điểm gần bốn mép mask đều có nền màu đục, không có vòng đen. Test vùng an toàn splash cũng pass. Ảnh render cuối: `.private/brand-review/launcher-no-ring.png`; cần tiếp tục xác nhận trên launcher OEM của điện thoại thật.
