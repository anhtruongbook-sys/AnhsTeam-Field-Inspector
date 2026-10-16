# Quy Trình Khảo Sát Hiện Trường Và Thu Thập Dữ Liệu Số (Site Survey & Digital Field Data Acquisition)

Tài liệu này quy định quy trình kỹ thuật và pháp lý đối với công tác khảo sát xây dựng tại hiện trường (địa hình, địa chất công trình, địa chất thủy văn, khảo sát hiện trạng công trình hiện hữu và công trình lân cận) theo quy định tại **Luật Xây dựng số 135/2025/QH15** và **Nghị định số 217/2026/NĐ-CP**.

---

## 1. Quy Trình Pháp Lý Về Khảo Sát Xây Dựng (Nghị định số 217/2026/NĐ-CP)

Công tác khảo sát tại hiện trường phải tuân thủ trình tự 5 bước kiểm soát chất lượng:
1. **Lập và phê duyệt Nhiệm vụ khảo sát xây dựng**:
   - Do nhà thầu thiết kế hoặc tổ chức tư vấn có đủ điều kiện năng lực lập, Chủ đầu tư phê duyệt.
   - Nội dung bao gồm: mục đích khảo sát, phạm vi khảo sát, tiêu chuẩn kỹ thuật áp dụng (TCVN 9363:2012, TCVN 9437:2012, TCVN 9398:2012), khối lượng các loại công tác khảo sát dự kiến.
2. **Lập và phê duyệt Phương án kỹ thuật khảo sát xây dựng**:
   - Do nhà thầu khảo sát lập phù hợp với nhiệm vụ khảo sát được duyệt.
   - Quy định rõ sơ đồ vị trí các điểm khoan/đào/đo đạc, thiết bị sử dụng, quy trình thí nghiệm hiện trường (SPT, CPT, nén tĩnh, cắt cánh) và phòng thí nghiệm hợp chuẩn (LAS-XD).
3. **Giám sát công tác khảo sát tại hiện trường**:
   - Kiểm tra sự phù hợp về năng lực thực tế của chủ nhiệm khảo sát, nhân lực và thiết bị đo đạc/máy khoan tại hiện trường so với phương án kỹ thuật khảo sát đã duyệt.
   - Giám sát vị trí tọa độ hố khoan, chiều sâu khoan, quy trình lấy mẫu nguyên dạng, bảo quản mẫu và vận chuyển mẫu về phòng thí nghiệm.
4. **Khảo sát hiện trạng công trình lân cận (Pre-Construction Condition Survey)**:
   - Trước khi khởi công đào đất hố móng hoặc ép/khoan cọc, bắt buộc tổ chức khảo sát, chụp ảnh định vị, đo bề rộng vết nứt hiện hữu, độ nghiêng và cao độ nền của các công trình liền kề có chữ ký xác nhận của các bên liên quan.
5. **Nghiệm thu kết quả khảo sát xây dựng**:
   - Đối chiếu nhật ký khảo sát hiện trường, hồ sơ ảnh chụp có gắn tọa độ địa lý (Geotagged Photos) và kết quả thí nghiệm trước khi ký Biên bản nghiệm thu báo cáo kết quả khảo sát.

---

## 2. Phương Pháp Thu Thập Chứng Cứ Số Đa Phương Thức Tại Hiện Trường

Để bảo đảm tính trung thực với dữ liệu gốc tại hiện trường, hệ thống thu thập dữ liệu áp dụng 4 lớp thông tin đồng bộ:

| Lớp dữ liệu | Phương tiện thu thập | Thông số kỹ thuật bắt buộc | Mục đích kiểm chứng |
| :--- | :--- | :--- | :--- |
| **1. Định vị không gian (Spatial Georeferencing)** | GNSS RTK / Máy toàn đạc điện tử / GPS thiết bị di động | Hệ tọa độ VN-2000 hoặc WGS84 (Vĩ độ, Kinh độ, Cao độ $Z$, Sai số ngang DOP) + Góc phương vị la bàn (Azimuth $0^\circ - 360^\circ$). | Xác nhận nhân sự có mặt đúng vị trí hố khoan, cấu kiện hoặc vị trí khuyết tật trên mặt bằng tổng thể. |
| **2. Hình ảnh hiện trường (Visual Evidence)** | Camera di động / Máy ảnh kỹ thuật số / UAV Photogrammetry | Độ phân giải tối thiểu $1920 \times 1080$ px; giữ nguyên siêu dữ liệu EXIF gốc; đóng dấu thông tin (Thời gian, Tọa độ, Tên dự án, Mã cấu kiện) ở góc ảnh. | Lưu giữ bằng chứng trực quan đối với các công tác khuất lấp (cốt thép trước khi đổ bê tông, chống thấm, đường ống ngầm). |
| **3. Âm thanh ghi chú hiện trường (Field Voice Notes)** | Microphone định hướng / Điện thoại thông minh | Tần số lấy mẫu 16 kHz PCM Mono (`.wav` hoặc `.m4a`); gắn mã định danh trùng với mã bức ảnh/điểm đo (`PT-001_note.wav`). | Giúp kỹ sư ghi nhận nhanh thông số sai lệch khi tay đang cầm thước đo hoặc ở khu vực khó gõ văn bản. |
| **4. Số liệu đo đạc & Thí nghiệm hiện trường (In-situ Testing Data)** | Thiết bị đo kỹ thuật số (Thước laser, Súng bật nẩy, Máy siêu âm, Máy đo độ ẩm/nhiệt độ) | Mã thiết bị, hạn kiểm định thiết bị, giá trị đo tại từng điểm, giá trị trung bình, độ lệch chuẩn. | Cung cấp số liệu định lượng phục vụ đối chiếu với dung sai cho phép trong Tiêu chuẩn Quốc gia (TCVN). |

---

## 3. Quy Định Đặt Tên Tệp Dữ Liệu Khảo Sát Hiện Trường (Chuẩn CDE ISO 19650)

Mọi tệp ảnh chụp, bản ghi âm và bảng đo đạc thu thập tại hiện trường được đặt tên thống nhất theo cấu trúc:

```text
[MãDựÁn]-[ĐơnVị]-[KhuVực/Tầng]-[LoạiDữLiệu]-[BộMôn]-[SốThứTự]_[YYYYMMDD_HHMMSS].[ĐuôiTệp]
```

**Ví dụ minh họa**:
- `DA01-IBST-B1-IMG-STR-0012_20261016_093015.jpg`: Ảnh chụp kiểm tra kết cấu tầng hầm B1 số 0012 ngày 16/10/2026 lúc 09:30:15.
- `DA01-IBST-Z00-SUR-GEO-0003_20261016_101500.csv`: Dữ liệu khảo sát địa chất khu vực tổng mặt bằng số 0003.
- `DA01-IBST-L03-AUD-MEP-0005_20261016_142030.wav`: Ghi âm thuyết minh kiểm tra cơ điện tầng 3 số 0005.
