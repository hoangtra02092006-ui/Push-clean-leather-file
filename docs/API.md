# Đặc tả API

Tiền tố: `/api/v1`

**Đơn vị:** toàn bộ số đo trao đổi giữa frontend và backend là **milimet** (số thực, tối đa 2 chữ số thập phân). Frontend chịu trách nhiệm quy đổi hiển thị sang centimet.

---

## 1. `POST /api/v1/files` — Tải file lên

Nhận một hoặc nhiều file PDF / PNG / JPG, lưu lại và đọc kích thước vật lý.

**Request:** `multipart/form-data`, trường `files` (lặp lại cho nhiều file).

```bash
curl -X POST http://localhost:8080/api/v1/files \
  -F "files=@nhan-ao.pdf" \
  -F "files=@logo.png"
```

**Response `200 OK`:**

```json
[
  {
    "id": "4f2c8a91-6d3e-4b17-9a52-1e8f0c7d4b33",
    "originalName": "nhan-ao.pdf",
    "widthMm": 200.0,
    "heightMm": 180.0,
    "sourceWidthMm": 200.0,
    "sourceHeightMm": 180.0,
    "trimmed": false,
    "type": "PDF",
    "pageCount": 1,
    "previewUrl": "/api/v1/files/4f2c8a91-6d3e-4b17-9a52-1e8f0c7d4b33/preview"
  },
  {
    "id": "8b1d5e77-2a94-4c60-b3f1-9d6e2a5c8017",
    "originalName": "logo.png",
    "widthMm": 52.92,
    "heightMm": 52.92,
    "sourceWidthMm": 52.92,
    "sourceHeightMm": 52.92,
    "trimmed": false,
    "type": "IMAGE",
    "pageCount": 1,
    "previewUrl": "/api/v1/files/8b1d5e77-2a94-4c60-b3f1-9d6e2a5c8017/preview"
  }
]
```

