# Triển khai

## ⚠️ Lưu ý kỹ thuật quan trọng

**Netlify không chạy được backend Java.** Netlify chỉ host nội dung tĩnh và serverless function (JavaScript/Go), không chạy được JVM lâu dài.

Vì vậy hệ thống được tách làm hai nơi:

```
┌──────────────────────────┐          ┌───────────────────────────────┐
│  Netlify                 │   REST   │  Render / Railway / Fly.io    │
│  frontend/ (tĩnh)        │ ───────► │  backend/ (Docker, JVM)       │
│  printnest.netlify.app   │          │  printnest-api.onrender.com   │
└──────────────────────────┘          └───────────────────────────────┘
```

Nếu chỉ deploy frontend lên Netlify mà chưa có backend, app vẫn xem và thao tác được đầy đủ giao diện nhờ **chế độ demo** (`VITE_USE_MOCK=true`) — chỉ không xuất được PDF thật.

---

## 1. Frontend lên Netlify

### 1.1. Cấu hình có sẵn

`frontend/netlify.toml` đã khai báo đủ:

```toml
[build]
  base = "frontend"
  command = "npm run build"
  publish = "dist"

[build.environment]
  NODE_VERSION = "20"

[[redirects]]
  from = "/*"
  to = "/index.html"
  status = 200
```

> **`[[redirects]]` là bắt buộc.** App dùng `createWebHistory`, nên vào thẳng `/nest/upload` mà không có dòng này sẽ ra lỗi 404 của Netlify thay vì trang Vue.

### 1.2. Các bước

