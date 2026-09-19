# Kiến trúc PrintNest

## 1. Sơ đồ khối

```
┌──────────────────────────────────────────────────────────────────────────┐
│                      TRÌNH DUYỆT (Netlify / localhost)                   │
│                                                                          │
│   Vue 3 + TypeScript + Vite                                              │
│   ┌────────────┐  ┌──────────────┐  ┌───────────────────────────┐       │
│   │ views/     │  │ stores/      │  │ api/                      │       │
│   │ Dashboard  │◄─┤ nestingJob   │◄─┤ client.ts (axios)         │       │
│   │ 3 bước     │  │ ui (toast)   │  │ files.ts / nesting.ts     │       │
│   └────────────┘  └──────────────┘  │ mock.ts ◄── chế độ demo   │       │
│                                     └─────────────┬─────────────┘       │
└───────────────────────────────────────────────────┼─────────────────────┘
                                                    │ REST + JSON
                                                    │ (đơn vị: MILIMET)
                                                    ▼
┌──────────────────────────────────────────────────────────────────────────┐
│               BACKEND — Spring Boot monolith (Render/Railway)            │
│                                                                          │
│   ┌──────────────┐   ┌──────────────────┐   ┌────────────────────────┐  │
│   │ file/        │   │ nesting/         │   │ export/                │  │
│   │ Controller   │   │ Controller       │   │ ExportController       │  │
│   │ Service      │   │ Service ─────────┼──►│ PdfComposer (PDFBox)   │  │
│   │ MetadataRead │   │ JobRunner @Async │   └────────────┬───────────┘  │
│   └──────┬───────┘   │   └─► engine/    │                │              │
│          │           │       NestingEngine               │              │
│          │           │       MaxRectsPacker              │              │
│          │           │       ShelfPacker                 │              │
│          │           └──────────┬───────┘                │              │
│          │                      │                        │              │
│          ▼                      ▼                        ▼              │
│   ┌─────────────┐   ┌──────────────────┐   ┌────────────────────────┐  │
│   │ Đĩa         │   │ job/             │   │ Trả về                 │  │
│   │ storage/    │   │ InMemoryJobStore │   │ application/pdf, .zip  │  │
│   │ (file gốc)  │   │ ConcurrentHashMap│   │                        │  │
│   └─────────────┘   └──────────────────┘   └────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
```

## 2. Luồng dữ liệu đầy đủ

Từ lúc người dùng kéo file vào cho tới lúc cầm được PDF:

```
 1. Người dùng thả file vào FileDropzone
       │
       ▼
 2. POST /api/v1/files  (multipart, từng file một)
       │   FileService lưu file xuống đĩa với tên = UUID
       │   FileMetadataReader đọc kích thước VẬT LÝ:
       │     · PDF  → TrimBox → CropBox → MediaBox (ưu tiên giảm dần)
       │     · Ảnh  → pixel ÷ DPI trong metadata (không có thì 72 DPI)
       ▼
 3. Trả về [{ id, originalName, widthMm, heightMm, previewUrl }]
       │   Frontend đẩy vào store.items, mặc định quantity=1, allowRotate=true
       ▼
 4. Người dùng sửa số lượng / kích thước / cờ xoay (bước 1)
    rồi nhập khổ, lề, gap, giới hạn dài (bước 2)
       │
       ▼
 5. POST /api/v1/nesting/jobs
       │   NestingService kiểm tra ĐỒNG BỘ (hình có rộng hơn khổ không?)
       │   → sai thì báo lỗi ngay, không tạo job
       │   → đúng thì tạo Job(PENDING), trả 202 { jobId }
       │   NestingJobRunner.run() chạy ở luồng nền
       ▼
 6. Frontend poll GET /api/v1/nesting/jobs/{jobId} mỗi 700 ms
       │
       │   Song song, ở luồng nền:
       │   NestingEngine.nest()
       │     a. Nở mỗi hình thêm một gap
       │     b. Sinh ~66 phương án chuẩn bị đầu vào (ép hướng từng loại hình)
       │     c. Mỗi phương án × 6 thứ tự sắp xếp × 5 packer
       │     d. Mỗi tổ hợp: tìm nhị phân chiều dài nhỏ nhất
       │     e. Leo đồi tinh chỉnh quanh 4 phương án dẫn đầu
       │     f. Chọn kết quả ngắn nhất → Job(DONE, result)
       ▼
 7. Job chuyển DONE → frontend dựng SheetPreview (SVG) + bảng số liệu
       │
       ▼
 8. Người dùng bấm "Tải PDF"
       │   GET /api/v1/nesting/jobs/{id}/sheets/{index}/pdf
       │   PdfComposer tạo trang đúng kích thước rồi, với mỗi hình:
       │     · PDF nguồn → LayerUtility.importPageAsForm + AffineTransform
       │       (GIỮ NGUYÊN VECTOR — không raster hoá)
       │     · Ảnh nguồn → PDImageXObject đặt đúng kích thước vật lý
       │     · Vẽ thêm khung cắt xám 0,25 pt
       ▼
 9. Trả về application/pdf (hoặc .zip cho tất cả các tấm)
```

