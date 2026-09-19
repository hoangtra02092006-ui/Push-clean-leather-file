# Backend

Java 17+ · Spring Boot 3.3 · Apache PDFBox 3 · Maven

## 1. Cây package

Tổ chức **package-by-feature**: mỗi package con chứa trọn bộ controller–service–model của một nghiệp vụ, không chia theo tầng kỹ thuật.

```
vn.printnest/
├─ PrintNestApplication.java      Điểm khởi động, bật @Async
│
├─ common/                        Thứ dùng chung, không thuộc nghiệp vụ nào
│  ├─ Units.java                  Quy đổi mm ↔ 1/100mm ↔ point. CHỈ CHỖ NÀY được đổi đơn vị
│  ├─ ErrorCode.java              Bộ mã lỗi + HTTP status tương ứng
│  ├─ ApiException.java           Ngoại lệ nghiệp vụ mang mã lỗi và thông điệp tiếng Việt
│  ├─ ApiErrorResponse.java       Cấu trúc { error: { code, message, details } }
│  ├─ GlobalExceptionHandler.java Biến mọi ngoại lệ thành một dạng JSON duy nhất
│  ├─ AppProperties.java          Đọc cấu hình tiền tố `app`
│  ├─ WebConfig.java              CORS
│  └─ AsyncConfig.java            Thread pool cho job nesting
│
├─ file/                          Nhận file và đọc kích thước vật lý
│  ├─ FileController.java         POST /files, GET /files/{id}/preview
│  ├─ FileService.java            Lưu đĩa, giữ metadata trong ConcurrentHashMap
│  ├─ FileMetadataReader.java     Đọc kích thước THẬT — xem mục 4
│  ├─ StoredFile.java             File đã lưu
│  ├─ FileResponse.java           DTO trả về
│  └─ FileType.java               PDF | IMAGE
│
├─ nesting/                       Nghiệp vụ trung tâm
│  ├─ NestingController.java      POST /nesting/jobs, GET /nesting/jobs/{id}
│  ├─ NestingService.java         Đổi DTO → đầu vào engine, kiểm tra đồng bộ, tạo job
│  ├─ NestingJobRunner.java       Bean RIÊNG chạy @Async — xem mục 3
│  ├─ engine/                     Thuật toán thuần, không phụ thuộc web
│  │  ├─ NestingEngine.java       Bộ điều phối: sinh & chọn phương án
│  │  ├─ MaxRectsPacker.java      Thuật toán chính
│  │  ├─ ShelfPacker.java         Thuật toán đối chứng
│  │  ├─ Heuristic.java           BSSF | BLSF | BAF | BL
│  │  ├─ Piece.java               Một bản in đã nở gap
│  │  ├─ PlacedPiece.java         Một bản in đã có vị trí
│  │  ├─ PackResult.java          Kết quả một lần xếp
│  │  ├─ EngineInput.java         Đầu vào thuần của thuật toán
│  │  └─ EngineItem.java          Một loại hình đã chốt kích thước
│  └─ model/                      DTO tầng API
│     ├─ NestRequest.java         Tham số + danh sách item (có @Valid)
│     ├─ NestItemRequest.java
│     ├─ Placement.java           Vị trí một hình, đơn vị mm
│     ├─ Sheet.java               Một tấm
│     ├─ NestStats.java           Số liệu tổng hợp
│     └─ NestResult.java
│
├─ export/                        Dựng file PDF thành phẩm
│  ├─ ExportController.java       GET .../sheets/{i}/pdf, GET .../export.zip
│  └─ PdfComposer.java            Nhúng file nguồn bằng PDFBox — xem mục 5
│
└─ job/                           Vòng đời job
   ├─ Job.java                    Bản ghi BẤT BIẾN, đổi trạng thái sinh bản mới
   ├─ JobStatus.java              PENDING | RUNNING | DONE | FAILED
   ├─ JobStore.java               Interface — để sau thay bằng DB
   └─ InMemoryJobStore.java       Hiện thực cho MVP
```

