# Hướng dẫn cho agent sửa PrintNest

File này là **hợp đồng làm việc** giữa người dùng và agent khi thay đổi hệ thống. Ba skill `/add`, `/fix`, `/adjust` đều nạp file này trước khi động vào code.

> Người dùng: bạn không cần đọc hết. Cứ gõ `/add`, `/fix` hoặc `/adjust` kèm mô tả bằng tiếng Việt.

---

## 1. Hệ thống gồm những gì

```
                    REST / JSON (đơn vị: MILIMET)
  frontend/  ◄──────────────────────────────────────►  backend/
  Vue 3 + TS                                           Spring Boot 3.3
  Netlify                                              Render (Docker)
      │                                                     │
      └─ src/api/mock.ts  ← BẢN SAO thuật toán              └─ nesting/engine/
         chạy trên trình duyệt cho chế độ demo                 MaxRects + ShelfPacker
```

Chi tiết từng tầng: [BACKEND.md](BACKEND.md) · [FRONTEND.md](FRONTEND.md) · [API.md](API.md) · [ARCHITECTURE.md](ARCHITECTURE.md) · [DESIGN-SYSTEM.md](DESIGN-SYSTEM.md)

---

## 2. Bản đồ: muốn đổi gì thì sửa ở đâu

| Muốn làm gì | Sửa ở đâu |
|---|---|
| Đổi cách xếp hình, thêm heuristic | `backend/.../nesting/engine/NestingEngine.java`, `MaxRectsPacker.java`, `ShelfPacker.java` |
| Thêm/đổi **tham số** ghép (khổ, gap, lề…) | `NestRequest.java` → `EngineInput.java` → `NestingService.toEngineInput()` → `types/index.ts` → `stores/nestingJob.ts` → `SettingsForm.vue` → `mock.ts` → `API.md` |
| Thêm/đổi **số liệu kết quả** | `NestStats.java` / `Sheet.java` / `Placement.java` → `NestingEngine.toResult()` → `types/index.ts` → `mock.ts` (hàm `buildResult`) → `ResultSummary.vue` → `API.md` |
| Đổi cách đọc kích thước file | `backend/.../file/FileMetadataReader.java` + `PdfContentBoxFinder.java` **và** `frontend/src/api/mock.ts` (`readDimensions`) |
| Đổi cách cắt khoảng trắng quanh hình | `PdfContentBoxFinder.java` → `FileMetadataReader.findContentBox()` → `PdfComposer.drawPdf()` (phải đi cùng nhau) |
| Đổi cách xếp lồng theo hình thật | `OccupancyMask.java` → `CavityFinder.java` → `NestingService.cavitiesFor()` → `MaxRectsPacker.addCavities()` |
| Đổi cách xuất PDF | `backend/.../export/PdfComposer.java` |
| Thêm endpoint mới | Controller trong package nghiệp vụ tương ứng → `frontend/src/api/*.ts` → `API.md` |
| Đổi giao diện một màn | `frontend/src/views/*.vue` |
| Đổi component dùng chung | `frontend/src/components/ui/App*.vue` + `DESIGN-SYSTEM.md` |
| Đổi màu / khoảng cách / cỡ chữ | **Chỉ** `frontend/src/assets/styles/tokens.css` + `DESIGN-SYSTEM.md` |
| Thêm chức năng mới vào Dashboard | `frontend/src/views/DashboardView.vue` (mảng `features`) + route trong `router/index.ts` |
| Đổi thông báo lỗi | `backend/.../common/ErrorCode.java` + chỗ ném `ApiException` + `API.md` |
| Đổi cấu hình runtime | `backend/src/main/resources/application.yml` + bảng biến môi trường trong `README.md` |

---

## 3. Bất biến — phá là hỏng sản phẩm

Những điều này **không bao giờ** được vi phạm. Nếu một yêu cầu buộc phải phá, **dừng lại và hỏi người dùng**.