## 3. Quy ước đơn vị — chỗ dễ sai nhất

Hệ thống đi qua **ba** hệ đơn vị. Mỗi tầng chỉ được biết một hệ:

| Tầng | Đơn vị | Lý do |
|---|---|---|
| Giao diện | **centimet** | Thợ ở xưởng nghĩ bằng cm |
| API (JSON) | **milimet** | Đủ mịn, số tròn, không lẫn lộn |
| Thuật toán | **1/100 mm, số nguyên** | Không có sai số dấu phẩy động |
| File PDF | **point** (1 inch = 72 pt) | Quy ước của PostScript/PDF |

Toàn bộ việc quy đổi tập trung ở **hai** chỗ duy nhất: `Units.java` ở backend và `src/api/units.ts` ở frontend. Không component hay service nào được tự chia 10.

## 4. Vì sao chọn monolith?

Đây là công cụ nội bộ cho **một xưởng**, vài người dùng đồng thời. Tách microservice ở quy mô này chỉ đẻ thêm việc: cấu hình mạng, truy vết lỗi qua nhiều tiến trình, triển khai nhiều đơn vị.

Nhưng monolith **không** có nghĩa là trộn lẫn. Code tổ chức **package-by-feature**: mỗi package con (`file`, `nesting`, `export`, `job`) chứa trọn bộ controller–service–model của một nghiệp vụ. Muốn tách `nesting` thành service riêng sau này thì bê nguyên package đi, không phải gỡ từng lớp ra khỏi `controllers/`, `services/`, `repositories/`.

## 5. Vì sao job chạy bất đồng bộ?

Thuật toán ngốn CPU: bộ dữ liệu 145 hình mất khoảng **5 giây**, đơn lớn hơn sẽ lâu hơn. Nếu chạy ngay trong luồng xử lý HTTP:

- Trình duyệt và proxy thường timeout ở 30–60 giây.
- Người dùng nhìn màn hình đơ, không biết app còn sống hay đã chết.
- Một đơn lớn chiếm luôn luồng HTTP, chặn cả người khác.

Nên: `POST` trả về `202 Accepted` kèm `jobId` ngay lập tức, việc thật chạy ở `ThreadPoolTaskExecutor` riêng, frontend poll trạng thái mỗi 700 ms và hiện spinner kèm dòng mô tả.

> **Một bẫy đã tránh:** Spring hiện thực `@Async` bằng proxy, nên một bean **tự gọi** phương thức `@Async` của chính nó sẽ chạy đồng bộ như thường. Vì vậy `NestingJobRunner` là bean **riêng**, không phải một phương thức của `NestingService`.

## 6. Vì sao chưa cần database?

Ở MVP, dữ liệu cần lưu chỉ gồm file gốc (nằm trên đĩa) và trạng thái job (nằm trong `ConcurrentHashMap`). Không có lịch sử, không có người dùng, không có báo cáo.

Thêm database lúc này là thêm một thứ phải cài, phải backup, phải migrate — đổi lại chưa được gì.

Nhưng `JobStore` **đã là interface**, và `InMemoryJobStore` chỉ là một cách hiện thực. Khi cần lịch sử ghép, viết `JpaJobStore` rồi đổi bean — tầng service không phải sửa một dòng.

Hệ quả phải chấp nhận: **khởi động lại máy chủ là mất hết job và file**. Thông báo lỗi `JOB_NOT_FOUND` / `FILE_NOT_FOUND` đã nói thẳng điều này cho người dùng ("Có thể máy chủ đã khởi động lại, hãy tải file lên lại").

## 7. Điểm mở rộng tương lai

| Hướng | Cần đụng vào đâu |
|---|---|
| **Lịch sử ghép** | Viết `JpaJobStore implements JobStore`, thêm entity, đổi bean |
| **True-shape nesting** (hình bất quy tắc) | Thêm packer mới cạnh `MaxRectsPacker`; `NestingEngine` đã gom mọi packer qua cùng một vòng chọn phương án nên chỉ cần thêm vào danh sách ứng viên |
| **Kéo thả chỉnh tay** | `Placement` đã mang đủ toạ độ; cần thêm endpoint nhận lại danh sách placement đã sửa rồi xuất PDF từ đó |
| **Đăng nhập / phân quyền** | Thêm Spring Security, gắn `userId` vào `Job` |
| **Thư viện file dùng lại** | `StoredFile` đã có sẵn; chỉ cần bỏ phần xoá và thêm endpoint liệt kê |

Bốn ô "Sắp có" trên Dashboard tương ứng các hướng này, để sẵn chứ chưa cài.

## 8. Kết quả nghiệm thu

### Bộ dữ liệu

Số liệu thật của xưởng — khổ **57 cm**, gap **0,3 cm**, lề **0,5 cm**, cho phép xoay:

| Kích thước (cm) | Số lượng |
|---|---|
| 7 × 3 | 30 |
| 5 × 7 | 10 |
| 13 × 4 | 20 |
| 4 × 17 | 15 |
| 16 × 13 | 20 |
| 20 × 18 | 50 |
| **Tổng** | **145 bản** |

