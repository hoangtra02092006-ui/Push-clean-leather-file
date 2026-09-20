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
| Đổi cách đọc kích thước file | `backend/.../file/FileMetadataReader.java` **và** `frontend/src/api/mock.ts` (`readDimensions`) |
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
3. **Không chồng lấn, không vượt khổ, đủ khoảng hở** `gap` giữa mọi cặp hình.
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
| Prune `freeRects` kiểu O(n²) mỗi lần đặt hình | Engine chậm gấp hàng chục lần | Chỉ đối chiếu các mảnh **mới sinh**, xem `mergeGenerated()` |

---

## 6. Lệnh kiểm chứng bắt buộc

Agent **phải** chạy và **phải dán kết quả thật** vào báo cáo. Không được nói "đã xong" khi chưa chạy.

### Backend (khi đụng vào `backend/`)

```bash
cd backend
./mvnw clean verify          # Windows: .\mvnw.cmd clean verify
```

Yêu cầu: **15/15 test pass**. Trong log phải thấy dòng nghiệm thu:

```
[NGHIEM THU] chieu dai = 478.2 cm | lap day = 92.45% | tiet kiem = 71.9% | so tam = 1
```

> Con số này **không được xấu đi**. Nếu chiều dài tăng hoặc lấp đầy giảm, đó là hồi quy thuật toán — phải báo rõ, không được lặng lẽ hạ ngưỡng test.

### Frontend (khi đụng vào `frontend/`)

```bash
cd frontend
npm run type-check    # phải sạch, không cảnh báo
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