## 2. Quy ước

- **Tên bằng tiếng Anh**, chú thích và thông điệp cho người dùng bằng tiếng Việt.
- **`record` cho mọi DTO và model.** Chúng đều là dữ liệu bất biến; `record` cho sẵn `equals`/`hashCode` (cần cho test tính tất định) và loại bỏ setter.
- **Không ném ngoại lệ trần ra ngoài.** Mọi lỗi cho client đều là `ApiException` mang `ErrorCode`.
- **Engine không biết gì về Spring.** `EngineInput`/`EngineItem` là bản ghi thuần, nên unit test gọi thẳng `new NestingEngine().nest(...)` mà không cần dựng context.

## 3. Phân tầng

```
Controller  →  chỉ nhận/trả HTTP, không chứa logic
    │          (@Valid, mã HTTP, Content-Type, tên file tải về)
    ▼
Service     →  điều phối nghiệp vụ
    │          (đổi DTO, kiểm tra đầu vào, tạo job, tra cứu file)
    ▼
Engine      →  thuật toán thuần
               (không biết HTTP, không biết Spring, không đụng đĩa)
```

Lý do tách `NestingJobRunner` khỏi `NestingService`: Spring hiện thực `@Async` bằng **proxy**. Một bean tự gọi phương thức `@Async` của chính nó sẽ **không** đi qua proxy, nên chạy đồng bộ — lỗi kinh điển làm cả request HTTP treo vài giây. Gọi qua một bean khác thì lời gọi đi qua proxy và thực sự chạy nền.

## 4. Đọc kích thước vật lý (`FileMetadataReader`)

Đây là chỗ dễ sai nhất của cả luồng: một file "to" trên màn hình chưa chắc to khi in.

**Với PDF** — lấy hộp theo thứ tự ưu tiên:

1. **TrimBox** — kích thước thành phẩm sau khi xén. Đây mới là cái thợ cần.
2. **CropBox** — vùng hiển thị.
3. **MediaBox** — cả trang, thường còn cả vùng tràn lề.

Dùng MediaBox khi có TrimBox sẽ cho ra hình to hơn thực tế, và cả bố trí bị sai.

Ngoài ra trang PDF có thể mang cờ xoay 90°/270°; khi đó chiều rộng và chiều cao hiển thị bị hoán đổi, `readPdf()` xử lý riêng trường hợp này.

**Với ảnh** — đổi pixel sang milimet theo DPI ghi trong metadata (`HorizontalPixelSize` của ImageIO ghi kích thước một pixel bằng **milimet**, nên DPI = 25,4 ÷ giá trị đó). Không có DPI thì coi là **72 DPI** — quy ước của Illustrator/Photoshop khi xuất web.

Đọc không được thì trả `SIZE_UNREADABLE` và gợi ý người dùng nhập tay — ô kích thước ở bước 1 vốn đã cho sửa sẵn.

## 5. Xuất PDF (`PdfComposer`)

Nguyên tắc quan trọng nhất: **không raster hoá**.

| Loại nguồn | Cách nhúng |
|---|---|
| PDF | `LayerUtility.importPageAsForm()` → form XObject, giữ nguyên đường vector và chữ |
| Ảnh | `PDImageXObject`, đặt đúng kích thước vật lý đã tính |

Trang mới được tạo đúng `sheetWidthMm × sheetLengthMm`, quy đổi sang point bằng `mm × 72 ÷ 25,4`.

Phép biến đổi cho mỗi hình được ghép theo thứ tự:

```
translate(x, y)                    → dịch tới vị trí đặt
  → translate(w, 0) + rotate(90°)  → nếu hình bị xoay (xoay quanh góc trái-dưới
                                      rồi đẩy sang phải đúng một chiều rộng,
                                      để hình rơi vào đúng ô chứ không ra ngoài trang)
  → scale(sx, sy)                  → kéo về đúng kích thước đích
  → translate(-box.x, -box.y)      → đưa góc hộp nguồn về 0
```

