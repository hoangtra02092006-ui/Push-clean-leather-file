<script setup lang="ts">
/**
 * Vẽ một tấm bằng SVG theo đúng tỷ lệ.
 *
 * Chọn SVG thay vì canvas vì: hình nét ở mọi mức phóng, mỗi hình chữ nhật là một phần tử
 * thật nên gắn tooltip và sự kiện chuột trực tiếp được, và không phải tự vẽ lại khi đổi
 * kích thước cửa sổ.
 *
 * Hệ toạ độ: backend trả gốc TRÁI-DƯỚI (quy ước của PDF), còn SVG lấy gốc TRÁI-TRÊN. Ta
 * lật trục Y một lần duy nhất ở đây, để mọi phép tính còn lại đọc thẳng.
 */
import { computed, ref } from 'vue'
import { formatCm } from '@/api/units'
import { categoryColor } from '@/composables/useCategoryColor'
import type { Placement, Sheet } from '@/types'

const props = defineProps<{ sheet: Sheet }>()

/** Bề rộng dải thước đo bên ngoài, tính theo đơn vị milimét của hệ toạ độ SVG. */
const RULER_MM = 26

const hovered = ref<Placement | null>(null)
const pointer = ref({ x: 0, y: 0 })

const viewBox = computed(
  () =>
    `${-RULER_MM} ${-RULER_MM} ${props.sheet.widthMm + RULER_MM * 2} ${
      props.sheet.lengthMm + RULER_MM * 2
    }`,
)

function colorOf(placement: Placement): string {
  return categoryColor(placement.categoryIndex)
}

/** Lật trục Y: y của SVG = chiều dài tấm − (y dưới + chiều cao hình). */
function topOf(placement: Placement): number {
  return props.sheet.lengthMm - placement.yMm - placement.hMm
}

/**
 * Chỉ ghi nhãn kích thước vào giữa hình khi hình đủ rộng/đủ cao.
 * Nhãn chen chúc trong ô bé xíu còn khó đọc hơn là không có nhãn.
 */
function fitsLabel(placement: Placement): boolean {
  return placement.wMm >= 45 && placement.hMm >= 22
}

/** Cỡ chữ của nhãn, tính theo mm để không bị phóng to quá đà khi tấm nhỏ. */
function labelSize(placement: Placement): number {
  return Math.min(11, Math.max(6, Math.min(placement.wMm / 6, placement.hMm / 3)))
}

/** Vạch chia thước đo: mỗi 10 cm một vạch. */
const ticksX = computed(() => buildTicks(props.sheet.widthMm))
const ticksY = computed(() => buildTicks(props.sheet.lengthMm))

function buildTicks(totalMm: number): number[] {
  const stepMm = 100
  const ticks: number[] = []
  for (let value = 0; value <= totalMm; value += stepMm) {
    ticks.push(value)
  }
  return ticks
}

function onEnter(placement: Placement, event: MouseEvent) {
  hovered.value = placement
  updatePointer(event)
}

function updatePointer(event: MouseEvent) {
  const host = (event.currentTarget as SVGElement).ownerSVGElement?.parentElement
  if (!host) return
  const rect = host.getBoundingClientRect()
  pointer.value = { x: event.clientX - rect.left, y: event.clientY - rect.top }
}
</script>

