/**
 * Màu phân loại hình — dùng chung cho mọi chỗ nhắc tới một loại hình.
 *
 * Bảng này trước đây nằm trong `SheetPreview.vue`. Khi bảng số liệu ở bước 3 cũng cần
 * chấm màu để nối với hình vẽ, chép sang một bản thứ hai là cách chắc chắn nhất để hai
 * bên lệch nhau sau vài lần sửa — nên tách hẳn ra một chỗ.
 *
 * Thứ tự phải khớp đúng với `--c-cat-*` trong `tokens.css`.
 */
const CATEGORY_COLORS = [
  'var(--c-cat-1)',
  'var(--c-cat-2)',
  'var(--c-cat-3)',
  'var(--c-cat-4)',
  'var(--c-cat-5)',
  'var(--c-cat-6)',
  'var(--c-cat-7)',
  'var(--c-cat-8)',
] as const

/**
 * Màu của một loại hình theo `categoryIndex` backend trả về.
 *
 * Quay vòng khi số loại nhiều hơn số màu: thà hai loại trùng màu còn hơn một loại không
 * có màu nào.
 */
export function categoryColor(categoryIndex: number): string {
  return CATEGORY_COLORS[categoryIndex % CATEGORY_COLORS.length]
}
