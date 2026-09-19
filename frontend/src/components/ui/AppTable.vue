<script setup lang="ts">
/**
 * Khung bang dung chung.
 *
 * Chi lo phan vo (vien, nen header, hover, cuon ngang); noi dung do component goi truyen
 * vao qua slot. Nho vay moi bang trong app trong giong nhau ma van tu do ve o.
 */
defineProps<{ columns: string[] }>()
</script>

<template>
  <div class="scroll-x">
    <table class="table">
      <thead>
        <tr>
          <th v-for="column in columns" :key="column" scope="col">{{ column }}</th>
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

/* Cot so can phai de de so sanh gia tri theo chieu doc. */
.table :deep(.col-num) {
  text-align: right;
  font-variant-numeric: tabular-nums;
}
</style>