Tổng diện tích hình: **25.200 cm²**.

### Kết quả đo được

| Kịch bản | Số tấm | Tổng dài | Tỷ lệ lấp đầy | Tiết kiệm vs in rời | Thời gian |
|---|---|---|---|---|---|
| Không giới hạn chiều dài | 1 | **478,2 cm** | **92,45%** | **71,9%** | ~5,1 s |
| `maxSheetLength = 1200 mm` | 5 | 494,1 cm | 89,48% | — | ~6 s |

Cả hai kịch bản đều **giữ đúng 145 hình**, không hình nào chồng lấn, không hình nào vượt khổ, mọi khoảng hở ≥ 3 mm. Các bất biến này được kiểm tra tự động trong `NestingEngineTest`.

Chế độ demo trên trình duyệt (thuật toán rút gọn) đạt **494,9 cm / 89,33%** trên cùng bộ dữ liệu — thấp hơn bản thật khoảng 3 điểm phần trăm, đúng như thiết kế.

### Về mục tiêu 93%

Mục tiêu đặt ra ban đầu là ≥ 93%; kết quả đo được là **92,45%**, thiếu **0,55 điểm phần trăm** (tương đương khoảng 28 mm trên tổng 4.782 mm).

**Vì sao còn thiếu.** Cận dưới lý thuyết của bài toán này — tổng diện tích các hình *đã nở gap* chia cho bề rộng khả dụng — là 4.668 mm, ứng với tỷ lệ lấp đầy tối đa **94,6%**. Để đạt 93% cần chiều dài ≤ 4.754 mm, tức hiệu suất xếp phải đạt **98,2%** so với cận dưới. Đó là mức gần như hoàn hảo, nằm ngoài tầm của các heuristic tham lam họ MaxRects trên một bộ dữ liệu trộn nhiều cỡ hình như thế này.

Con số tham chiếu "xếp tay đạt 97,6% ở 453 cm" trong đề bài không khớp với mô hình khoảng cách của app: 453 cm nhỏ hơn cả cận dưới 466,8 cm khi tính đủ gap 3 mm giữa mọi cặp hình, nên nhiều khả năng con số đó được đo với gap nhỏ hơn hoặc không tính gap.

**Đã thử những gì.** Quá trình tối ưu đi qua các mốc sau, mỗi mốc đều đo trên cùng bộ dữ liệu:

| Thay đổi | Chiều dài | Lấp đầy |
|---|---|---|
| MaxRects cơ bản, 4 heuristic × 2 thứ tự | 487,6 cm | 90,67% |
| Sửa mô hình gap ở biên (không phí nửa gap mỗi cạnh) | 485,2 cm | 91,12% |
| Thêm 4 thứ tự sắp xếp nữa (tổng 6) | 485,2 cm | 91,12% |
| **Duyệt phương án ép hướng theo từng loại hình (2ⁿ)** | 480,5 cm | 92,01% |
| **Thêm leo đồi tinh chỉnh có gieo hạt cố định** | **478,2 cm** | **92,45%** |

Các hướng đã thử nhưng **không** cải thiện: ép hướng "mềm" (vẫn cho packer xoay thêm), tăng số vòng leo đồi từ 400 lên 1.200, và dùng nhiều hạt gieo khác nhau. Kết quả 478,2 cm ổn định qua mọi biến thể này, cho thấy đây là cực trị địa phương vững của họ thuật toán hiện tại.

**Hướng để vượt qua**, nếu sau này cần: xếp theo **khối** (gom các bản giống nhau thành lưới k×m rồi mới xếp khối), hoặc mô hình hoá bằng quy hoạch nguyên rồi gọi solver. Cả hai đều là bước nhảy về độ phức tạp so với hiện tại.

**Đánh giá thực dụng:** con số có ý nghĩa với xưởng là **tiết kiệm 71,9% so với cách in rời đang làm**. Chênh lệch 0,55 điểm phần trăm lấp đầy tương đương khoảng 2,8 cm giấy trên một đơn 145 bản.

> Ngưỡng trong `NestingEngineTest.acceptanceDataset` được đặt ở **92%** — sát dưới con số đo được — để bắt ngay mọi thay đổi làm thuật toán xấu đi, chứ không phải để nới lỏng tiêu chí.

## 9. Tính tất định

Cùng đầu vào **luôn** cho cùng đầu ra. Điều này được kiểm tra bằng test `isDeterministic`, và được bảo đảm bởi:

- Mọi phép so sánh điểm trong packer đều có tie-break đầy đủ tới `(y, x, hướng xoay)`.
- Mọi phép sắp xếp đều kết thúc bằng tie-break theo `id` của hình.
- Bước leo đồi dùng `Random` với **hạt gieo cố định**, không dùng `Math.random()` hay thời gian hệ thống.

Vì sao quan trọng: thợ chạy lại cùng một đơn phải ra đúng file cũ, nếu không sẽ không biết bản in nào là bản đã duyệt.
