---
name: adjust
description: Điều chỉnh chức năng đã có trong PrintNest — đổi giá trị mặc định, đổi giao diện, đổi cách hiển thị, đổi thông báo, tinh chỉnh thuật toán, đổi ngưỡng. Dùng khi người dùng gõ /adjust hoặc nói "chỉnh lại", "đổi thành", "cho nó thành", "tăng/giảm", "muốn nó hiện khác đi".
user-invocable: true
allowed-tools:
  - Read
  - Write
  - Edit
  - Glob
  - Grep
  - Bash
---

# /adjust — Điều chỉnh chức năng có sẵn

Yêu cầu nằm trong phần đối số sau `/adjust`.

Khác với `/add` (làm cái chưa có) và `/fix` (sửa cái sai), skill này đổi **hành vi của cái đang chạy đúng**. Rủi ro chính ở đây là **làm hỏng thứ vốn đang tốt**.

## Bước 0 — Nạp hợp đồng

Đọc **`docs/AGENT-GUIDE.md`**. Đặc biệt mục 3 (bất biến) — điều chỉnh hay vô tình phá bất biến hơn là thêm mới.

## Bước 1 — Ghi lại trạng thái TRƯỚC khi đổi

Bắt buộc, để còn so sánh. Tuỳ loại điều chỉnh:

- Đụng thuật toán → chạy `./mvnw clean verify`, **chép lại dòng `[NGHIEM THU]`**
- Đụng giao diện → ghi lại màn hình/hành vi hiện tại
- Đụng giá trị mặc định → ghi lại giá trị cũ

Không có số "trước" thì không chứng minh được "sau" tốt hơn hay tệ đi.

## Bước 2 — Đổi đúng một chỗ

Mỗi loại điều chỉnh có **một nguồn sự thật duy nhất**. Sửa đúng chỗ đó, đừng rải rác:

| Điều chỉnh | Nguồn sự thật duy nhất |
|---|---|
| Màu, khoảng cách, cỡ chữ, bo góc | `frontend/src/assets/styles/tokens.css` |
| Giá trị mặc định của form | `DEFAULT_SETTINGS` trong `stores/nestingJob.ts` |
| Cách hiển thị số, quy đổi đơn vị | `frontend/src/api/units.ts` |
| Thông báo lỗi cho người dùng | Chỗ ném `ApiException` + `ErrorCode.java` |
| Tham số thuật toán (số vòng, hạt gieo, ngưỡng) | Các hằng `static final` đầu `NestingEngine.java` |
| Giới hạn upload, CORS, thư mục lưu | `backend/src/main/resources/application.yml` |

> Nếu thấy mình phải sửa cùng một giá trị ở hai nơi, **dừng lại** — đó là dấu hiệu thiết kế đang sai, báo cho người dùng biết.

## Bước 3 — Cập nhật tài liệu kèm theo

Điều chỉnh mà quên tài liệu là cách chắc chắn nhất để tài liệu thành vô dụng:

- Đổi token → `docs/DESIGN-SYSTEM.md`
- Đổi mặc định hoặc biến môi trường → `README.md` mục 6
- Đổi hành vi API → `docs/API.md`
- Đổi thuật toán → `docs/BACKEND.md` mục 6 và bảng kết quả trong `docs/ARCHITECTURE.md`

## Bước 4 — So sánh trước/sau

Chạy lại đúng lệnh đã chạy ở Bước 1, đặt hai kết quả cạnh nhau:

```
Trước:  478.2 cm | 92.45% | 145 hình
Sau:    ???.? cm | ??.??% | 145 hình
```

Ba khả năng, xử lý khác nhau:

- **Tốt hơn** → báo con số cải thiện
- **Không đổi** → xác nhận điều chỉnh không gây hồi quy
- **Tệ đi** → **nói thẳng mức độ tệ đi**, giải thích đây có phải đánh đổi người dùng chấp nhận không. Tuyệt đối không hạ ngưỡng test để giấu.

## Bước 5 — Báo cáo

- Đổi gì, từ giá trị nào sang giá trị nào
- Bảng so sánh trước/sau bằng số thật
- Tài liệu nào đã cập nhật
- Có đánh đổi gì không

Nếu điều chỉnh khiến kết quả xấu đi rõ rệt, **đề xuất hoàn tác** và để người dùng quyết.
