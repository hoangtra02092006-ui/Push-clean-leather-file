<script setup lang="ts">
/**
 * Hàng ô số liệu tổng hợp của một lần ghép.
 *
 * Bốn con số được chọn đúng cái thợ và chủ xưởng cần biết: in ra mấy file, tốn bao nhiêu
 * mét, lấp đầy được bao nhiêu, và tiết kiệm được bao nhiêu so với cách in rời cũ.
 */
import { computed } from 'vue'
import AppStatTile from '@/components/ui/AppStatTile.vue'
import { formatCm, formatPercent } from '@/api/units'
import type { NestStats } from '@/types'

const props = defineProps<{ stats: NestStats }>()

const totalLengthCm = computed(() => formatCm(props.stats.totalLengthMm, 1))
const fillPct = computed(() => formatPercent(props.stats.fillRate, 1))
const savedPct = computed(() => props.stats.savedVsIndividualPct.toFixed(1))
</script>

<template>
  <div class="tiles">
    <AppStatTile label="Số file xuất ra" :value="String(stats.totalSheets)" unit="file" />
    <AppStatTile label="Tổng chiều dài" :value="totalLengthCm" unit="cm" tone="accent" />
    <AppStatTile label="Tỷ lệ lấp đầy" :value="fillPct" unit="%" />
    <AppStatTile label="Tiết kiệm so với in rời" :value="savedPct" unit="%" tone="success" />
  </div>
</template>

<style scoped>
.tiles {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--s-3);
}

@media (max-width: 900px) {
  .tiles {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
