# Frontend

Vue 3 (`<script setup>`, Composition API) · TypeScript · Vite · Pinia · Vue Router · Axios

## 1. Cây thư mục

```
frontend/src/
├─ main.ts                    Khởi tạo app, nạp 3 file CSS theo đúng thứ tự
├─ App.vue                    Chỉ bọc <RouterView> trong <AppShell>
│
├─ router/index.ts            Định tuyến + canh gác bước wizard
│
├─ api/
│  ├─ client.ts               Axios instance, chuẩn hoá lỗi thành ApiError
│  ├─ units.ts                Quy đổi mm ↔ cm. CHỈ CHỖ NÀY được chia 10
│  ├─ files.ts                Upload file
│  ├─ nesting.ts              Tạo job, poll, sinh URL tải file
│  └─ mock.ts                 Chế độ demo — xem mục 7
│
├─ stores/
│  ├─ nestingJob.ts           Toàn bộ dữ liệu wizard 3 bước
│  └─ ui.ts                   Hàng toast
│
├─ types/index.ts             Kiểu dùng chung, khớp 1-1 với DTO backend
│
├─ composables/useId.ts       Sinh id cho cặp label/input
│
├─ assets/styles/
│  ├─ tokens.css              Design token — xem DESIGN-SYSTEM.md
│  ├─ base.css                Reset tối giản + kiểu chữ cơ bản
│  └─ utilities.css           Lớp tiện ích tự viết
│
├─ components/
│  ├─ layout/                 AppShell, AppHeader, PageHeader
│  ├─ ui/                     12 component dùng chung, tiền tố App*
│  └─ nesting/                5 component chuyên cho nghiệp vụ ghép
│
└─ views/                     DashboardView, 3 bước wizard, NotFoundView
```

## 2. Quy ước component

| Quy ước | Lý do |
|---|---|
| **`App*` = dùng chung**, luôn nằm trong `components/ui/` | Nhìn tên là biết có thể dùng ở bất cứ đâu, sửa nó là ảnh hưởng cả app |
| Component trong `components/nesting/` **chỉ** phục vụ luồng ghép | Không cố làm chúng tổng quát khi mới dùng ở một chỗ |
| `views/` chỉ ghép component và nối store, **không** chứa logic tính toán | Logic thuộc về store hoặc `api/` |
| Mọi component chỉ dùng **token CSS**, cấm hard-code màu và khoảng cách | Đổi một token là cả giao diện đổi theo |
| Mỗi màn chỉ có **duy nhất một** nút `primary` | Nhiều nút đậm cùng lúc thì người dùng không biết hành động chính là gì |

### Danh sách component `ui/`

`AppButton` · `AppCard` · `AppInput` · `AppNumberInput` · `AppTable` · `AppStepper` · `AppBadge` · `AppToggle` · `AppEmptyState` · `AppSpinner` · `AppToast` · `AppStatTile`

`AppInput` và `AppNumberInput` dùng chung `field.css` thay vì chép đôi — hai ô nhập trông y hệt nhau, tách ra hai nơi thì sớm muộn sẽ lệch nhau vài pixel.

