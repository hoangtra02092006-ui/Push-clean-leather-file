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
          "shareOfShapes": 0.9214,
          "fillRate": 0.8015
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
| `shareOfShapes` | Tỷ lệ 0..1 trên **tổng diện tích hình**. Cộng hết các loại lại **bằng 1** |
| `fillRate` | Tỷ lệ 0..1 trên **diện tích giấy đã dùng**. Cộng hết các loại lại **bằng `stats.fillRate`**; phần còn thiếu chính là giấy bỏ đi |

Hai tỷ lệ này nhìn cùng một thứ từ hai phía và **không thay thế cho nhau**: `shareOfShapes` trả lời "trong số hình đem in, mẫu này chiếm bao nhiêu"; `fillRate` trả lời "mẫu này ngốn bao nhiêu phần giấy".

> **Mọi `fillRate` đều đếm mỗi chỗ trên giấy đúng MỘT lần.** Ở chế độ `FREE` và `TRUE_SHAPE`, khung bao của hai hình được phép lồng vào nhau, nên cộng tổng khung bao sẽ ra tỷ lệ vượt 100%. Chỗ nào hai khung bao cùng trùm thì tính cho hình đứng trước trong danh sách — nói cách khác `fillRate` của một mẫu là **phần giấy nó chiếm chỗ riêng**, còn hình nhỏ chui gọn vào góc trống của hình lớn thì không tốn thêm giấy nào nên không được tính.

> **Lưu ý về `fillRate` ở chế độ `FREE`.** Tỷ lệ lấp đầy được tính trên diện tích **khung bao**. Ở chế độ `FREE` các khung bao được phép lồng nhau, nên phần lồng bị đếm hai lần và `fillRate` cao hơn thực tế. Con số đáng tin để so sánh hai chế độ là **`totalLengthMm`** — đó cũng là thứ xưởng trả tiền.

**Lỗi:** `JOB_NOT_FOUND`.

---

## 5. `GET /api/v1/nesting/jobs/{jobId}/sheets/{index}/pdf` — Tải một tấm

Trả về `application/pdf` kèm `Content-Disposition: attachment`.

Tên file có sẵn kích thước để thợ không phải mở ra kiểm tra:

```
minh-tri-a7e3f019-tam01-57x478cm.pdf
```

`index` bắt đầu từ **0**.

**Lỗi:** `JOB_NOT_FOUND`, `JOB_NOT_READY`.

---

## 6. `GET /api/v1/nesting/jobs/{jobId}/export.zip` — Tải tất cả

Trả về file `.zip` chứa toàn bộ các tấm, tên file bên trong giống mục 5.

**Lỗi:** `JOB_NOT_FOUND`, `JOB_NOT_READY`.

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
| `INTERNAL_ERROR` | 500 | Lỗi không lường trước. Stack trace chỉ ghi vào log, không lộ ra ngoài |

Frontend phân nhánh theo `error.code`, **không** parse chuỗi `message`.
