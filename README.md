# PrintNest — Ghép file in tiết kiệm khổ

Công cụ nội bộ tự động **dàn khuôn (nesting)** nhiều file in lên khổ cuộn cố định sao cho tốn ít chiều dài nhất, rồi xuất ra PDF đúng khổ để gửi thẳng cho máy in.

---

## 1. Bài toán này giải quyết chuyện gì?

Xưởng in trên **cuộn khổ rộng cố định** (ví dụ 57 cm), dài vô hạn, và **tính tiền theo mét dài**.

Nếu in từng file riêng lẻ, mỗi hình chiếm trọn chiều ngang khổ — phần còn thừa hai bên bị bỏ trắng. In 50 cái nhãn 20×18 cm theo cách đó sẽ ngốn gần gấp ba số mét cần thiết.

Cách làm cũ là mở Adobe Illustrator xếp tay. Một đơn vài chục loại hình mất hàng giờ, và kết quả vẫn không tối ưu vì mắt người không so được hàng trăm phương án xếp.

PrintNest thay thế việc đó:

```
Danh sách file + số lượng          PrintNest                 File PDF đã dàn sẵn
┌──────────────────────┐      ┌────────────────┐      ┌──────────────────────┐
│ nhan-A.pdf   × 50    │      │  Đọc kích thước │      │  57 cm × 478 cm      │
│ nhan-B.pdf   × 20    │ ───► │  Thử ~600 cách  │ ───► │  145 hình đã xếp     │
│ logo.pdf     × 15    │      │  xếp khác nhau  │      │  lấp đầy 92%         │
│ (khổ 57 cm, gap 3mm) │      │  Chọn cách ngắn │      │  giữ nguyên vector   │
└──────────────────────┘      └────────────────┘      └──────────────────────┘
```

