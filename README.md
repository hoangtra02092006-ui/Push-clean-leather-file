# Xưởng in Minh Trí — Ghép file in tiết kiệm khổ

Công cụ nội bộ tự động **dàn khuôn (nesting)** nhiều file in lên khổ cuộn cố định sao cho tốn ít chiều dài nhất, rồi xuất ra PDF đúng khổ để gửi thẳng cho máy in.

> **Về tên gọi.** Tên hiển thị trên web là **Xưởng in Minh Trí**. `PrintNest` là tên mã trong code — package `vn.printnest`, class `PrintNestApplication`, tiền tố log — giữ nguyên vì đổi tên package kéo theo toàn bộ cây thư mục mà không đem lại gì cho người dùng. File PDF tải về mang tiền tố `minh-tri-`.

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
├─ samples/                   ← file in mẫu + kết quả kỳ vọng để kiểm thử
├─ .claude/skills/            ← skill /add /fix /adjust cho Claude Code
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

| Thành phần | Phiên bản | Ghi chú |
|---|---|---|
| JDK | **17 trở lên** (Dockerfile dùng 21) | Bắt buộc cài |
| Maven | — | **Không cần cài.** Repo có sẵn Maven Wrapper (`mvnw`) |
| Node.js | 20+ | Bắt buộc cài |

Kiểm tra nhanh:

```bash
java -version    # phải ra 17 trở lên
node -v          # phải ra v20 trở lên
```

---

## 5. Chạy ở máy local

Cần **hai cửa sổ terminal** chạy song song: một cho backend, một cho frontend.

### Terminal 1 — Backend

```bash
cd backend
./mvnw spring-boot:run       # Windows (PowerShell / CMD): .\mvnw.cmd spring-boot:run
```

Chờ tới khi thấy dòng `Started PrintNestApplication`. Backend chạy ở `http://localhost:8080`.

> Dùng `mvnw` (Maven Wrapper) chứ không phải `mvn`: wrapper tự tải đúng phiên bản Maven về, nên không cần cài Maven và ai clone repo cũng build ra kết quả giống hệt nhau.

Thư mục lưu file tạm mặc định là `backend/storage/` (đã nằm trong `.gitignore`).

Chạy toàn bộ test (121 test):

```bash
cd backend
./mvnw clean verify          # Windows: .\mvnw.cmd clean verify
```

### Terminal 2 — Frontend

```bash
cd frontend
cp .env.example .env      # Windows PowerShell: copy .env.example .env
npm install               # chỉ cần chạy lần đầu
npm run dev
```

Rồi mở **http://localhost:5173**.

Mặc định `.env.example` có `VITE_USE_MOCK=true` (chế độ demo, không cần backend). Muốn dùng backend thật đang chạy ở Terminal 1, sửa `frontend/.env` thành:

```
VITE_API_BASE_URL=http://localhost:8080
VITE_USE_MOCK=false
```

> Đổi `.env` xong phải **khởi động lại `npm run dev`** — Vite chỉ đọc biến môi trường lúc khởi động.

Badge "Chế độ demo" trên header còn hiện tức là đang chạy mock; biến mất tức là đã nối được backend thật.

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
| `VITE_USE_MOCK` | _tự suy ra_ | `true` = **chế độ demo** (chạy thuật toán rút gọn ngay trên trình duyệt). `false` = gọi API thật. **Để trống thì tự suy ra**: chưa khai `VITE_API_BASE_URL` thì bật demo, khai rồi thì dùng backend thật |