> `widthMm`/`heightMm` là kích thước **vật lý khi in**, không phải pixel.
>
> Với **PDF**, đây là kích thước của **vùng có nét vẽ thật**, đã bỏ khoảng trắng bao quanh.
> `sourceWidthMm`/`sourceHeightMm` là khổ trang nguyên bản, và `trimmed` cho biết có cắt
> được gì không. Ví dụ một hình 3×3 cm nằm giữa trang A4 sẽ trả về `widthMm: 30`,
> `sourceWidthMm: 210`, `trimmed: true`. Tắt bằng `APP_TRIM_ENABLED=false`.
>
> Với **ảnh**, không cắt gì cả — xem [BACKEND.md](BACKEND.md#4-đọc-kích-thước-vật-lý-filemetadatareader).

**Lỗi có thể gặp:** `UNSUPPORTED_FORMAT`, `FILE_TOO_LARGE`, `SIZE_UNREADABLE`.

---

## 2. `GET /api/v1/files/{id}/preview` — Ảnh xem trước

Trả về ảnh **đã cắt khoảng trắng và thu nhỏ về tối đa 360 px cạnh dài**, được giữ lại trong bộ nhớ nên lần hỏi sau trả về gần như tức thì.

Kiểu MIME **không cố định**, tuỳ loại file nguồn:

| File nguồn | Trả về | Vì sao |
|---|---|---|
| PDF | `image/png` | Nét vẽ chỉ vài màu → PNG ra vài KB và sắc cạnh |
| PNG / JPG | `image/jpeg` (chất lượng 0,82) | Ảnh chụp mã PNG phình tới ~258 KB, JPEG chỉ còn ~1/10 mà mắt thường không phân biệt |

Với PDF, ảnh được cắt đúng bằng vùng có nét vẽ (`contentBox`) nên **khớp với `widthMm`/`heightMm` trả về ở mục 1**. Cách cắt là đặt lại CropBox của trang rồi để PDFBox vẽ, nhờ vậy `/Rotate` của trang được xử lý một lần duy nhất và ảnh xem trước không bao giờ lệch so với file xuất ra. Ảnh bitmap không bị cắt vì viền trắng của ảnh có thể là cố ý.

Ảnh này chỉ phục vụ hiển thị — việc xuất PDF luôn dùng file gốc nên không ảnh hưởng chất lượng in. Với ảnh lớn, backend giải nén ở độ phân giải thấp ngay từ đầu (subsampling) thay vì đọc trọn ảnh rồi mới thu nhỏ.

**Lỗi:** `FILE_NOT_FOUND`.

---

## 3. `POST /api/v1/nesting/jobs` — Tạo lần ghép

**Request:** `application/json`

```json
{
  "mode": "ORTHOGONAL",
  "sheetWidthMm": 570,
  "marginMm": 5,
  "gapMm": 3,
  "maxSheetLengthMm": 2000,
  "allowRotateGlobal": true,
  "drawCutLines": true,
  "items": [
    {
      "fileId": "4f2c8a91-6d3e-4b17-9a52-1e8f0c7d4b33",
      "quantity": 50,
      "allowRotate": true,
      "widthMm": 200,
      "heightMm": 180
    },
    {
      "fileId": "8b1d5e77-2a94-4c60-b3f1-9d6e2a5c8017",
      "quantity": 15,
      "allowRotate": false
    }
  ]
}
```

| Trường | Bắt buộc | Ý nghĩa |
|---|---|---|
| `mode` | | `ORTHOGONAL` (mặc định) = chỉ xoay 0°/90°, mỗi hình chiếm trọn khung chữ nhật. `FREE` = cho phép hình nhỏ lồng vào phần trống bên trong khung của hình lớn |
| `sheetWidthMm` | ✔ | Khổ ngang cuộn, phải > 0 |
| `marginMm` | ✔ | Lề biên mỗi phía, ≥ 0 |
| `gapMm` | ✔ | Khoảng hở tối thiểu giữa hai hình bất kỳ, ≥ 0 |
| `maxSheetLengthMm` | | Giới hạn chiều dài mỗi file. **`null` = không giới hạn** |
| `allowRotateGlobal` | ✔ | Tắt sẽ ghi đè xuống mọi item |
| `drawCutLines` | | Vẽ khung cắt xám quanh mỗi hình. Mặc định `true` |
| `items[].widthMm` / `heightMm` | | Ghi đè kích thước đọc từ file. Bỏ trống thì dùng metadata |

**Response `202 Accepted`:**

```json
{
  "jobId": "a7e3f019-4c82-4d5b-b16a-e93c7f2d8054",
  "status": "PENDING",
  "progress": 0,
  "createdAt": "2026-09-19T13:42:07.881Z"
}
```

Trả về ngay; việc tính chạy ở luồng nền. Dùng endpoint dưới để theo dõi.

**Lỗi:** `ITEM_WIDER_THAN_SHEET`, `FILE_NOT_FOUND`, `INVALID_REQUEST`.

---

## 4. `GET /api/v1/nesting/jobs/{jobId}` — Trạng thái và kết quả

**Đang chạy — `200 OK`:**

```json
{
  "jobId": "a7e3f019-4c82-4d5b-b16a-e93c7f2d8054",
  "status": "RUNNING",
  "progress": 10,
  "createdAt": "2026-09-19T13:42:07.881Z"
}
```

**Xong — `200 OK`:**

```json
{
  "jobId": "a7e3f019-4c82-4d5b-b16a-e93c7f2d8054",
  "status": "DONE",
  "progress": 100,
  "createdAt": "2026-09-19T13:42:07.881Z",
  "finishedAt": "2026-09-19T13:42:13.024Z",
  "result": {
    "sheets": [
      {
        "index": 0,
        "widthMm": 570.0,
        "lengthMm": 4782.0,
        "fillRate": 0.9245,
        "placements": [
          {
            "fileId": "4f2c8a91-6d3e-4b17-9a52-1e8f0c7d4b33",
            "label": "nhan-ao.pdf",
            "categoryIndex": 0,
            "xMm": 5.0,
            "yMm": 5.0,
            "wMm": 200.0,
            "hMm": 180.0,
            "rotated": false
          },
          {
            "fileId": "8b1d5e77-2a94-4c60-b3f1-9d6e2a5c8017",
            "label": "logo.png",
            "categoryIndex": 1,
            "xMm": 208.0,
            "yMm": 5.0,
            "wMm": 52.92,
            "hMm": 52.92,
            "rotated": true
          }
        ]
      }
    ],
    "stats": {
      "totalSheets": 1,
      "totalLengthMm": 4782.0,
      "totalShapeAreaMm2": 2520000.0,
      "usedAreaMm2": 2725740.0,
      "fillRate": 0.9245,
      "savedVsIndividualPct": 71.9,
      "totalPieces": 145,
      "byType": [
        {
          "fileId": "4f2c8a91-6d3e-4b17-9a52-1e8f0c7d4b33",
          "label": "the-25x14.9.pdf",
          "categoryIndex": 0,
          "pieces": 15,
          "widthMm": 250.0,
          "heightMm": 149.0,
          "shapeAreaMm2": 558750.0,
          "lengthMm": 3835.2
        }
      ]
    }
  }
}
```

**Thất bại — `200 OK`** (job tồn tại, nhưng chạy hỏng):

```json
{
  "jobId": "a7e3f019-4c82-4d5b-b16a-e93c7f2d8054",
  "status": "FAILED",
  "progress": 0,
  "error": {
    "code": "ITEM_WIDER_THAN_SHEET",
    "message": "Hinh \"bang-ron.pdf\" 60.0x20.0 cm rong hon kho 57.0 cm (da tinh ca truong hop xoay).",
    "details": {
      "fileId": "c3a9...",
      "label": "bang-ron.pdf"
    }
  }
}
```

### Ghi chú về hệ toạ độ

`xMm`/`yMm` là góc **TRÁI-DƯỚI** của hình, gốc toạ độ ở góc trái-dưới của tấm (quy ước PDF). Frontend vẽ bằng SVG (gốc trái-**trên**) nên phải lật trục Y một lần:

```
ySvg = sheet.lengthMm − placement.yMm − placement.hMm
```

`wMm`/`hMm` là kích thước **sau khi đã xoay** — không cần tự hoán đổi khi `rotated = true`.

`categoryIndex` là thứ tự loại hình (0, 1, 2…), dùng để tô màu preview nhất quán giữa các lần chạy.

### `stats.byType` — số liệu tách theo từng loại hình

Một dòng cho mỗi loại hình đã tải lên, **sắp theo `categoryIndex` tăng dần** (tất định — cùng đầu vào thì cùng thứ tự dòng).

| Trường | Ý nghĩa |
|---|---|
| `pieces` | Số bản in của loại này đã đặt được |
| `widthMm` / `heightMm` | Kích thước một bản, đo **trước khi xoay** — không đổi theo việc packer có xoay hay không |
| `shapeAreaMm2` | Tổng diện tích các bản của loại này |
| `lengthMm` | **Chiều dài cuộn mẫu này ăn**, đã gồm cả phần giấy bỏ đi chia đều. Cộng hết các loại lại **bằng `stats.totalLengthMm`** |

`lengthMm` là con số chủ xưởng dùng để chia tiền giấy, vì giấy tính tiền theo **mét dài** chứ không theo mét vuông:

```
lengthMm = tổng chiều dài × (diện tích giấy mẫu này chiếm / tổng diện tích bị phủ)
```

Viết theo cách quen thuộc hơn thì tương đương:

```
lengthMm = (diện tích giấy mẫu này chiếm / diện tích giấy đã dùng) ÷ tỷ lệ lấp đầy × tổng chiều dài
```

Chia cho tỷ lệ lấp đầy chính là để **tính cả phần giấy bỏ đi vào đầu mỗi mẫu** — công bằng, vì không mẫu nào một mình gây ra chỗ trống. Bản rút gọn ở trên cho cùng kết quả mà cộng lại đúng tổng, không dư một sai số làm tròn nào.

> **"Diện tích giấy mẫu này chiếm" đếm mỗi chỗ đúng MỘT lần.** Ở chế độ `FREE` và `TRUE_SHAPE`, khung bao của hai hình được phép lồng vào nhau; chỗ nào hai khung bao cùng trùm thì tính cho hình đứng trước trong danh sách. Hình nhỏ chui gọn vào góc trống của hình lớn **không tốn thêm mét giấy nào** nên chiều dài của nó gần bằng 0 — đúng như thực tế.

> **Lưu ý về `fillRate` ở chế độ `FREE`.** Tỷ lệ lấp đầy được tính trên diện tích **khung bao**. Ở chế độ `FREE` các khung bao được phép lồng nhau, nên phần lồng bị đếm hai lần và `fillRate` cao hơn thực tế. Con số đáng tin để so sánh hai chế độ là **`totalLengthMm`** — đó cũng là thứ xưởng trả tiền.

**Lỗi:** `JOB_NOT_FOUND`.

---

## 5. `GET .../sheets/{index}/pdf` và `.../sheets/{index}/tif` — Tải một tấm

Hai định dạng, **cùng một bố trí**: cả hai đều dựng lại từ `PdfComposer` nên không thể lệch nhau.

| Đuôi | Trả về | Dùng khi |
|---|---|---|
| `/pdf` | `application/pdf` | Giữ nguyên vector, nét sắc ở mọi mức phóng |
| `/tif` | `image/tiff` (nén LZW, RGB) | RIP chỉ nhận ảnh bitmap |

Tên file có sẵn kích thước để thợ không phải mở ra kiểm tra:

```
minh-tri-a7e3f019-tam01-57x100cm.pdf
minh-tri-a7e3f019-tam01-57x100cm.tif
```

`index` bắt đầu từ **0**.

### Về bản TIF

TIF là ảnh bitmap nên phải chốt trước độ phân giải, đặt bằng `APP_TIFF_DPI` (mặc định 150). **Gấp đôi DPI là gấp bốn lần bộ nhớ** — một tấm 57 × 100 cm:

| DPI | Kích thước | Bộ nhớ một ảnh |
|---:|---|---:|
| 150 | 3.366 × 5.906 | 80 MB |
| 300 | 6.732 × 11.811 | 318 MB |
| 600 | 13.465 × 23.622 | 1,3 GB |

Vì vậy có **trần cứng** `APP_TIFF_MAX_MEGAPIXELS` (mặc định 80 triệu điểm). Vượt trần thì trả `TIFF_TOO_LARGE` kèm hướng xử lý, thay vì để máy chủ hết bộ nhớ rồi tự khởi động lại. **Bản PDF không vướng giới hạn này.**

Thông số của file xuất ra, đọc thẳng từ tag trong file:

| Tag | Giá trị | Vì sao |
|---|---|---|
| `Compression` | LZW | Không mất dữ liệu. Bản in không được phép có nhiễu quanh nét vẽ |
| `PhotometricInterpretation` | RGB, 8 bit mỗi kênh | Giữ nguyên màu như bản PDF, không có kênh trong suốt |
| `XResolution` / `YResolution` | = `APP_TIFF_DPI` | **Thiếu là file nguy hiểm**: phần mềm sẽ đoán 72 DPI và tấm 57 cm in ra thành 2,4 mét |
| `ResolutionUnit` | 2 (inch) | Đi kèm hai tag trên mới có nghĩa |
| `RowsPerStrip` | 64 | Mặc định của Java là **1** — ảnh 4.836 hàng thành 4.836 dải, phình bảng tag và làm LZW khởi động lại từ điển mỗi hàng. Đặt 64 làm file nhẹ đi gần 5 lần |
| `ICCProfile` | sRGB (6.876 byte) | Không có thì file chỉ nói "đây là RGB" mà không nói RGB **nào**, RIP phải đoán. Đoán sai thì màu lệch mà không chỗ nào báo lỗi |

Ghi metadata hỏng thì **từ chối phát hành file** chứ không phát hành một file sẽ in sai kích thước.

**Lỗi:** `JOB_NOT_FOUND`, `JOB_NOT_READY`, `TIFF_TOO_LARGE`.

---

## 6. `GET /api/v1/nesting/jobs/{jobId}/export.zip` — Tải tất cả

Trả về file `.zip` chứa toàn bộ các tấm, tên file bên trong giống mục 5.

| Tham số | Mặc định | Giá trị |
|---|---|---|
| `format` | `pdf` | `pdf` hoặc `tif` |

```
GET .../export.zip            -> minh-tri-a7e3f019-pdf.zip
GET .../export.zip?format=tif -> minh-tri-a7e3f019-tif.zip
```

Định dạng lạ bị **báo lỗi ngay** chứ không lặng lẽ trả về PDF — trả nhầm định dạng thì thợ chỉ phát hiện khi file đã ở trên máy in.

**Lỗi:** `JOB_NOT_FOUND`, `JOB_NOT_READY`, `INVALID_REQUEST`, `TIFF_TOO_LARGE`.

---

## 7. Bảng mã lỗi

Mọi lỗi đều trả về cùng một cấu trúc:

```json
{
  "error": {
    "code": "MA_LOI",
    "message": "Thông điệp tiếng Việt hiển thị thẳng cho người dùng.",
    "details": { }
  }
}
```

| Mã | HTTP | Khi nào xảy ra |
|---|---|---|
| `FILE_TOO_LARGE` | 413 | File vượt `app.storage.max-file-size` (mặc định 50 MB) |
| `UNSUPPORTED_FORMAT` | 415 | Đuôi file không phải `.pdf`, `.png`, `.jpg`, `.jpeg` |
| `SIZE_UNREADABLE` | 422 | Không đọc được kích thước vật lý; gợi ý nhập tay |
| `ITEM_WIDER_THAN_SHEET` | 422 | Có hình rộng hơn khổ, kể cả sau khi xoay. `details` nêu rõ hình nào |
| `INVALID_REQUEST` | 400 | Tham số không qua được `@Valid`, hoặc lề lớn hơn khổ, hoặc giới hạn dài nhỏ hơn hình cao nhất |
| `JOB_NOT_FOUND` | 404 | Không có job với id đó — thường do máy chủ đã khởi động lại |
| `FILE_NOT_FOUND` | 404 | Không có file với id đó |
| `JOB_NOT_READY` | 409 | Job chưa chạy xong nên chưa có gì để tải |
| `NESTING_FAILED` | 500 | Thuật toán không hội tụ hoặc số lượng bị lệch |
| `TIFF_TOO_LARGE` | 422 | Tấm quá lớn để dựng TIF ở độ phân giải đang đặt. Giảm `APP_TIFF_DPI` hoặc đặt chiều dài tối đa mỗi file ngắn lại |
| `INTERNAL_ERROR` | 500 | Lỗi không lường trước. Stack trace chỉ ghi vào log, không lộ ra ngoài |

Frontend phân nhánh theo `error.code`, **không** parse chuỗi `message`.