**Kết quả đo được trên bộ dữ liệu thật của xưởng** (145 bản in, 6 loại hình, khổ 57 cm): dài **478,2 cm**, tỷ lệ lấp đầy **92,45%**, tiết kiệm **71,9%** so với cách in rời từng hình. Chi tiết ở [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#8-kết-quả-nghiệm-thu).

### Ràng buộc thực tế app phải tôn trọng

| Ràng buộc | Cách xử lý |
|---|---|
| Khổ ngang cố định | Người dùng nhập, thuật toán không bao giờ vượt |
| Khoảng cách tối thiểu giữa 2 hình | Tham số `gap`, đảm bảo đúng từng cặp hình |
| Lề biên | Tham số `margin`, chừa đều 4 phía |
| Một số hình **không được xoay** (logo, chữ, canh sợi vải) | Cờ `allowRotate` riêng từng dòng |
| Máy in yếu bị lag với file quá dài | `maxSheetLength` — tự chia thành nhiều file PDF |
| Số lượng phải đúng tuyệt đối | Bất biến được kiểm tra bằng unit test |
| **Tuyệt đối không tự co giãn hình** | Thuật toán chỉ tịnh tiến và xoay 90°, không scale |

---

## 2. Luồng sử dụng

```
  /                      /nest/upload          /nest/settings         /nest/result/:id
┌───────────┐          ┌────────────┐        ┌──────────────┐       ┌──────────────┐
│ Dashboard │  ──────► │ 1 Nạp file │ ─────► │ 2 Tham số in │ ────► │ 3 Kết quả    │
│ (cửa ngõ) │          │ + số lượng │        │ khổ/gap/lề   │       │ + tải PDF    │
└───────────┘          └────────────┘        └──────────────┘       └──────────────┘
```

Dashboard là **cửa ngõ duy nhất** — vào app là thấy màn này, phải bấm vào ô chức năng mới đi tiếp.

---

## 3. Cấu trúc repo

```
printnest/
├─ README.md                  ← bạn đang đọc file này
├─ .gitignore
├─ docs/                      ← tài liệu chi tiết, xem mục 7
├─ backend/                   ← Java 17+ / Spring Boot 3.3 / PDFBox 3
│  ├─ pom.xml
│  ├─ Dockerfile              ← multi-stage, JRE 21 slim
│  └─ src/
│     ├─ main/java/vn/printnest/
│     │  ├─ PrintNestApplication.java
│     │  ├─ common/           ← cấu hình, CORS, xử lý lỗi, quy đổi đơn vị
│     │  ├─ file/             ← nhận upload, đọc kích thước vật lý
│     │  ├─ nesting/          ← controller, service, và engine thuật toán
│     │  │  ├─ engine/        ← MaxRects, ShelfPacker, NestingEngine
│     │  │  └─ model/         ← NestRequest, Placement, Sheet, NestResult
│     │  ├─ export/           ← dựng PDF bằng PDFBox
│     │  └─ job/              ← trạng thái job chạy nền
│     └─ test/java/vn/printnest/
└─ frontend/                  ← Vue 3 + TypeScript + Vite
   ├─ netlify.toml
   ├─ .env.example
   └─ src/
      ├─ api/                 ← client, files, nesting, mock (chế độ demo)
      ├─ stores/              ← Pinia: nestingJob, ui
      ├─ assets/styles/       ← tokens.css, base.css, utilities.css
      ├─ components/{layout,ui,nesting}/
      └─ views/               ← Dashboard + 3 bước wizard + 404
```

---

## 4. Yêu cầu môi trường

| Thành phần | Phiên bản |
|---|---|
| JDK | **17 trở lên** (Dockerfile dùng 21) |
| Maven | 3.9+ |
| Node.js | 20+ |

---

## 5. Chạy ở máy local

### Backend

```bash
cd backend
mvn spring-boot:run
```

Chạy ở `http://localhost:8080`. Thư mục lưu file tạm mặc định là `backend/storage/`.

Chạy toàn bộ test (15 test: 10 cho thuật toán, 5 cho API):

```bash
cd backend
mvn clean verify
```

### Frontend

```bash
cd frontend
cp .env.example .env     # rồi sửa lại nếu cần
npm install
npm run dev
```

Mở `http://localhost:5173`.

Kiểm tra kiểu và build production:

```bash
npm run type-check
npm run build
```

---

## 6. Biến môi trường

### Frontend (`frontend/.env`)

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080` | URL gốc của backend, bỏ dấu `/` cuối |
| `VITE_USE_MOCK` | `true` | `true` = **chế độ demo**: chạy thuật toán rút gọn ngay trên trình duyệt, không cần backend. `false` = gọi API thật |

> **Chế độ demo** có để bản Netlify xem và thao tác được đầy đủ giao diện khi chưa có backend. Khi bật, header hiện badge "Chế độ demo". Chế độ này **không xuất được PDF thật** và cho tỷ lệ lấp đầy thấp hơn bản thật vài phần trăm — xem [docs/FRONTEND.md](docs/FRONTEND.md#7-chế-độ-demo-mock).

### Backend

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `PORT` | `8080` | Cổng lắng nghe (Render/Railway tự bơm vào) |
| `APP_STORAGE_PATH` | `./storage` | Thư mục lưu file tạm |
| `APP_MAX_FILE_SIZE` | `52428800` (50 MB) | Dung lượng tối đa mỗi file |
| `APP_CORS_ORIGINS` | `http://localhost:5173,...` | Danh sách origin được phép, cách nhau bằng dấu phẩy |

---

## 7. Tài liệu chi tiết

| File | Nội dung |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Sơ đồ khối, luồng dữ liệu, lý do chọn kiến trúc, **bảng kết quả nghiệm thu** |
| [docs/BACKEND.md](docs/BACKEND.md) | Cây package, phân tầng, **mô tả thuật toán nesting từng bước**, cách chạy test |
| [docs/FRONTEND.md](docs/FRONTEND.md) | Cây thư mục, quy ước component, state Pinia, luồng router, chế độ demo |
| [docs/API.md](docs/API.md) | Đặc tả đầy đủ từng endpoint kèm JSON mẫu và bảng mã lỗi |
| [docs/DESIGN-SYSTEM.md](docs/DESIGN-SYSTEM.md) | Bảng token, thang chữ, thang khoảng cách, danh sách component và props |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | Các bước deploy frontend lên Netlify và backend lên Render/Railway |

---

## 8. Giới hạn hiện tại

- **Chỉ xếp hình chữ nhật.** Hình bất quy tắc được coi là hình chữ nhật bao quanh nó. True-shape nesting (NFP) là hướng mở rộng, xem [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#7-điểm-mở-rộng-tương-lai).
- **Chưa kéo thả chỉnh tay** vị trí từng hình sau khi máy xếp.
- **Chưa có đăng nhập / phân quyền** — app dùng trong mạng nội bộ.
- **Job và file lưu trong bộ nhớ tiến trình.** Khởi động lại máy chủ là mất; người dùng được báo "hãy tải file lên lại". Interface `JobStore` đã tách sẵn để thay bằng database.
- Tỷ lệ lấp đầy đạt **92,45%** trên bộ nghiệm thu, thấp hơn mục tiêu 93% đặt ra ban đầu khoảng 0,55 điểm phần trăm. Phân tích nguyên nhân và các hướng cải thiện ở [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#8-kết-quả-nghiệm-thu).
