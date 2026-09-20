<script setup lang="ts">
/**
 * Bảng danh sách hình cần ghép.
 *
 * Ô kích thước CHO SỬA TAY vì metadata file hay sai (file xuất từ Illustrator có thể mang
 * vùng tràn lề, ảnh không ghi DPI...). Khi đã sửa, hiện nút hoàn tác để quay về đúng giá
 * trị đọc được từ file — thợ có thể đổi ý mà không phải tải lại file.
 *
 * Bấm vào ô xem trước sẽ mở ảnh to giữa màn hình. Ảnh đó là ảnh ĐÃ CẮT khoảng trắng,
 * đúng bằng kích thước ghi trong bảng, để thợ đối chiếu được ngay ở bước 1 thay vì phải
 * chạy ghép xong mới phát hiện app hiểu sai khung hình.
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import AppTable from '@/components/ui/AppTable.vue'
import AppToggle from '@/components/ui/AppToggle.vue'
import AppBadge from '@/components/ui/AppBadge.vue'
import { cmToMm, formatCm, parseDecimalInput } from '@/api/units'
import type { NestItem } from '@/types'

const props = defineProps<{ items: NestItem[] }>()
const emit = defineEmits<{ remove: [fileId: string]; reset: [fileId: string] }>()

const columns = [
  'Xem trước',
  'Tên file',
  { label: 'Rộng (cm)', align: 'right' as const },
  { label: 'Dài (cm)', align: 'right' as const },
  { label: 'Số lượng', align: 'right' as const },
  'Cho xoay',
  '',
]

/** Dòng nào đã bị sửa kích thước so với file gốc. */
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

/**
 * Hình đang được xem to; `null` là đang đóng khung phóng to.
 */
const zoomed = ref<NestItem | null>(null)

// Nghe Esc ở cấp cửa sổ: bắt trên chính thẻ <div> chỉ chạy khi nó đang được focus, mà
// người dùng vừa bấm chuột vào ảnh nên focus vẫn nằm ở nút thu nhỏ.
function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    zoomed.value = null
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

/** Gán giá trị cm từ ô nhập về lại mm trong store. */
function setWidth(item: NestItem, raw: string) {
  const value = parseDecimalInput(raw)
  if (value !== null && value > 0) {
    item.widthMm = cmToMm(value)
  }
}