1. **Không co giãn hình.** Thuật toán chỉ tịnh tiến và xoay 90°. Co hình cho vừa khổ là in ra sai kích thước, hỏng cả lô hàng.
2. **Bảo toàn số lượng.** Tổng hình đặt ra = tổng số lượng yêu cầu. Không thiếu, không thừa.
3. **Không chồng lấn, không vượt khổ, đủ khoảng hở** `gap` giữa mọi cặp hình. Ở chế độ `FREE`, khung bao được phép lồng nhau nhưng **nét vẽ thật thì tuyệt đối không** — mọi ô trống trả lại cho packer phải đã cách nét vẽ ít nhất một `gap`.
4. **Tất định.** Cùng đầu vào → cùng đầu ra. Mọi `Random` phải gieo hạt cố định; mọi `sort` phải có tie-break tới `id`.
5. **Không raster hoá PDF.** File nguồn PDF nhúng bằng `LayerUtility.importPageAsForm`, giữ nguyên vector.
6. **Số nguyên 1/100 mm** trong engine. Không cộng dồn số thực.
7. **Đơn vị:** API luôn **mm**, giao diện luôn **cm**. Quy đổi chỉ được nằm ở `Units.java` và `src/api/units.ts`.
8. **Không màn hình trắng.** Mọi trạng thái rỗng/đang tải/lỗi đều phải có `AppEmptyState` hoặc `AppSpinner`.
9. **Mỗi màn chỉ một nút `primary`.**
10. **Chỉ dùng token CSS.** Cấm hard-code mã màu và số pixel trong component.

---

## 4. Quy tắc vàng: sửa backend thì phải sửa `mock.ts`

`frontend/src/api/mock.ts` là **bản sao rút gọn** của thuật toán backend, chạy trên trình duyệt cho chế độ demo. Nó **phải trả về đúng schema** như backend.

> Đổi `NestStats`, `Sheet`, `Placement` hay `NestRequest` mà quên `mock.ts` → bản demo trên Netlify **hỏng âm thầm**: không có lỗi biên dịch, không có test đỏ, chỉ là giao diện hiện `undefined` hoặc `NaN`.

Sau mỗi lần đổi schema, chạy lại kịch bản demo ở mục 6.

---

## 5. Bẫy đã gặp thật — đừng giẫm lại