Bước cuối quan trọng: nhiều file có góc hộp **không** nằm ở `(0,0)`; bỏ qua sẽ làm mọi hình lệch đi đúng bằng độ lệch đó.

Mỗi file nguồn chỉ được nhúng **một lần** rồi dùng lại (`formCache`, `imageCache`), dù xuất hiện 50 lần trên tấm. Không làm vậy thì file PDF phình to gấp nhiều lần và máy in xử lý ì ạch.

Khung cắt xám 0,25 pt được vẽ quanh mỗi hình, bật/tắt qua `drawCutLines` (mặc định bật).

## 6. Thuật toán nesting — từng bước

Bài toán là **strip packing**: bề rộng cố định, tối thiểu hoá chiều dài.

### Bước 1 — Nở hình theo gap

Mỗi hình được cộng thêm **trọn một** `gap` vào cả hai chiều. Khi ghi toạ độ thật, hình được neo vào **góc trái-dưới** của ô đã nở:

```
   ô đã nở (w+gap)          hai hình liền nhau:
  ┌──────────────┐          ┌────────┐ gap ┌────────┐
  │ ┌────────┐   │          │ hình A │◄───►│ hình B │
  │ │  hình  │   │  gap     └────────┘     └────────┘
  │ └────────┘   │          Khoảng hở đúng bằng gap, không hơn không kém,
  └──────────────┘          vì mọi hình đều dịch một lượng NHƯ NHAU.
```

Vùng khả dụng được **nới thêm đúng một gap** (`sheetWidth − 2×margin + gap`), vì hình sát biên không cần chừa gap ở phía ngoài. Không làm vậy sẽ phí một dải gap dọc theo hai cạnh khổ — trên bộ nghiệm thu, riêng việc này đáng 2,4 cm.

### Bước 2 — Chặn đầu vào sai

Hình nào có cạnh ngắn nhất (đã tính cả trường hợp xoay) lớn hơn bề rộng khả dụng thì trả `ITEM_WIDER_THAN_SHEET`, nêu rõ **hình nào** và kích thước bao nhiêu.

### Bước 3 — Sinh phương án chuẩn bị đầu vào

Ngoài phương án "tự do" (packer tự quyết định xoay từng hình), engine duyệt các phương án **ép hướng**: mỗi *loại* hình bị chốt một hướng duy nhất trước khi xếp. Với n loại xoay được, có 2ⁿ phương án (giới hạn n ≤ 8).

Đây là cải tiến có sức nặng nhất trên dữ liệu thật (+0,9 điểm phần trăm). Packer tham lam quyết định xoay theo *từng hình một* nên dễ phá vỡ cấu trúc lưới: một hình xoay lệch làm hỏng cả hàng. Khi chốt hướng cho cả loại, các bản giống nhau tự xếp thành cột đều tăm tắp, dải biên thừa gom lại thành một dải liên tục đủ rộng để nhồi loại hình khác vào — đúng cách thợ lành nghề xếp tay.

### Bước 4 — Chạy mọi tổ hợp

Mỗi phương án được chạy qua:

- **6 thứ tự sắp xếp**: diện tích ↓, cạnh dài ↓, cạnh ngắn ↓, chiều cao ↓, chiều rộng ↓, chu vi ↓.
- **5 packer**: MaxRects với 4 heuristic + ShelfPacker đối chứng.

| Heuristic | Ưu tiên ô trống có... |
|---|---|
| `BSSF` Best Short Side Fit | cạnh ngắn khít nhất |
| `BLSF` Best Long Side Fit | cạnh dài khít nhất |
| `BAF` Best Area Fit | diện tích thừa ít nhất |
| `BL` Bottom-Left | vị trí thấp nhất, rồi trái nhất |

Không heuristic nào thắng tuyệt đối trên mọi bộ dữ liệu, nên chạy hết rồi lấy kết quả tốt nhất.