function setHeight(item: NestItem, raw: string) {
  const value = parseDecimalInput(raw)
  if (value !== null && value > 0) {
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
        <button
          type="button"
          class="thumb"
          :title="`Bấm để xem to ${item.label}`"
          @click="zoomed = item"
        >
          <img :src="item.previewUrl" :alt="`Xem trước ${item.label}`" loading="lazy" />
        </button>
      </td>

      <td>
        <span class="name truncate" :title="item.label">{{ item.label }}</span>
        <span class="name__meta">
          <AppBadge tone="neutral">{{ item.type === 'PDF' ? 'PDF' : 'Ảnh' }}</AppBadge>
          <!-- Báo cho thợ biết app đã tự bỏ phần trắng bao quanh, kèm số gốc để đối
               chiếu. Không nói ra thì nhìn bảng sẽ tưởng app đọc sai kích thước file. -->
          <AppBadge
            v-if="item.trimmed"
            tone="success"
            :title="`Khổ trang gốc ${formatCm(item.sourceWidthMm)} x ${formatCm(item.sourceHeightMm)} cm, đã bỏ phần trắng bao quanh`"
          >
            đã cắt trắng
          </AppBadge>
          <span v-if="edited.has(item.fileId)" class="text-xs text-mute">
            gốc {{ formatCm(item.originalWidthMm) }} x {{ formatCm(item.originalHeightMm) }} cm
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
          :aria-label="`Chiều rộng của ${item.label}`"
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
          :aria-label="`Chiều dài của ${item.label}`"
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
          :aria-label="`Số lượng của ${item.label}`"
          @input="setQuantity(item, ($event.target as HTMLInputElement).value)"
        />
      </td>

      <td>
        <div class="rotate">
          <AppToggle v-model="item.allowRotate" :label="item.allowRotate ? 'Có' : 'Không'" />
        </div>
      </td>

      <td>
        <div class="actions">
          <button
            v-if="edited.has(item.fileId)"
            type="button"
            class="icon-btn"
            title="Trả về kích thước đọc từ file"
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
            :title="`Xoá ${item.label}`"
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

  <!--
    Khung phóng to. Dựng ngay trong component này thay vì một modal dùng chung vì nó chỉ
    có một việc: phóng to đúng ảnh vừa bấm. Bấm ra ngoài hoặc phím Esc đều đóng được.
  -->
  <div
    v-if="zoomed"
    class="zoom"
    role="dialog"
    aria-modal="true"
    :aria-label="`Xem trước ${zoomed.label}`"
    @click.self="zoomed = null"
  >
    <div class="zoom__panel">
      <div class="zoom__head">
        <strong class="truncate" :title="zoomed.label">{{ zoomed.label }}</strong>
        <button type="button" class="zoom__close" aria-label="Đóng" @click="zoomed = null">
          &times;
        </button>
      </div>

      <div class="zoom__stage">
        <img :src="zoomed.previewUrl" :alt="`Xem trước ${zoomed.label}`" />
      </div>

      <p class="zoom__foot num">
        {{ formatCm(zoomed.widthMm) }} x {{ formatCm(zoomed.heightMm) }} cm
        <span v-if="zoomed.trimmed" class="text-mute">
          · đã bỏ phần trắng bao quanh (khổ gốc
          {{ formatCm(zoomed.sourceWidthMm) }} x {{ formatCm(zoomed.sourceHeightMm) }} cm)
        </span>
      </p>
    </div>
  </div>
</template>

<style scoped>
/*
  Nhãn của công tắc đổi giữa "Có" và "Không" - hai chuỗi dài ngắn khác nhau. Để mặc cho
  ô tự co giãn thì mỗi lần bật/tắt là cả bảng bị đẩy sang một khoảng. Chốt bề rộng đủ
  chứa chuỗi dài hơn thì bảng đứng yên.
*/
.rotate :deep(.toggle__label) {
  display: inline-block;
  min-width: 3.5rem;
}

.thumb {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  padding: 0;
  border: 1px solid var(--c-border);
  border-radius: var(--r-sm);
  background: var(--c-bg);
  overflow: hidden;
  cursor: zoom-in;
  transition: border-color var(--t-fast);
}

.thumb:hover {
  border-color: var(--c-accent);
}

.zoom {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: grid;
  place-items: center;
  padding: var(--s-5);
  background: rgb(15 23 42 / 55%);
}

.zoom__panel {
  display: flex;
  flex-direction: column;
  gap: var(--s-3);
  max-width: min(680px, 90vw);
  padding: var(--s-4);
  background: var(--c-surface);
  border-radius: var(--r-md);
  box-shadow: var(--sh-2);
}

.zoom__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--s-3);
}

.zoom__close {
  border: none;
  background: none;
  color: var(--c-text-soft);
  font-size: var(--fs-xl);
  line-height: 1;
  cursor: pointer;
}

/* Nền ô carô để thấy rõ đâu là phần trong suốt, đâu là nét trắng của hình. */
.zoom__stage {
  display: grid;
  place-items: center;
  padding: var(--s-3);
  border: 1px solid var(--c-border);
  border-radius: var(--r-sm);
  background-color: var(--c-bg);
  background-image:
    linear-gradient(45deg, var(--c-border) 25%, transparent 25%),
    linear-gradient(-45deg, var(--c-border) 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, var(--c-border) 75%),
    linear-gradient(-45deg, transparent 75%, var(--c-border) 75%);
  background-size: 14px 14px;
  background-position: 0 0, 0 7px, 7px -7px, -7px 0;
}

.zoom__stage img {
  max-width: 100%;
  max-height: 60vh;
  object-fit: contain;
}

.zoom__foot {
  margin: 0;
  font-size: var(--fs-sm);
  color: var(--c-text-soft);
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
