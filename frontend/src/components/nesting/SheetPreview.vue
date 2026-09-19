<script setup lang="ts">
/**
 * Ve mot tam bang SVG theo dung ty le.
 *
 * Chon SVG thay vi canvas vi: hinh net o moi muc phong, moi hinh chu nhat la mot phan tu
 * that nen gan tooltip va su kien chuot truc tiep duoc, va khong phai tu ve lai khi doi
 * kich thuoc cua so.
 *
 * He toa do: backend tra goc TRAI-DUOI (quy uoc cua PDF), con SVG lay goc TRAI-TREN. Ta
 * lat truc Y mot lan duy nhat o day, de moi phep tinh con lai doc thang.
 */
import { computed, ref } from 'vue'
import { formatCm } from '@/api/units'
import type { Placement, Sheet } from '@/types'

const props = defineProps<{ sheet: Sheet }>()

/** Bang mau phan loai - khop dung thu tu voi --c-cat-* trong tokens.css. */
const CATEGORY_COLORS = [
  'var(--c-cat-1)',
  'var(--c-cat-2)',
  'var(--c-cat-3)',
  'var(--c-cat-4)',
  'var(--c-cat-5)',
  'var(--c-cat-6)',
  'var(--c-cat-7)',
  'var(--c-cat-8)',
]

/** Be rong dai thuoc do ben ngoai, tinh theo don vi milimet cua he toa do SVG. */
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
  return CATEGORY_COLORS[placement.categoryIndex % CATEGORY_COLORS.length]
}

/** Lat truc Y: y cua SVG = chieu dai tam - (y duoi + chieu cao hinh). */
function topOf(placement: Placement): number {
  return props.sheet.lengthMm - placement.yMm - placement.hMm
}

/**
 * Chi ghi nhan kich thuoc vao giua hinh khi hinh du rong/du cao.
 * Nhan chen chuc trong o be xiu con kho doc hon la khong co nhan.
 */
function fitsLabel(placement: Placement): boolean {
  return placement.wMm >= 45 && placement.hMm >= 22
}

/** Co chu cua nhan, tinh theo mm de khong bi phong to qua da khi tam nho. */
function labelSize(placement: Placement): number {
  return Math.min(11, Math.max(6, Math.min(placement.wMm / 6, placement.hMm / 3)))
}

/** Vach chia thuoc do: moi 10 cm mot vach. */
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
      :aria-label="`Bo tri tam ${sheet.index + 1}: ${sheet.placements.length} hinh`">
      <!-- Nen tam -->
      <rect
        x="0"
        y="0"
        :width="sheet.widthMm"
        :height="sheet.lengthMm"
        class="sheet-bg"
      />

      <!-- Thuoc do canh tren -->
      <g class="ruler">
        <line x1="0" :y1="-6" :x2="sheet.widthMm" :y2="-6" />
        <template v-for="tick in ticksX" :key="`x-${tick}`">
          <line :x1="tick" :y1="-6" :x2="tick" :y2="-12" />
          <text :x="tick" :y="-15" text-anchor="middle">{{ formatCm(tick, 0) }}</text>
        </template>
      </g>

      <!-- Thuoc do canh trai (so do tu duoi len, dung chieu voi ban in) -->
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

      <!-- Cac hinh da dat -->
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

      <!-- Vien kho, ve sau cung de luon nam tren -->
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
        vi tri {{ formatCm(hovered.xMm) }} ; {{ formatCm(hovered.yMm) }} cm
      </span>
      <span :class="hovered.rotated ? 'text-accent' : 'text-mute'">
        {{ hovered.rotated ? 'Da xoay 90 do' : 'Giu nguyen huong' }}
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
  /* Tam rat dai nen gioi han chieu cao va cho cuon, thay vi ep vao mot khung be ti. */
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