### Bước 5 — Tìm nhị phân chiều dài

Với mỗi tổ hợp, engine **tìm nhị phân** chiều dài `L` nhỏ nhất mà tổ hợp đó vẫn xếp hết hình. Cận dưới là diện tích lý thuyết, cận trên lấy từ một lần xếp tự do.

Đây là điểm mấu chốt: **ép chiều dài xuống buộc packer phải nén chặt**. Một lần xếp không ràng buộc sẽ để lại nhiều khoảng hở mà thuật toán không có động lực lấp.

### Bước 6 — Nhồi hình nhỏ

Trong mỗi lần xếp, hình nào không nhét được **không làm dừng vòng lặp** — chúng được gom lại rồi thử lại theo thứ tự diện tích **tăng dần**, lặp cho tới khi không còn tiến triển.

Sau lượt xếp chính, các dải biên thừa thường chỉ còn vừa hình nhỏ nhất. Đây chính là chỗ tạo ra chênh lệch lớn so với xếp tay.

### Bước 7 — Leo đồi tinh chỉnh

Các heuristic tham lam bị kẹt ở cực trị địa phương. Engine lấy phương án tốt nhất **của từng heuristic** (4 điểm xuất phát khác nhau), xáo trộn nhẹ thứ tự đặt rồi xếp lại, giữ nếu ngắn hơn. 300 vòng × 3 hạt gieo cố định.

Bộ sinh số ngẫu nhiên được **gieo hạt cố định** nên hàm vẫn tất định.

### Bước 8 — Cắt thành nhiều tấm

Khi có `maxSheetLength`: đổ hình vào tấm hiện tại tới khi đầy (chọn phương án đặt được **nhiều hình nhất**), chốt tấm, mở tấm mới. Sau khi biết chính xác tập hình của một tấm, tấm đó được **xếp lại một lần nữa** để nén sát — nhờ vậy tấm cuối không bị kéo dài vô ích.

**Tổng số hình luôn được bảo toàn**, và engine kiểm tra lại điều này trước khi trả kết quả: lệch một hình là ném `NESTING_FAILED` chứ không im lặng trả ra bản thiếu.

### Độ chính xác

Toàn bộ tính bằng **số nguyên đơn vị 1/100 mm**. Không có phép cộng dồn số thực nào, nên không có sai số tích luỹ — điều thiết yếu khi cộng hàng trăm toạ độ lại với nhau.

**Tuyệt đối không tự co giãn hình để cho vừa khổ.** Thuật toán chỉ tịnh tiến và xoay 90°.

## 7. Chạy test

```bash
cd backend
mvn clean verify          # toàn bộ 15 test
mvn test -Dtest=NestingEngineTest      # chỉ thuật toán (10 test)
mvn test -Dtest=NestingApiIntegrationTest   # chỉ API (5 test)
```

### `NestingEngineTest` — bất biến của thuật toán

Mỗi test đều chạy lại toàn bộ bộ kiểm tra bất biến:

- Không có hai hình nào chồng lấn.
- Mọi hình nằm trọn trong khổ, đã trừ lề.
- Khoảng hở giữa hai hình bất kỳ ≥ `gap`.
- Tổng số hình đặt ra = tổng số lượng yêu cầu.
- Không tấm nào dài quá `maxSheetLength`.
- Hình có `allowRotate=false` giữ nguyên hướng.
- Cùng đầu vào cho cùng đầu ra.

### `NestingApiIntegrationTest` — trọn luồng qua HTTP

Upload PDF sinh tại chỗ → tạo job → poll tới `DONE` → tải PDF → **kiểm tra kích thước trang PDF đúng tới 0,1 mm**. Đây là chỗ dễ sai nhất vì phải đi qua ba hệ đơn vị.

## 8. Cấu hình

Xem `src/main/resources/application.yml`. Mọi giá trị đều đọc được từ biến môi trường — danh sách đầy đủ ở [README](../README.md#6-biến-môi-trường).