1. Đăng nhập [app.netlify.com](https://app.netlify.com) → **Add new site** → **Import an existing project**.
2. Chọn GitHub và chọn repo này.
3. Netlify tự đọc `netlify.toml`; xác nhận:
   - Base directory: `frontend`
   - Build command: `npm run build`
   - Publish directory: `frontend/dist`
4. Bấm **Deploy site**.

Xong bước này bạn đã có bản demo chạy được đầy đủ giao diện.

### 1.3. Nối với backend thật (làm sau khi deploy backend)

1. Vào **Site configuration → Environment variables**, thêm:

   | Key | Value |
   |---|---|
   | `VITE_API_BASE_URL` | `https://printnest-api.onrender.com` (URL backend thật, **không** có `/` ở cuối) |
   | `VITE_USE_MOCK` | `false` |

2. Vào **Deploys → Trigger deploy → Clear cache and deploy site**.

   > Vite nhúng biến môi trường vào bundle **lúc build**, không đọc lúc chạy. Đổi biến xong **bắt buộc phải build lại**, nếu không sẽ không có tác dụng gì.

3. Badge "Chế độ demo" trên header biến mất — đó là dấu hiệu app đã dùng backend thật.

---

## 2. Backend lên Render

`backend/Dockerfile` là multi-stage: giai đoạn đầu build bằng Maven + JDK 21, giai đoạn sau chỉ chép file `.jar` sang ảnh JRE 21 Alpine, chạy bằng tài khoản thường (không phải root).

### 2.1. Các bước

1. Đăng nhập [render.com](https://render.com) → **New** → **Web Service**.
2. Kết nối repo này.
3. Cấu hình:

   | Mục | Giá trị |
   |---|---|
   | Language | **Docker** |
   | Root Directory | `backend` |
   | Dockerfile Path | `backend/Dockerfile` |
   | Instance Type | Starter trở lên (**không** dùng Free — xem mục 2.3) |

4. Thêm biến môi trường:

   | Key | Value |
   |---|---|
   | `APP_CORS_ORIGINS` | `https://ten-site-cua-ban.netlify.app` |
   | `APP_STORAGE_PATH` | `/app/storage` |
   | `APP_MAX_FILE_SIZE` | `52428800` (tuỳ chọn, mặc định 50 MB) |

   > **`PORT` do Render tự bơm vào** — không cần tự đặt. `application.yml` đã đọc sẵn `${PORT:8080}`.

5. **Add Disk** (khuyến nghị): mount path `/app/storage`, dung lượng 1 GB.

   Không có disk thì file tải lên sẽ mất mỗi lần container khởi động lại, và người dùng gặp lỗi `FILE_NOT_FOUND` giữa chừng.

6. **Create Web Service**. Lần build đầu mất khoảng 5–8 phút.

### 2.2. Kiểm tra

```bash
curl -i https://printnest-api.onrender.com/api/v1/nesting/jobs/test
```

Đúng thì trả về `404` kèm JSON:

```json
{"error":{"code":"JOB_NOT_FOUND","message":"Khong tim thay lan ghep test. ..."}}
```

Nhận được JSON này nghĩa là ứng dụng đã chạy và tầng xử lý lỗi hoạt động.

### 2.3. Vì sao không dùng gói Free của Render

Gói Free ngủ đông sau 15 phút không có request, và mất khoảng 50 giây để thức dậy. Thuật toán nesting lại ngốn CPU (5 giây cho một đơn 145 hình) và gói Free chỉ có 0,1 CPU — một đơn lớn có thể chạy hàng phút hoặc bị giết giữa chừng.

### 2.4. Railway / Fly.io

Cách làm tương tự — cả hai đều đọc được `backend/Dockerfile`:

- **Railway:** New Project → Deploy from GitHub → đặt Root Directory `backend`. Railway tự phát hiện Dockerfile và bơm `PORT`.
- **Fly.io:** `cd backend && fly launch --dockerfile Dockerfile`, rồi `fly volumes create storage --size 1` và gắn vào `/app/storage` trong `fly.toml`.

---

## 3. Thứ tự làm và danh sách kiểm tra

```
1. Push code lên GitHub
2. Deploy backend lên Render            → lấy được URL backend
3. Deploy frontend lên Netlify          → lấy được URL Netlify
4. Đặt APP_CORS_ORIGINS ở Render        = URL Netlify
5. Đặt VITE_API_BASE_URL ở Netlify      = URL backend
6. Đặt VITE_USE_MOCK=false ở Netlify     (không bắt buộc: để trống cũng tự hiểu)
7. Trigger deploy lại frontend          ← BƯỚC HAY BỊ QUÊN NHẤT
```

- [ ] Mở trang Netlify, badge "Chế độ demo" **không** còn hiện
- [ ] Tải thử một file PDF, kích thước đọc ra đúng
- [ ] Ghép thử, tải được file PDF về và mở lên đúng khổ
- [ ] Mở thẳng `https://ten-site.netlify.app/nest/upload` bằng URL, không bị 404

---

## 4. Xử lý sự cố thường gặp

| Hiện tượng | Nguyên nhân | Cách sửa |
|---|---|---|
| "Không kết nối được tới máy chủ" | `VITE_API_BASE_URL` sai, hoặc backend đang ngủ | Kiểm tra URL không có `/` ở cuối; mở thẳng URL backend xem có sống không |
| Lỗi CORS trong Console trình duyệt | `APP_CORS_ORIGINS` chưa có domain Netlify | Thêm chính xác cả `https://`, rồi khởi động lại backend |
| Đổi biến ở Netlify mà không thấy gì đổi | Chưa build lại | **Trigger deploy → Deploy project without cache** |
| Đặt biến trong Netlify UI mà vẫn vô hiệu | Biến cùng tên được khai trong `[build.environment]` của `netlify.toml` — file này có quyền **CAO HƠN** UI | Xoá biến đó khỏi `netlify.toml` |
| 404 khi vào thẳng `/nest/upload` | Thiếu `[[redirects]]` | Kiểm tra `netlify.toml` đã được commit chưa |
| `FILE_NOT_FOUND` giữa chừng | Container đã khởi động lại, file tạm mất | Gắn disk vào `/app/storage` |
| `JOB_NOT_FOUND` sau khi chờ lâu | Job lưu trong RAM, máy chủ đã khởi động lại | Làm lại từ bước 1; muốn bền thì thay `JobStore` bằng bản dùng DB |
| Ghép chạy rất lâu rồi lỗi | Gói máy chủ quá yếu | Nâng instance, hoặc đặt `maxSheetLengthMm` để chia nhỏ |

---

## 5. Ghi chú vận hành

- **File tạm tự dọn sau 48 giờ.** `StorageJanitor` quét mỗi giờ, xoá file quá hạn ở **cả ba chỗ**: file trên đĩa, siêu dữ liệu trong RAM (kèm bản đồ chiếm chỗ — thứ nặng nhất, file khổ lớn giữ tới nửa MB), và ảnh xem trước đã dựng. Bản ghi lần ghép cũng bị dọn theo, kể cả job kẹt ở trạng thái đang chạy.

  Chỉnh bằng `APP_RETENTION_HOURS` (đặt `0` để tắt hẳn) và `APP_RETENTION_SWEEP_MINUTES`. Không có cái này thì đĩa và RAM phình vô hạn: có gắn volume thì đĩa đầy rồi dừng hẳn, không gắn thì RAM phình tới lúc JVM hết chỗ và tự khởi động lại — mất sạch file đang làm dở.
- **Không có xác thực.** App thiết kế cho mạng nội bộ. Đưa ra Internet công khai thì nên đặt sau một lớp bảo vệ (Cloudflare Access, basic auth ở reverse proxy, hoặc thêm Spring Security).
- **Bộ nhớ.** `JAVA_OPTS` đặt `-XX:MaxRAMPercentage=75`. Instance 512 MB là đủ cho đơn cỡ bộ nghiệm thu.
