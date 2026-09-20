---
name: add
description: Thêm một chức năng mới vào PrintNest (màn hình mới, endpoint mới, tham số ghép mới, số liệu mới, component mới). Dùng khi người dùng gõ /add hoặc nói "thêm chức năng", "làm thêm", "bổ sung tính năng", "tôi muốn có thêm".
user-invocable: true
allowed-tools:
  - Read
  - Write
  - Edit
  - Glob
  - Grep
  - Bash
---

# /add — Thêm chức năng mới

Yêu cầu của người dùng nằm trong phần đối số sau `/add`.

## Bước 0 — Nạp hợp đồng

Đọc **`docs/AGENT-GUIDE.md`** trước tiên. Đó là bản đồ file, danh sách bất biến, bẫy đã gặp và lệnh kiểm chứng. Không được bỏ qua.

## Bước 1 — Xác định phạm vi

Dùng bảng "Bản đồ" ở mục 2 của AGENT-GUIDE để liệt kê **chính xác** những file phải sửa.

Trả lời 4 câu trước khi gõ dòng code nào:

1. Chức năng này nằm ở **tầng nào** — chỉ frontend, chỉ backend, hay cả hai?
2. Có **đổi schema API** không? Nếu có → bắt buộc sửa cả `frontend/src/api/mock.ts`.
3. Có đụng vào **thuật toán** không? Nếu có → phải chạy lại bộ nghiệm thu và so số.
4. Có **bất biến nào** ở mục 3 bị ảnh hưởng không? Nếu có → **dừng, hỏi người dùng**, đừng tự quyết.

Nếu yêu cầu mơ hồ tới mức hai cách hiểu dẫn tới hai sản phẩm khác hẳn nhau, hỏi **một** câu gọn rồi làm tiếp. Còn lại tự quyết theo thông lệ của repo.

## Bước 2 — Làm theo thứ tự từ trong ra ngoài

Luôn đi **backend → API → frontend → tài liệu**, vì tầng ngoài phụ thuộc tầng trong:

```
1. model/record   →  2. engine/service  →  3. controller
                                              ↓
4. types/index.ts →  5. api/*.ts + mock.ts →  6. store  →  7. component/view
                                              ↓
                                        8. docs/*.md
```

Viết test **cùng lúc** với code, không để cuối. Với thuật toán, test phải kiểm lại bộ bất biến (không chồng lấn, đủ gap, đúng số lượng).

## Bước 3 — Thêm chức năng vào Dashboard

Nếu là một **chức năng người dùng thấy được**, thêm ô vào mảng `features` trong `DashboardView.vue`:

- Đang làm xong → `available: true`, badge xanh "Hoạt động", có `route`
- Chưa làm → `available: false`, badge "Sắp có", nền xám, `cursor: not-allowed`

Icon vẽ tay theo bộ Lucide, SVG inline 24px — **không** nạp thư viện icon.

## Bước 4 — Kiểm chứng

Chạy đủ các lệnh ở mục 6 của AGENT-GUIDE tuỳ theo tầng đã đụng. **Dán kết quả thật** vào báo cáo, kể cả khi có test đỏ.

## Bước 5 — Báo cáo

Bằng tiếng Việt, gọn:

- Đã thêm gì, sửa những file nào
- Kết quả lệnh kiểm chứng (số liệu thật)
- Số liệu nghiệm thu trước/sau, nếu đụng thuật toán
- **Những gì chưa làm được và vì sao**

Chỉ commit khi người dùng yêu cầu. Nếu commit, theo Conventional Commits và chia thành các commit có nghĩa.
