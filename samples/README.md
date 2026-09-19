# Bộ file mẫu để kiểm thử

Các file trong thư mục này dùng để thử PrintNest mà không cần đi tìm file in thật.

Mỗi file PDF được dựng bằng tay (xem `generate.mjs`) với **MediaBox và TrimBox đặt chính xác** theo số centimet ghi trong tên file. Nhờ vậy con số app đọc ra phải khớp tuyệt đối — lệch một chút là biết ngay bước đọc metadata có vấn đề.

> Muốn sinh lại: `cd samples && node generate.mjs`

---

## 1. Danh sách file

| File | Kích thước app **phải** đọc ra | Dùng để thử |
|---|---|---|
| `nhan-7x3.pdf` | 70,0 × 30,0 mm | Bộ nghiệm thu |
| `nhan-5x7.pdf` | 50,0 × 70,0 mm | Bộ nghiệm thu |
| `nhan-13x4.pdf` | 130,0 × 40,0 mm | Bộ nghiệm thu |
| `nhan-4x17.pdf` | 40,0 × 170,0 mm | Bộ nghiệm thu |
| `nhan-16x13.pdf` | 160,0 × 130,0 mm | Bộ nghiệm thu |
| `nhan-20x18.pdf` | 200,0 × 180,0 mm | Bộ nghiệm thu |
| `logo-30x5.pdf` | 300,0 × 50,0 mm | Thử cờ **khoá xoay** |
| `loi-qua-kho-60x59.pdf` | 600,0 × 590,0 mm | Thử **báo lỗi** rộng hơn khổ |
| `anh-600x400-300dpi.png` | **50,8 × 33,87 mm** | Thử đường xử lý **ảnh** |

Riêng file PNG đáng chú ý: ảnh 600 × 400 **pixel**, nhưng vì trong file có khai báo 300 DPI nên kích thước in thật chỉ là 50,8 × 33,87 mm (`600 ÷ 300 × 25,4`). Nếu app hiện ra một con số khác (ví dụ lấy 72 DPI thành 211 mm) thì phần đọc DPI đang sai.

---

## 2. Kịch bản A — Bộ nghiệm thu đầy đủ ⭐

Đây là kịch bản chính, dùng đúng số liệu thật của xưởng.

**Bước 1 — nạp file và đặt số lượng:**

| File | Số lượng | Cho xoay |
|---|---|---|
| `nhan-7x3.pdf` | 30 | ✔ |
| `nhan-5x7.pdf` | 10 | ✔ |
| `nhan-13x4.pdf` | 20 | ✔ |
| `nhan-4x17.pdf` | 15 | ✔ |
| `nhan-16x13.pdf` | 20 | ✔ |
| `nhan-20x18.pdf` | 50 | ✔ |

Chân trang phải hiện: **6 loại hình · 145 bản · diện tích hình 25.200 cm²**

**Bước 2 — tham số:** khổ ngang `57` cm · lề biên `0,5` cm · khoảng cách `0,3` cm · chiều dài tối đa **để trống** · cho phép xoay **bật**.

**Kết quả mong đợi:**

| Ô số liệu | Giá trị |
|---|---|
| Số file xuất ra | **1** |
| Tổng chiều dài | **478,2 cm** |
| Tỷ lệ lấp đầy | **92,45 %** |
| Tiết kiệm so với in rời | **71,9 %** |

File PDF tải về: `printnest-xxxxxxxx-tam01-57x478cm.pdf`, khổ trang đúng **570,00 × 4782,00 mm**.

> Thời gian tính khoảng **5–7 giây**. Màn hình hiện spinner kèm dòng "Đang tính toán bố trí…" trong lúc đó là đúng, không phải treo.

---

## 3. Kịch bản B — Giới hạn chiều dài mỗi file

Giống hệt kịch bản A, chỉ đổi **chiều dài tối đa mỗi file = `120` cm**.

**Kết quả mong đợi:**

| Ô số liệu | Giá trị |
|---|---|
| Số file xuất ra | **5** |
| Tổng chiều dài | **494,1 cm** |
| Tỷ lệ lấp đầy | **89,48 %** |

Bảng chi tiết từng file:

| Tấm | Kích thước (cm) | Lấp đầy |
|---|---|---|
| 1 | 57 × 117,8 | 90,43 % |
| 2 | 57 × 108,1 | 91,01 % |
| 3 | 57 × 113,8 | 89,05 % |
| 4 | 57 × 113,8 | 89,05 % |
| 5 | 57 × 40,6 | 85,04 % |

**Hai điều cần kiểm:**

1. **Không tấm nào dài quá 120 cm** — tấm dài nhất là 117,8 cm.
2. **Tổng số hình vẫn đúng 145** — chia tấm không được làm mất hay thừa bản nào.

Nút "Tải tất cả (.zip)" phải cho ra đúng **5 file** PDF bên trong.

Tổng dài tăng từ 478,2 lên 494,1 cm là **đúng như dự kiến**: cắt thành nhiều tấm thì mỗi tấm lại mất thêm phần lề hai đầu, và tấm cuối không bao giờ đầy.

