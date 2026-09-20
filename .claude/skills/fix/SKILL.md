---
name: fix
description: Sửa lỗi trong PrintNest — tính sai, hiển thị sai, API lỗi, build hỏng, deploy hỏng, kết quả ghép không như mong đợi. Dùng khi người dùng gõ /fix hoặc nói "bị lỗi", "sai rồi", "không chạy", "fix giúp", "sao nó lại ra thế này".
user-invocable: true
allowed-tools:
  - Read
  - Write
  - Edit
  - Glob
  - Grep
  - Bash
---

# /fix — Sửa lỗi

Mô tả lỗi nằm trong phần đối số sau `/fix`.

## Bước 0 — Nạp hợp đồng

Đọc **`docs/AGENT-GUIDE.md`**, đặc biệt **mục 5 "Bẫy đã gặp thật"** — rất nhiều lỗi lặp lại nằm sẵn trong bảng đó.

## Bước 1 — Tái hiện lỗi TRƯỚC khi sửa

Đây là bước không được bỏ. **Không sửa một lỗi mình chưa nhìn thấy.**

- Có file mẫu và kết quả kỳ vọng đã đo thật ở [`samples/README.md`](../../../samples/README.md) — dùng nó để tái hiện.
- Lỗi thuật toán → viết một test đỏ tái hiện đúng triệu chứng, rồi mới sửa.
- Lỗi giao diện → chạy `npm run dev` và thao tác thật.
- Lỗi API → gọi thẳng bằng `curl`, xem status và body thật.
- Lỗi deploy → đọc log thật trên Render/Netlify, đừng đoán.

Nếu **không tái hiện được**, nói thẳng với người dùng và hỏi thêm: thao tác cụ thể, file dùng, thông báo lỗi, ảnh chụp màn hình. Đừng sửa mò.

## Bước 2 — Tìm nguyên nhân gốc, không vá triệu chứng

Hỏi "tại sao" cho tới khi chạm nguyên nhân thật.

> Ví dụ có thật: bản Netlify vẫn chạy chế độ demo dù đã đặt `VITE_USE_MOCK=false` trên UI.
> Triệu chứng: badge demo không biến mất. Vá triệu chứng: ẩn badge đi — **sai**.
> Nguyên nhân gốc: `netlify.toml` khai cùng biến đó và có quyền cao hơn UI.

Phân biệt rõ ba loại và nói rõ trong báo cáo:

| Loại | Xử lý |
|---|---|
| **Lỗi code** | Sửa code + thêm test chặn tái diễn |
| **Lỗi cấu hình** (biến môi trường, CORS, gói dịch vụ) | Hướng dẫn người dùng sửa ở đúng chỗ — agent không tự vào dashboard được |
| **Không phải lỗi** (hiểu nhầm hành vi đúng) | Giải thích vì sao nó đúng, đừng đổi code cho vừa ý |

## Bước 3 — Sửa, rồi chặn tái diễn

- Sửa tối thiểu đủ trị tận gốc. Không nhân tiện refactor thứ không liên quan.
- **Thêm test** tái hiện đúng lỗi đó. Test phải đỏ trước khi sửa, xanh sau khi sửa.
- Nếu là một loại bẫy mới → **thêm một dòng vào bảng mục 5 của AGENT-GUIDE**.

## Bước 4 — Kiểm chứng

Chạy các lệnh ở mục 6 của AGENT-GUIDE. Hai điều phải chứng minh:

1. Lỗi đã hết (test mới xanh, hoặc thao tác thật cho kết quả đúng).
2. **Không làm hỏng chỗ khác** — 15/15 test vẫn pass, số liệu nghiệm thu **không xấu đi**.

> Nếu sửa xong mà chiều dài nghiệm thu tăng hoặc lấp đầy giảm, đó là đánh đổi — **phải báo rõ**, không được lặng lẽ hạ ngưỡng test cho qua.

## Bước 5 — Báo cáo

- **Triệu chứng** → **nguyên nhân gốc** → **cách sửa**
- Bằng chứng đã hết lỗi (kết quả lệnh thật)
- Test nào được thêm
- Nếu là lỗi cấu hình: nêu **chính xác** giá trị người dùng cần đặt, ở đâu

Thành thật: nếu chỉ vá tạm hoặc chưa chắc đã trị tận gốc, nói rõ điều đó.
