<script setup lang="ts">
/**
 * Khung bảng dùng chung.
 *
 * Chỉ lo phần vỏ (viền, nền header, hover, cuộn ngang); nội dung do component gọi truyền
 * vào qua slot. Nhờ vậy mọi bảng trong app trông giống nhau mà vẫn tự do vẽ ô.
 *
 * <p>Mỗi cột khai luôn CĂN LỀ của nó. Trước đây tiêu đề bị ép cứng căn trái trong khi ô
 * dữ liệu số lại căn phải, nên cột số nào cũng lệch: chữ "Rộng (cm)" nằm ở mép trái còn
 * con số nằm tít mép phải. Khai một chỗ rồi dùng cho cả `th` lẫn `td` thì hai hàng
 * không thể lệch nhau được nữa.
 */
import { computed } from 'vue'

type ColumnAlign = 'left' | 'right' | 'center'

/** Một cột: chỉ cần chuỗi nếu căn trái, khai object khi muốn căn khác. */
type TableColumn = string | { label: string; align?: ColumnAlign }

const props = defineProps<{ columns: TableColumn[] }>()

const normalized = computed(() =>
  props.columns.map((column) =>
    typeof column === 'string'
      ? { label: column, align: 'left' as ColumnAlign }
      : { label: column.label, align: column.align ?? 'left' },
  ),
)
</script>

<template>
  <div class="scroll-x">
    <table class="table">
      <thead>
        <tr>
          <th
            v-for="(column, index) in normalized"
            :key="index"
            scope="col"
            :class="`col-${column.align}`"
          >
            {{ column.label }}
          </th>
        </tr>
      </thead>
      <tbody>
        <slot />
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--fs-base);
}

.table :deep(th) {
  position: sticky;
  top: 0;
  background: var(--c-bg);
  padding: var(--s-2) var(--s-3);
  border-bottom: 1px solid var(--c-border);
  color: var(--c-text-soft);
  font-size: var(--fs-sm);
  font-weight: 550;
  text-align: left;
  letter-spacing: 0.02em;
  white-space: nowrap;
}

.table :deep(td) {
  padding: var(--s-3);
  border-bottom: 1px solid var(--c-border);
  vertical-align: middle;
}

.table :deep(tbody tr:hover) {
  background: var(--c-bg);
}

.table :deep(tbody tr:last-child td) {
  border-bottom: 0;
}

/* Căn lề dùng chung cho tiêu đề và ô dữ liệu, nhờ vậy hai hàng luôn thẳng nhau. */
.table :deep(.col-left) {
  text-align: left;
}

.table :deep(.col-right) {
  text-align: right;
}

.table :deep(.col-center) {
  text-align: center;
}

/* Cột số căn phải để dễ so sánh giá trị theo chiều dọc. */
.table :deep(.col-num) {
  text-align: right;
  font-variant-numeric: tabular-nums;
}
</style>
