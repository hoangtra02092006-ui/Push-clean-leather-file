<script setup lang="ts">
/**
 * Bang danh sach hinh can ghep.
 *
 * O kich thuoc CHO SUA TAY vi metadata file hay sai (file xuat tu Illustrator co the mang
 * vung tran le, anh khong ghi DPI...). Khi da sua, hien nut hoan tac de quay ve dung gia
 * tri doc duoc tu file - tho co the doi y ma khong phai tai lai file.
 */
import { computed } from 'vue'
import AppTable from '@/components/ui/AppTable.vue'
import AppToggle from '@/components/ui/AppToggle.vue'
import AppBadge from '@/components/ui/AppBadge.vue'
import { cmToMm, formatCm } from '@/api/units'
import type { NestItem } from '@/types'

const props = defineProps<{ items: NestItem[] }>()
const emit = defineEmits<{ remove: [fileId: string]; reset: [fileId: string] }>()

const columns = ['Xem truoc', 'Ten file', 'Rong (cm)', 'Dai (cm)', 'So luong', 'Cho xoay', '']

/** Dong nao da bi sua kich thuoc so voi file goc. */
const edited = computed(
  () =>
    new Set(
      props.items
        .filter(
          (item) =>
            item.widthMm !== item.originalWidthMm || item.heightMm !== item.originalHeightMm,
        )
        .map((item) => item.fileId),
    ),
)

/** Gan gia tri cm tu o nhap ve lai mm trong store. */
function setWidth(item: NestItem, raw: string) {
  const value = Number(raw.replace(',', '.'))
  if (!Number.isNaN(value) && value > 0) {
    item.widthMm = cmToMm(value)
  }
}

function setHeight(item: NestItem, raw: string) {
  const value = Number(raw.replace(',', '.'))
  if (!Number.isNaN(value) && value > 0) {
    item.heightMm = cmToMm(value)
  }
}

function setQuantity(item: NestItem, raw: string) {
  const value = Math.floor(Number(raw))
  item.quantity = Number.isNaN(value) || value < 1 ? 1 : value
}
</script>

<template>
  <AppTable :columns="columns">
    <tr v-for="item in items" :key="item.fileId">
      <td>
        <span class="thumb">
          <img :src="item.previewUrl" :alt="`Xem truoc ${item.label}`" loading="lazy" />
        </span>
      </td>

      <td>
        <span class="name truncate" :title="item.label">{{ item.label }}</span>
        <span class="name__meta">
          <AppBadge tone="neutral">{{ item.type === 'PDF' ? 'PDF' : 'Anh' }}</AppBadge>
          <span v-if="edited.has(item.fileId)" class="text-xs text-mute">
            goc {{ formatCm(item.originalWidthMm) }} x {{ formatCm(item.originalHeightMm) }} cm
          </span>
        </span>
      </td>

      <td class="col-num">
        <input
          class="cell-input"
          type="number"
          min="0.1"
          step="0.1"
          :value="formatCm(item.widthMm, 2)"
          :aria-label="`Chieu rong cua ${item.label}`"
          @input="setWidth(item, ($event.target as HTMLInputElement).value)"
        />
      </td>

      <td class="col-num">
        <input
          class="cell-input"
          type="number"
          min="0.1"
          step="0.1"
          :value="formatCm(item.heightMm, 2)"
          :aria-label="`Chieu dai cua ${item.label}`"
          @input="setHeight(item, ($event.target as HTMLInputElement).value)"
        />
      </td>

      <td class="col-num">
        <input
          class="cell-input cell-input--narrow"
          type="number"
          min="1"
          step="1"
          :value="item.quantity"
          :aria-label="`So luong cua ${item.label}`"
          @input="setQuantity(item, ($event.target as HTMLInputElement).value)"
        />
      </td>

      <td>
        <AppToggle v-model="item.allowRotate" :label="item.allowRotate ? 'Co' : 'Khong'" />
      </td>

      <td>
        <div class="actions">
          <button
            v-if="edited.has(item.fileId)"
            type="button"
            class="icon-btn"
            title="Tra ve kich thuoc doc tu file"
            @click="emit('reset', item.fileId)"
          >
            <svg
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M3 12a9 9 0 1 0 3-6.7L3 8" />
              <path d="M3 3v5h5" />
            </svg>
          </button>

          <button
            type="button"
            class="icon-btn icon-btn--danger"
            :title="`Xoa ${item.label}`"
            @click="emit('remove', item.fileId)"
          >
            <svg
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M4 7h16" />
              <path d="M10 11v6" />
              <path d="M14 11v6" />
              <path d="M6 7l1 13h10l1-13" />
              <path d="M9 7V4h6v3" />
            </svg>
          </button>
        </div>
      </td>
    </tr>
  </AppTable>
</template>

<style scoped>
.thumb {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border: 1px solid var(--c-border);
  border-radius: var(--r-sm);
  background: var(--c-bg);
  overflow: hidden;
}

.thumb img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.name {
  display: block;
  max-width: 220px;
  font-weight: 550;
}

.name__meta {
  display: flex;
  align-items: center;
  gap: var(--s-2);
  margin-top: var(--s-1);
}

.cell-input {
  width: 92px;
  height: 32px;
  padding: 0 var(--s-2);
  border: 1px solid var(--c-border);
  border-radius: var(--r-sm);
  background: var(--c-surface);
  color: var(--c-text);
  font-family: inherit;
  font-size: var(--fs-sm);
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.cell-input--narrow {
  width: 70px;
}

.cell-input:focus {
  outline: none;
  border-color: var(--c-accent);
  box-shadow: 0 0 0 3px var(--c-accent-soft);
}

.cell-input::-webkit-outer-spin-button,
.cell-input::-webkit-inner-spin-button {
  appearance: none;
  margin: 0;
}

.cell-input[type='number'] {
  appearance: textfield;
  -moz-appearance: textfield;
}

.actions {
  display: flex;
  gap: var(--s-1);
  justify-content: flex-end;
}

.icon-btn {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 1px solid var(--c-border);
  border-radius: var(--r-sm);
  background: var(--c-surface);
  color: var(--c-text-soft);
  cursor: pointer;
  transition: border-color var(--t-fast), color var(--t-fast);
}

.icon-btn:hover {
  border-color: var(--c-accent);
  color: var(--c-accent);
}

.icon-btn--danger:hover {
  border-color: var(--c-danger);
  color: var(--c-danger);
}
</style>
