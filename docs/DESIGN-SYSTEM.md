# Design System

Định nghĩa ở `frontend/src/assets/styles/tokens.css`.

## 1. Nguyên tắc

1. **Một màu nhấn duy nhất**, còn lại là thang xám. Màu nhấn chỉ dùng cho hành động chính và trạng thái đang chọn — dùng tràn lan thì nó hết "nhấn".
2. **Phân cấp bằng viền và nền**, không bằng đổ bóng nặng. Chỉ có hai mức bóng, cả hai đều rất nhẹ.
3. **Khoảng trắng rộng rãi.** Thà thừa chỗ còn hơn chen chúc.
4. **Chữ số dùng `tabular-nums`.** Không có nó, các cột số trong bảng sẽ nhảy qua nhảy lại khi giá trị đổi.
5. **Mọi component chỉ dùng token.** Cấm hard-code mã màu hay số pixel.

---

## 2. Màu

### Nền và viền

| Token | Giá trị | Dùng cho |
|---|---|---|
| `--c-bg` | `#f7f8fa` | ⬜ Nền trang, nền header bảng |
| `--c-surface` | `#ffffff` | ⬜ Nền card, ô nhập, bảng |
| `--c-border` | `#e3e6ea` | ▫️ Viền mặc định |
| `--c-border-strong` | `#c9ced6` | ▪️ Viền ô nhập, viền card nổi bật hơn một bậc |

### Chữ

| Token | Giá trị | Dùng cho |
|---|---|---|
| `--c-text` | `#1a1d21` | ⬛ Chữ chính |
| `--c-text-soft` | `#5b6472` | Chữ phụ, mô tả |
| `--c-text-mute` | `#8b94a3` | Nhãn, gợi ý, đơn vị |

### Màu nhấn

| Token | Giá trị | Dùng cho |
|---|---|---|
| `--c-accent` | `#2563eb` | 🟦 Nút chính, link, trạng thái đang chọn |
| `--c-accent-hover` | `#1d4ed8` | Nút chính khi rê chuột |
| `--c-accent-soft` | `#eff4ff` | Nền nhạt, vòng focus |

### Ngữ nghĩa

| Token | Giá trị | | Nền nhạt |
|---|---|---|---|
| `--c-success` | `#0f9d58` | 🟩 | `--c-success-soft` `#e8f5ee` |
| `--c-warn` | `#d97706` | 🟧 | `--c-warn-soft` `#fdf3e3` |
| `--c-danger` | `#dc2626` | 🟥 | `--c-danger-soft` `#fdecec` |

### Bảng màu phân loại hình (preview)

Dùng trong `SheetPreview` để tô màu theo **loại hình**. Thứ tự **cố định**, ánh xạ thẳng từ `categoryIndex` mà backend trả về.

| Token | Giá trị | | Token | Giá trị |
|---|---|---|---|---|
| `--c-cat-1` | `#7c3aed` 🟣 | | `--c-cat-5` | `#db2777` 🩷 |
| `--c-cat-2` | `#ea580c` 🟠 | | `--c-cat-6` | `#16a34a` 🟢 |
| `--c-cat-3` | `#0891b2` 🔵 | | `--c-cat-7` | `#4f46e5` 🔷 |
| `--c-cat-4` | `#ca8a04` 🟡 | | `--c-cat-8` | `#b91c1c` 🔴 |

> **Không random màu.** Một loại hình phải giữ nguyên một màu qua mọi lần ghép, nếu không thợ sẽ đọc nhầm bản vẽ. Quá 8 loại thì quay vòng bằng phép chia dư.

---

## 3. Khoảng cách — thang 4px

| Token | px | Dùng cho |
|---|---|---|
| `--s-1` | 4 | Khe giữa nhãn và giá trị |
| `--s-2` | 8 | Khe giữa các phần tử cùng hàng |
| `--s-3` | 12 | Padding ô bảng |
| `--s-4` | 16 | Khe giữa các card, **gutter hai bên trang** |
| `--s-5` | 24 | Padding trong card |
| `--s-6` | 32 | Khe giữa các khối lớn |
| `--s-7` | 48 | Padding trạng thái rỗng |
| `--s-8` | 64 | Chừa dưới cùng trang |

## 4. Bo góc và đổ bóng

| Token | Giá trị | Dùng cho |
|---|---|---|
| `--r-sm` | `6px` | Nút, ô nhập, badge |
| `--r-md` | `10px` | Card, bảng, vùng preview |
| `--r-lg` | `14px` | Khối lớn |
| `--sh-1` | `0 1px 2px rgba(16,24,40,.05)` | Núm toggle |
| `--sh-2` | `0 4px 12px rgba(16,24,40,.08)` | Toast, card khi rê chuột, chân trang dính |

## 5. Chữ

Font: `--f-sans` = `'Inter', system-ui, -apple-system, 'Segoe UI', sans-serif`.

| Token | px | Dùng cho |
|---|---|---|
| `--fs-xs` | 12 | Nhãn ô số liệu, gợi ý, badge |
| `--fs-sm` | 13 | Chữ phụ, header bảng, nút nhỏ |
| `--fs-base` | 14 | Chữ thân, ô nhập, nút |
| `--fs-lg` | 16 | Tiêu đề card, tên chức năng |
| `--fs-xl` | 20 | `h2` |
| `--fs-2xl` | 26 | `h1`, con số trong ô số liệu |
| `--fs-3xl` | 32 | Dành cho số liệu rất lớn |