> **Chế độ demo** có để bản Netlify xem và thao tác được đầy đủ giao diện khi chưa có backend. Khi bật, header hiện badge "Chế độ demo". Chế độ này **không xuất được PDF thật** và cho tỷ lệ lấp đầy thấp hơn bản thật vài phần trăm — xem [docs/FRONTEND.md](docs/FRONTEND.md#7-chế-độ-demo-mock).

### Backend

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `PORT` | `8080` | Cổng lắng nghe (Render/Railway tự bơm vào) |
| `APP_STORAGE_PATH` | `./storage` | Thư mục lưu file tạm |
| `APP_MAX_FILE_SIZE` | `52428800` (50 MB) | Dung lượng tối đa mỗi file |
| `APP_CORS_ORIGINS` | `http://localhost:5173,...` | Danh sách origin được phép, cách nhau bằng dấu phẩy |
| `APP_TRIM_ENABLED` | `true` | Đọc content stream của PDF để lấy đúng vùng có nét vẽ, bỏ khoảng trắng bao quanh. Đặt `false` để quay về lấy trọn khổ trang |
| `APP_RETENTION_HOURS` | `48` | Giữ file upload, ảnh xem trước và bản ghi lần ghép bao nhiêu giờ rồi tự xoá. Đặt `0` để **tắt hẳn** việc dọn rác |
| `APP_RETENTION_SWEEP_MINUTES` | `60` | Bao lâu quét dọn một lần |
| `APP_TIFF_DPI` | `300` | Độ phân giải bản TIF. Gấp đôi DPI là gấp **bốn** lần bộ nhớ |
| `APP_TIFF_MAX_MEGAPIXELS` | `100` | Trần cứng tính bằng triệu điểm ảnh. Vượt thì báo lỗi rõ ràng thay vì để máy chủ hết bộ nhớ |
| `APP_TIFF_COMPRESSION` | `Deflate` | Kiểu nén. Đổi về `LZW` nếu RIP đời cũ không đọc được Deflate |
| `APP_TIFF_COMPRESSION_QUALITY` | `0.3` | Mức nén 0..1. Số **nhỏ là nhanh**, file to hơn. Nén không mất dữ liệu nên đây chỉ là đánh đổi thời gian lấy dung lượng |
| `APP_TIFF_COLOR_MODE` | `cmyk` | `rgb` hoặc `cmyk` |
| `APP_TIFF_CMYK_PROFILE` |  `classpath:color/USWebCoatedSWOP.icc` | Hồ sơ ICC của không gian CMYK đích. **Nên thay bằng hồ sơ của chính máy in** nếu xin được từ nhà cung cấp RIP |
| `APP_TIFF_TRANSPARENT_LAYER` | `true` | Kèm lớp mang độ trong suốt để mở ra thấy ô caro xám (không có vùng in) thay vì nền trắng. Đổi lại ảnh bị lưu **hai lần** trong file |
| `APP_TIFF_LAYER_ZIP` | `true` | Nén lớp bằng ZIP. Trên cùng một tấm: ZIP cho file 15,8 MB dựng mất 5,0 s, RLE cho 21,6 MB dựng mất 1,5 s. Đặt `false` để quay về RLE như file mẫu |
| `APP_TIFF_WHITE_ENABLED` | `true` | Thêm kênh mực trắng lót cho in DTF. Đặt `false` để quay về CMYK thuần 4 kênh |
| `APP_TIFF_WHITE_CHANNEL` | `W1` | Tên kênh mực trắng, phải khớp đúng với cái RIP chờ đợi |
| `APP_TIFF_WHITE_ALPHA_THRESHOLD` | `128` | Ngưỡng alpha 0..255: trên ngưỡng thì coi là có hình, cần lót trắng |
| `APP_TIFF_WHITE_CHOKE` | `1` | Số điểm ảnh co lớp trắng vào trong mỗi phía, để nó nằm **lọt** trong lớp màu. Bằng đúng thao tác `Select → Modify → Contract → 1` |
| `APP_TIFF_WHITE_TOLERANCE` | `6` | Độ lệch cho phép so với trắng tuyệt đối khi dò nền của ảnh không có kênh trong suốt |
| `APP_CUT_DPI` | `300` | Độ phân giải để dò viền cắt, bằng đúng bản in. Gấp đôi DPI là gấp **bốn** lần số điểm phải quét |
| `APP_CUT_OFFSET_MM` | `0.0` | Nở đường cắt ra **ngoài viền lớp W1** bao nhiêu mm. `0` = chạy đúng trên viền đó |
| `APP_CUT_SIMPLIFY_MM` | `0.1` | Sai số cho phép khi bớt đỉnh của đường cắt |
| `APP_CUT_MIN_AREA_MM2` | `0.2` | Bỏ qua mảng nhỏ hơn mức này. **Chỉ để lọc hạt bụi** (một điểm ảnh ở 300 DPI là 0,007 mm²). Đặt cao hơn là ăn mất chữ nhỏ |
| `APP_CUT_STROKE_PT` | `0.25` | Độ dày nét đường cắt |
| `APP_CUT_SPOT_NAME` | `CutContour` | Tên màu mực riêng. Illustrator và Cutting Master nhận dạng đường cắt bằng **tên** này |
| `APP_CUT_MARK_LENGTH_MM` | `15.0` | Chiều dài cạnh dấu định vị (1,5 cm) |
| `APP_CUT_MARK_THICKNESS_MM` | `1.0` | Độ dày nét dấu |
| `APP_CUT_MARK_TOP_LEFT` · `_TOP_RIGHT` · `_BOTTOM_LEFT` · `_BOTTOM_RIGHT` | `SQUARE_WITH_EDGE` | Hình dấu **từng góc**. `SQUARE_WITH_EDGE` vẽ hai nét ở hai cạnh phía trong, khép ô vuông cùng mép trang. Còn nhận `L`, `SQUARE_OUTLINE`, `SQUARE_FILLED` |
| `APP_CUT_MARK_CLEARANCE_MM` | `5.0` | Vùng trống bắt buộc quanh dấu. Cộng với dấu là **20 mm** mỗi góc. Có nét vẽ lấn vào thì bản cắt **tự nới trang dài ra** (đều hai đầu, tối đa 20 mm mỗi đầu; bề ngang không đổi) — bản in không đổi |

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
| [docs/AGENT-GUIDE.md](docs/AGENT-GUIDE.md) | **Hợp đồng cho agent khi sửa hệ thống** — bản đồ file, bất biến, bẫy đã gặp, lệnh kiểm chứng. Nền của 3 skill `/add` `/fix` `/adjust` |

---

## 8. Sửa hệ thống bằng skill

Repo có sẵn 3 skill cho Claude Code, gõ thẳng trong phiên làm việc:

| Lệnh | Dùng khi | Ví dụ |
|---|---|---|
| `/add` | Thêm chức năng chưa có | `/add cho phép xuất thêm file SVG` |
| `/fix` | Sửa cái đang sai | `/fix preview vẽ lệch khi tấm dài hơn 3m` |
| `/adjust` | Đổi hành vi cái đang đúng | `/adjust để gap mặc định là 0.5cm` |

Cả ba đều nạp [docs/AGENT-GUIDE.md](docs/AGENT-GUIDE.md) trước khi động vào code, nên agent luôn biết: sửa file nào, bất biến nào không được phá, và phải chạy lệnh gì để chứng minh là đã xong.

---

## 9. Giới hạn hiện tại

- **Chỉ xếp hình chữ nhật.** Hình bất quy tắc được coi là hình chữ nhật bao quanh nó. True-shape nesting (NFP) là hướng mở rộng, xem [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#7-điểm-mở-rộng-tương-lai).
- **Chưa kéo thả chỉnh tay** vị trí từng hình sau khi máy xếp.
- **Chưa có đăng nhập / phân quyền** — app dùng trong mạng nội bộ.
- **Job và file lưu trong bộ nhớ tiến trình.** Khởi động lại máy chủ là mất; người dùng được báo "hãy tải file lên lại". Interface `JobStore` đã tách sẵn để thay bằng database.
- Tỷ lệ lấp đầy đạt **92,45%** trên bộ nghiệm thu, thấp hơn mục tiêu 93% đặt ra ban đầu khoảng 0,55 điểm phần trăm. Phân tích nguyên nhân và các hướng cải thiện ở [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#8-kết-quả-nghiệm-thu).