| Bẫy | Hậu quả | Cách tránh |
|---|---|---|
| `@Async` gọi từ chính bean đó | Chạy đồng bộ, treo request HTTP vài giây | Giữ `NestingJobRunner` là bean **riêng**, không gộp vào `NestingService` |
| Khai biến trong `[build.environment]` của `netlify.toml` | Đè lên biến đặt trong Netlify UI, người dùng đặt bao nhiêu lần cũng vô hiệu | Không khai biến runtime trong `netlify.toml` |
| File nhị phân không đánh dấu trong `.gitattributes` | PDF/PNG bị đổi LF→CRLF khi clone trên Windows → hỏng file | Mọi đuôi nhị phân mới phải thêm vào `.gitattributes` |
| Sửa `tokens.css` nhưng quên `DESIGN-SYSTEM.md` | Tài liệu nói một đằng, code một nẻo | Sửa cặp đôi |
| Thêm trường vào response mà không cần | Job bị hỏi lại mỗi 700 ms → phí băng thông | Trường chỉ dùng phía server thì đánh `@JsonIgnore` |
| Đổi `.env` của Vite mà không khởi động lại | Không có tác dụng gì | Vite chỉ đọc biến lúc khởi động |
| Dùng `v-model` trên `<input type="number">` rồi xử lý giá trị như chuỗi | Vue tự ép sang `number` trước khi trao cho v-model → `raw.trim()` ném TypeError mỗi lần gõ, ô nhập **âm thầm** không đổi được giá trị | Dùng `type="text"` + `inputmode="decimal"`, tự đọc chuỗi thô từ DOM (`AppNumberInput`) |
| Prune `freeRects` kiểu O(n²) mỗi lần đặt hình | Engine chậm gấp hàng chục lần | Chỉ đối chiếu các mảnh **mới sinh**, xem `mergeGenerated()` |
| Trả ảnh xem trước ở độ phân giải gốc | JPEG 3 MB thành PNG 32 MB cho một ô 44 px — người dùng tưởng "upload chậm" | Thu nhỏ về 360 px và giữ lại trong bộ nhớ; ảnh lớn thì giải nén subsampling |
| Tạo file `.vue`/`.md` bằng heredoc của Bash | Dấu tiếng Việt rụng sạch, cả màn hình thành tiếng Việt không dấu mà build vẫn xanh nên không ai phát hiện | Ghi file có dấu bằng công cụ Write hoặc `fs.writeFileSync` (UTF-8), đừng đẩy qua heredoc |
| Thay chuỗi hàng loạt bằng bảng có khoá ngắn (`Loi`, `goc `, `Ket qua`) | Khoá ngắn trúng cả vào chú thích, sinh ra câu nửa có dấu nửa không | Sắp bảng theo khoá **dài trước**, rồi soát lại các dòng chú thích lẫn dấu |
| Ô dữ liệu đánh `col-num` nhưng tiêu đề cột để mặc định | `th` căn trái, `td` căn phải → cột số nào cũng lệch, nhìn như bảng vỡ | Khai `align` cho cột ngay trong mảng `columns` của `AppTable` |
| Nhãn đổi độ dài theo trạng thái (`Có` ↔ `Không`) nằm trong ô bảng | Bật/tắt một cái là cả bảng bị đẩy ngang | Chốt `min-width` cho nhãn đủ chứa chuỗi dài nhất |
| Ảnh xem trước vẽ cả trang trong khi bảng ghi kích thước đã cắt trắng | Thợ nhìn ảnh lệch hẳn số đo, tưởng app đọc sai khổ file | Đặt lại CropBox của trang bằng `contentBox` rồi để PDFBox vẽ — đừng tự cắt bitmap (sẽ phải tự xử lý `/Rotate`) |
| Kích thước báo ra ngoài lấy từ hộp A, `PdfComposer` lại vẽ theo hộp B | Hình bị co lại hoặc lệch đúng bằng phần chênh — in ra sai kích thước | Cả hai phải dùng **cùng** `StoredFile.contentBox`, không dùng `form.getBBox()` |
| Tìm vùng có hình bằng cách quét pixel không trắng | Hình tô màu trắng bị coi là khoảng trống rồi cắt mất | Đọc content stream (`PdfContentBoxFinder`), không quét ảnh |
| Quên trừ diện tích hốc lõm khỏi cận dưới tìm nhị phân | Cận dưới cao hơn lời giải tối ưu → tự chặn mất đúng cái lợi vừa tạo ra | Dùng `Piece.netArea()` chứ không phải `area()` |
| Tính tỷ lệ lấp đầy bằng **tổng** diện tích khung bao | Ở chế độ xếp lồng, khung bao được phép lồng nhau → phần giấy chung bị đếm hai lần → lấp đầy 100,3% và "giấy bỏ đi" âm | Lấy **hợp** bằng `Coverage.of()`; chế độ lưới không chồng nhau nên hợp = tổng, số cũ không xê dịch |
| Dùng chung một con số giới hạn cho cả packer chữ nhật lẫn xếp lồng | Hai lối xếp đo chiều dài theo hai quy ước khác nhau: packer chữ nhật cắt bớt một gap ở trên cùng, xếp lồng thì không → tấm dài hơn giới hạn đúng bằng một gap, đặt 200 cm với khoảng cách 5 cm thì ra 205 cm | Giữ `maxContentLengthCmm` (chiều dài thật) tách khỏi `maxSheetLengthCmm` (đã cộng gap); chiều rộng vốn đã làm đúng nên cứ soi theo đó |
| Nới thêm vài hàng "cho chắc" vào khung xếp (`budget + 2`) | Mỗi hàng thừa là một milimét tràn qua giới hạn thợ đã đặt | Có giới hạn thì lấy đúng số hàng cho phép, không cộng thêm |
| Xoay hốc lõm theo chiều cao ĐÃ nở gap | Hốc lệch đi đúng một gap, hình chui vào bị chạm nét vẽ | Xoay theo `realH`, khớp với `PdfComposer` |