Props chi tiết ở [DESIGN-SYSTEM.md](DESIGN-SYSTEM.md#6-component).

### Danh sách component `nesting/`

| Component | Việc |
|---|---|
| `FileDropzone` | Kéo thả + chọn file, hiện tiến trình từng file |
| `ItemTable` | Bảng hình: sửa kích thước, số lượng, cờ xoay, xoá |
| `SettingsForm` | Form tham số, validate ngay dưới ô nhập |
| `SheetPreview` | Vẽ một tấm bằng SVG đúng tỷ lệ, có thước đo và tooltip |
| `ResultSummary` | Hàng 4 ô số liệu |

## 3. Quy ước đơn vị

Backend làm việc hoàn toàn bằng **milimet**; người dùng nghĩ bằng **centimet**.

Mọi việc quy đổi tập trung ở `src/api/units.ts`. Trong `SettingsForm`, mỗi trường có một cặp `computed` get/set làm cầu nối:

```ts
const sheetWidthCm = computed({
  get: () => mmToCm(settings.value.sheetWidthMm),
  set: (value) => { settings.value.sheetWidthMm = cmToMm(value ?? 0) },
})
```

Nhờ vậy **không chỗ nào trong app phải nhớ "số này đang là đơn vị gì"** — store luôn mm, ô nhập luôn cm.

## 4. State Pinia

```
┌─────────────────────────── nestingJob ────────────────────────────┐
│                                                                   │
│  STATE                                                            │
│    items: NestItem[]        ← bước 1 ghi vào                      │
│    settings: NestSettings   ← bước 2 ghi vào                      │
│    jobId / job              ← bước 3 đọc                          │
│    submitting               ← khoá nút khi đang gửi               │
│                                                                   │
│  GETTER (dẫn xuất, không lưu trùng)                               │
│    totalQuantity            Σ quantity                            │
│    totalShapeAreaMm2        Σ w × h × quantity                    │
│    canProceedToSettings     có hình & mọi kích thước > 0          │
│    result / isRunning / error   bóc tách từ job                   │
│                                                                   │
│  ACTION                                                           │
│    addFiles · removeItem · resetItemSize · clearAll               │
│    submit()      → POST job, bắt đầu poll, trả jobId              │
│    loadJob(id)   → đọc lại job (khi tải lại trang kết quả)        │
│    stopPolling()                                                  │
└───────────────────────────────────────────────────────────────────┘

┌──────── ui ────────┐
│  toasts: Toast[]   │
│  notify/success/error/dismiss   (tự ẩn sau 4 giây)
└────────────────────┘
```

**Vì sao gộp cả ba bước vào một store?** Để quay lại bước trước không mất dữ liệu — yêu cầu rõ ràng của nút "Ghép lại với tham số khác" ở màn kết quả.

**Vì sao `items` giữ cả `originalWidthMm`?** Để nút hoàn tác trả kích thước về đúng giá trị đọc từ file, sau khi người dùng đã sửa tay.

## 5. Luồng router

```
/                      DashboardView      cửa ngõ duy nhất
/nest/upload           NestUploadView     bước 1
/nest/settings         NestSettingsView   bước 2   ← có canh gác
/nest/result/:jobId    NestResultView     bước 3
/:pathMatch(.*)*       NotFoundView
```

**Canh gác:** route bước 2 mang `meta.requiresItems`. Vào thẳng bằng URL khi chưa có hình nào sẽ bị đẩy về bước 1, tránh màn hình trống trơ.

**Thanh Stepper:** bước đã qua bấm quay lại được; bước chưa tới bị khoá bằng `disabled` thật chứ không chỉ làm mờ, để bấm nhầm không nhảy sang màn thiếu dữ liệu.

**Bước 3 đọc lại job từ URL.** Người dùng tải lại trang hoặc lưu link kết quả thì `onMounted` gọi `store.loadJob(jobId)` — không phụ thuộc state còn sống trong bộ nhớ.

## 6. Gọi API và xử lý lỗi

Mọi lỗi từ axios được interceptor trong `client.ts` chuẩn hoá thành **một** lớp `ApiError` có `code` và `message` tiếng Việt:

```
Backend trả { error: { code, message } }  →  ApiError giữ nguyên
Không có phản hồi (ERR_NETWORK)           →  ApiError('NETWORK_ERROR', "Không kết nối được…
                                               hãy kiểm tra backend hoặc bật chế độ demo")
Mã HTTP khác                              →  ApiError('INTERNAL_ERROR', "Máy chủ trả về lỗi 5xx")
```

Nhờ vậy tầng giao diện **không phải đoán cấu trúc lỗi** và luôn có `code` để phân nhánh.

Lỗi được hiển thị ở hai nơi, tuỳ mức nghiêm trọng:

- **Toast** (`AppToast`, góc trên phải, tự ẩn sau 4 giây) — cho lỗi thao tác lẻ, ví dụ một file upload hỏng trong khi các file khác vẫn vào được.
- **`AppEmptyState` biến thể `error`** — cho lỗi làm hỏng cả màn, ví dụ ghép thất bại. Nêu rõ nguyên nhân kèm nút thử lại.

Lỗi đồng bộ lúc tạo job (như `ITEM_WIDER_THAN_SHEET`) được store dựng thành một job `FAILED` giả lập, nhờ vậy màn kết quả chỉ có **một** chỗ để hiện lỗi thay vì hai đường xử lý song song.

## 7. Chế độ demo (mock)

Netlify chỉ host được frontend tĩnh. Nếu không có chế độ này, bản demo bấm vào đâu cũng báo lỗi mạng — không xem được gì.

Bật bằng `VITE_USE_MOCK=true`, hoặc tự bật khi chưa khai `VITE_API_BASE_URL`. Khi bật:

- `src/api/mock.ts` thay thế toàn bộ tầng API.
- Kích thước file được đọc **ngay trên trình duyệt**: ảnh lấy pixel thật ÷ 96 DPI; PDF thì dò chuỗi `/MediaBox [...]` trong 64 KB đầu file.
- Thuật toán nesting rút gọn chạy bằng TypeScript, trả dữ liệu **đúng y hệt schema thật**.
- Header hiện badge **"Chế độ demo"**.
- Nút tải PDF bị chặn lại kèm thông báo giải thích, thay vì để người dùng bấm vào một link hỏng.

### Khác biệt so với bản thật (có chủ ý)

| | Backend | Chế độ demo |
|---|---|---|
| Heuristic | 4, × 6 thứ tự × ~66 phương án ép hướng | 1 (Best Short Side Fit) |
| Tìm nhị phân chiều dài | có | không |
| Leo đồi tinh chỉnh | có | không |
| Xuất PDF thật | có | không |
| **Lấp đầy trên bộ nghiệm thu** | **92,45%** | **89,33%** |

Chế độ demo dùng để **xem và thử giao diện**, không dùng để chạy đơn thật.

## 8. Lệnh

```bash
npm run dev          # máy chủ dev ở cổng 5173
npm run type-check   # vue-tsc, phải sạch trước khi commit
npm run build        # build production vào dist/
npm run preview      # xem thử bản build
```

Bundle sau khi build: **167 KB** JS chính (64 KB gzip), các view được tách chunk riêng nhờ import động trong router.

## 9. Vì sao không dùng UI library?

Element Plus hay Vuetify kéo theo hàng trăm KB và một hệ thống thiết kế riêng, trong khi app này chỉ cần khoảng 12 component đơn giản. Tự viết cho phép giao diện nhất quán tuyệt đối với bộ token, bundle nhẹ, và không phải chống lại kiểu mặc định của thư viện mỗi lần muốn sửa một chi tiết.