<template>
  <div class="preview">
    <svg :viewBox="viewBox" class="preview__svg" role="img"
      :aria-label="`Bố trí tấm ${sheet.index + 1}: ${sheet.placements.length} hình`">
      <!-- Nền tấm -->
      <rect
        x="0"
        y="0"
        :width="sheet.widthMm"
        :height="sheet.lengthMm"
        class="sheet-bg"
      />

      <!-- Thước đo cạnh trên -->
      <g class="ruler">
        <line x1="0" :y1="-6" :x2="sheet.widthMm" :y2="-6" />
        <template v-for="tick in ticksX" :key="`x-${tick}`">
          <line :x1="tick" :y1="-6" :x2="tick" :y2="-12" />
          <text :x="tick" :y="-15" text-anchor="middle">{{ formatCm(tick, 0) }}</text>
        </template>
      </g>

      <!-- Thước đo cạnh trái (số đo từ dưới lên, đúng chiều với bản in) -->
      <g class="ruler">
        <line :x1="-6" y1="0" :x2="-6" :y2="sheet.lengthMm" />
        <template v-for="tick in ticksY" :key="`y-${tick}`">
          <line :x1="-6" :y1="sheet.lengthMm - tick" :x2="-12" :y2="sheet.lengthMm - tick" />
          <text
            :x="-15"
            :y="sheet.lengthMm - tick"
            text-anchor="end"
            dominant-baseline="middle"
          >
            {{ formatCm(tick, 0) }}
          </text>
        </template>
      </g>

      <!-- Các hình đã đặt -->
      <g
        v-for="(placement, index) in sheet.placements"
        :key="`${placement.fileId}-${index}`"
        class="piece"
        @mouseenter="onEnter(placement, $event)"
        @mousemove="updatePointer"
        @mouseleave="hovered = null"
      >
        <rect
          :x="placement.xMm"
          :y="topOf(placement)"
          :width="placement.wMm"
          :height="placement.hMm"
          :fill="colorOf(placement)"
          fill-opacity="0.18"
          :stroke="colorOf(placement)"
          stroke-width="0.8"
          rx="1"
        />
        <text
          v-if="fitsLabel(placement)"
          :x="placement.xMm + placement.wMm / 2"
          :y="topOf(placement) + placement.hMm / 2"
          :fill="colorOf(placement)"
          :font-size="labelSize(placement)"
          text-anchor="middle"
          dominant-baseline="middle"
          class="piece__label"
        >
          {{ formatCm(placement.wMm) }}x{{ formatCm(placement.hMm) }}
        </text>
      </g>

      <!-- Viền khổ, vẽ sau cùng để luôn nằm trên -->
      <rect
        x="0"
        y="0"
        :width="sheet.widthMm"
        :height="sheet.lengthMm"
        class="sheet-border"
      />
    </svg>

    <div
      v-if="hovered"
      class="tooltip"
      :style="{ left: `${pointer.x + 14}px`, top: `${pointer.y + 14}px` }"
    >
      <strong class="tooltip__name">{{ hovered.label }}</strong>
      <span class="num">{{ formatCm(hovered.wMm) }} x {{ formatCm(hovered.hMm) }} cm</span>
      <span class="num text-mute">
        vị trí {{ formatCm(hovered.xMm) }} ; {{ formatCm(hovered.yMm) }} cm
      </span>
      <span :class="hovered.rotated ? 'text-accent' : 'text-mute'">
        {{ hovered.rotated ? 'Đã xoay 90 độ' : 'Giữ nguyên hướng' }}
      </span>
    </div>
  </div>
</template>

<style scoped>
.preview {
  position: relative;
  background: var(--c-bg);
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  padding: var(--s-4);
}

.preview__svg {
  display: block;
  width: 100%;
  /* Tấm rất dài nên giới hạn chiều cao và cho cuộn, thay vì ép vào một khung bé tí. */
  max-height: 70vh;
}

.sheet-bg {
  fill: var(--c-surface);
}

.sheet-border {
  fill: none;
  stroke: var(--c-border-strong);
  stroke-width: 1;
}

.ruler line {
  stroke: var(--c-text-mute);
  stroke-width: 0.6;
}

.ruler text {
  fill: var(--c-text-mute);
  font-size: 9px;
  font-family: var(--f-sans);
}

.piece {
  cursor: pointer;
}

.piece:hover rect {
  fill-opacity: 0.34;
}

.piece__label {
  font-family: var(--f-sans);
  font-weight: 600;
  pointer-events: none;
}

.tooltip {
  position: absolute;
  z-index: 5;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--s-2) var(--s-3);
  background: var(--c-surface);
  border: 1px solid var(--c-border-strong);
  border-radius: var(--r-sm);
  box-shadow: var(--sh-2);
  font-size: var(--fs-xs);
  pointer-events: none;
  white-space: nowrap;
}

.tooltip__name {
  font-size: var(--fs-sm);
}

.text-accent {
  color: var(--c-accent);
}
</style>