---

## 6. Lệnh kiểm chứng bắt buộc

Agent **phải** chạy và **phải dán kết quả thật** vào báo cáo. Không được nói "đã xong" khi chưa chạy.

### Backend (khi đụng vào `backend/`)

```bash
cd backend
./mvnw clean verify          # Windows: .\mvnw.cmd clean verify
```

Yêu cầu: **26/26 test pass**. Trong log phải thấy dòng nghiệm thu:

```
[NGHIEM THU] chieu dai = 478.2 cm | lap day = 92.45% | tiet kiem = 71.9% | so tam = 1
```

> Con số này **không được xấu đi**. Nếu chiều dài tăng hoặc lấp đầy giảm, đó là hồi quy thuật toán — phải báo rõ, không được lặng lẽ hạ ngưỡng test.

### Frontend (khi đụng vào `frontend/`)

```bash
cd frontend
npm run type-check    # phải sạch, không cảnh báo
npm run test          # kiểm phần đọc số từ ô nhập
npm run build         # phải thành công
```

### Chế độ demo (khi đổi schema hoặc `mock.ts`)

```bash
cd frontend
npx esbuild src/api/mock.ts --bundle --format=esm --platform=node --outfile=/tmp/mock.mjs
```

Rồi chạy bộ nghiệm thu qua `mockCreateJob` và kiểm: đủ **145 hình**, không chồng lấn, không vượt khổ.

### Chạy thật (khi đổi luồng nghiệp vụ)

```bash
# Terminal 1
cd backend && ./mvnw spring-boot:run
# Terminal 2
cd frontend && npm run dev
```

Thử lại các kịch bản trong [`samples/README.md`](../samples/README.md) — có sẵn file mẫu và **kết quả kỳ vọng đã đo thật**.

---

## 7. Quy ước code

- **Tên biến, hàm, class, commit message: tiếng Anh.** Chú thích, thông điệp cho người dùng, tài liệu: **tiếng Việt không dấu trong code Java** (tránh lỗi encoding), **có dấu trong file `.md` và `.vue`**.
- **Java:** `record` cho mọi DTO/model. Không ném ngoại lệ trần — dùng `ApiException` kèm `ErrorCode`.
- **Vue:** `<script setup>` + TypeScript. Component dùng chung đặt tên `App*` và nằm trong `components/ui/`.
- **Chú thích giải thích *tại sao*, không phải *cái gì*.** Code đã nói nó làm gì rồi.
- **Commit theo Conventional Commits**, chia thành commit có nghĩa, không dồn một cục.

---

## 8. Thế nào là "xong"

Một thay đổi chỉ được coi là hoàn thành khi **đủ cả 6**:

- [ ] Code chạy được, không còn `TODO` trong luồng chính
- [ ] Lệnh kiểm chứng ở mục 6 đã chạy, kết quả thật được dán vào báo cáo
- [ ] Số liệu nghiệm thu không xấu đi
- [ ] `mock.ts` đã đồng bộ (nếu đụng schema)
- [ ] Tài liệu liên quan đã cập nhật (xem cột phải mục 2)
- [ ] Báo cáo nêu rõ: đã sửa gì, đã kiểm thế nào, **và những gì chưa làm được**

Nếu có phần không làm được, **nói thẳng** — đừng im lặng thu hẹp phạm vi.