---

## 4. Kịch bản C — Hình rộng hơn khổ (thử báo lỗi)

Nạp **`loi-qua-kho-60x59.pdf`**, số lượng 1, khổ ngang `57` cm.

**Kết quả mong đợi:** app **không** chạy ghép, mà báo lỗi ngay:

> **Không ghép được**
> Hinh "loi-qua-kho-60x59.pdf" 60.0x59.0 cm rong hon kho 57.0 cm (da tinh ca truong hop xoay).

Kèm nút "Sửa tham số và thử lại". Mã lỗi `ITEM_WIDER_THAN_SHEET`, HTTP 422.

Điểm quan trọng: thông báo phải **nêu đúng tên file nào** gây lỗi. Khi đơn có vài chục loại hình, báo chung chung "có hình quá khổ" thì thợ không biết phải sửa cái nào.

Thử đổi khổ ngang thành `70` cm rồi bấm lại — lần này phải ghép được bình thường.

---

## 5. Kịch bản D — Tác dụng của cờ "cho xoay" ⭐

Kịch bản này cho thấy rõ nhất vì sao cờ xoay lại quan trọng. Nạp **`logo-30x5.pdf`**, số lượng **12**, khổ `57` cm.

Chạy **hai lần**, chỉ khác nhau ở toggle "Cho xoay" trong bảng:

| | Cho xoay **TẮT** | Cho xoay **BẬT** |
|---|---|---|
| Tổng chiều dài | **64,3 cm** | **41,6 cm** |
| Tỷ lệ lấp đầy | 49,11 % | 75,91 % |
| Số hình bị xoay | 0 | 10 |
| Tiết kiệm vs in rời | 0,46 % | 35,60 % |

**Giải thích:** logo rộng 30 cm, mà khổ khả dụng chỉ 56,3 cm — **không nhét nổi hai cái cạnh nhau**. Khoá xoay thì mỗi logo chiếm trọn một hàng, xếp máy cũng chẳng hơn gì xếp tay (tiết kiệm 0,46 %). Cho xoay thì logo nằm dọc, bề ngang chỉ còn 5 cm, nhét được nhiều cái một hàng → **ngắn hơn 35 %**.

Bài học thực tế: chỉ khoá xoay khi **thật sự** cần (canh sợi vải, chữ phải đúng chiều). Khoá thừa là mất tiền.

---

## 6. Kịch bản E — Trộn ảnh và PDF

Nạp cả hai:

| File | Số lượng | Cho xoay |
|---|---|---|
| `anh-600x400-300dpi.png` | 20 | ✔ |
| `nhan-20x18.pdf` | 6 | ✔ |

Khổ `57` cm, lề `0,5`, gap `0,3`.

**Kết quả mong đợi:**

| Ô số liệu | Giá trị |
|---|---|
| Số file xuất ra | 1 |
| Tổng chiều dài | **48,67 cm** |
| Tỷ lệ lấp đầy | **90,26 %** |
| Tổng số hình | 26 |

Trong preview, hai loại hình phải có **hai màu khác nhau**, và mở file PDF ra thì cả ảnh lẫn hình vector đều nằm đúng ô của nó.

---

## 7. Bảng kiểm nhanh

- [ ] Kích thước đọc ra khớp cột 2 của mục 1, kể cả file PNG ra 50,8 × 33,87 mm
- [ ] Kịch bản A: 1 file, 478,2 cm, 92,45 %
- [ ] Kịch bản B: 5 file, không tấm nào quá 120 cm, tổng vẫn 145 hình
- [ ] Kịch bản C: báo lỗi nêu đúng tên file, không chạy ghép
- [ ] Kịch bản D: bật xoay cho kết quả ngắn hơn hẳn (41,6 so với 64,3 cm)
- [ ] Mở file PDF tải về: đúng khổ ngang 57 cm, hình không bị co giãn méo
- [ ] Rê chuột lên preview: tooltip hiện tên file, kích thước, đã xoay hay chưa
- [ ] Bấm thẳng URL `/nest/upload`: vào được, không lỗi 404

---

## 8. Lưu ý

**Các con số trên là kết quả đo thật**, chạy qua backend ở `localhost:8080` (Spring Boot 3.3.4, JDK 17), không phải ước lượng.

Thuật toán **tất định**: cùng bộ file và cùng tham số thì luôn ra đúng con số đó, chạy bao nhiêu lần cũng vậy. Nếu bạn nhận được kết quả khác, nghĩa là có tham số nào đó khác đi — hãy kiểm tra lại khổ, lề, gap và cờ xoay.

**Ở chế độ demo** (`VITE_USE_MOCK=true`, mặc định trên Netlify) các con số sẽ **khác và kém hơn**, vì trình duyệt chạy bản thuật toán rút gọn. Kịch bản A ở chế độ demo cho khoảng **494,9 cm / 89,33 %**, và nút tải PDF bị chặn kèm thông báo giải thích. Muốn đối chiếu đúng bảng trên thì phải chạy backend thật và đặt `VITE_USE_MOCK=false`.