Thời gian chuyển cảnh: `--t-fast` 120ms (đổi màu, đổi viền), `--t-base` 200ms (trượt, hiện/ẩn).

---

## 6. Component

### `AppButton`

| Prop | Kiểu | Mặc định |
|---|---|---|
| `variant` | `primary \| secondary \| ghost` | `secondary` |
| `size` | `md \| sm` | `md` |
| `disabled` / `loading` / `block` | `boolean` | `false` |

```
primary    ▐ nền accent, chữ trắng       → hành động chính, MỖI MÀN CHỈ MỘT
secondary  ▢ nền trắng, viền đậm         → hành động phụ
ghost      ░ trong suốt, chữ mờ          → hành động phụ trợ (quay lại, trợ giúp)
```

Cao 38px (`sm`: 30px). `loading` tự hiện spinner và khoá nút.

### `AppInput` / `AppNumberInput`

| Prop | Ghi chú |
|---|---|
| `label` | **Bắt buộc**, gắn `for` vào input |
| `hint` | Dòng gợi ý nhỏ bên dưới |
| `error` | Thay chỗ `hint`, đổi viền sang đỏ |
| `suffix` | Đơn vị hiện trong ô (`cm`) |
| `allowEmpty` | Chỉ `AppNumberInput`: cho phép để trống → model `null` |

Cao 38px, viền 1px. Focus: viền đổi sang accent + ring 3px `--c-accent-soft`.

> `AppNumberInput` dùng model `number | null`. `null` = ô để trống, có nghĩa riêng ở vài trường ("chiều dài tối đa để trống = không giới hạn"). Ép về `0` thì không phân biệt được với việc người dùng nhập số 0.

### `AppCard`

Nền `--c-surface`, viền 1px, bo `--r-md`, padding `--s-5`. Props: `title`, `subtitle`; slot `actions` ở góc phải tiêu đề.

### `AppTable`

Props: `columns: string[]`. Nội dung do component gọi truyền qua slot mặc định.

Header nền `--c-bg`, chữ `--fs-sm`, dính khi cuộn. Hàng có viền dưới 1px, rê chuột đổi nền. Thêm class `col-num` vào `<td>` để căn phải + `tabular-nums`. Bọc sẵn `.scroll-x` nên tự cuộn ngang ở màn hẹp.

### `AppStepper`

Props: `current: number` (1–3). Bước đã qua bấm quay lại được; bước chưa tới `disabled` thật.

### `AppBadge`

Props: `tone: neutral | accent | success | warn | danger`.

### `AppToggle`

Props: `label`, `hint`, `disabled`. Model `boolean`. Dùng `<button role="switch">` nên bộ đọc màn hình hiểu đúng.

### `AppEmptyState`

| Prop | Ghi chú |
|---|---|
| `title` | **Bắt buộc** |
| `description` | Câu hướng dẫn người dùng làm gì tiếp |
| `variant` | `empty` (viền đứt, xám) hoặc `error` (viền đỏ, nền đỏ nhạt) |

**Bắt buộc dùng** ở mọi chỗ có thể không có dữ liệu. Không màn nào được rơi vào trạng thái trắng trơn.

### `AppSpinner`

Props: `size` (px), `label`, `inline`. Tôn trọng `prefers-reduced-motion`.

### `AppToast`

Đặt **một lần** trong `AppShell`, điều khiển qua store `ui`. Góc trên phải, tự ẩn sau **4 giây**, có nút đóng. Ba loại: `success`, `error`, `info` — phân biệt bằng dải màu bên trái.

### `AppStatTile`

Props: `label`, `value`, `unit`, `tone`. Nhãn nhỏ viết hoa ở trên, con số to `--fs-2xl` ở dưới, đơn vị nhỏ hơn số.

---

## 7. Responsive

| Ngưỡng | Thay đổi |
|---|---|
| ≥ 1280px | Bố cục đầy đủ (mục tiêu chính) |
| ≤ 1024px | Lưới card Dashboard về 2 cột |
| ≤ 900px | Bước 2 xếp dọc; ô số liệu về 2 cột |
| ≤ 768px | Lưới card về **1 cột**; bảng cuộn ngang; gutter hai bên **16px**; không có cuộn ngang toàn trang |

## 8. Khả năng tiếp cận

- Mọi `label` gắn `for` trỏ đúng `id` (sinh bởi `useId`).
- Vòng focus nhìn thấy được: `:focus-visible` cho outline 2px màu accent, offset 2px.
- Tương phản chữ ≥ 4.5:1: `--c-text` trên `--c-surface` đạt ~16:1, `--c-text-soft` đạt ~6:1.
- Icon trang trí gắn `aria-hidden="true"`; nút chỉ có icon luôn có `title` hoặc `aria-label`.
- Toast đặt trong vùng `role="status" aria-live="polite"`.
- `prefers-reduced-motion` được tôn trọng ở spinner và hiệu ứng nhấc card.

## 9. Icon

Vẽ tay lại theo bộ **Lucide**: SVG inline, nét stroke, 24px, `stroke-width` 1.8–2, `stroke-linecap="round"`.

Không nạp font icon hay thư viện icon — thêm một request và một phụ thuộc chỉ để vẽ vài hình là không đáng.

Icon đang dùng: `layout-grid`, `folder`, `history`, `sliders`, `bar-chart` (Dashboard), `upload` (dropzone), `rotate-ccw` và `trash` (bảng hình), `alert-circle` (trạng thái lỗi).
