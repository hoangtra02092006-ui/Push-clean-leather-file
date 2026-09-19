<script setup lang="ts">
/**
 * Hang o so lieu tong hop cua mot lan ghep.
 *
 * Bon con so duoc chon dung cai tho va chu xuong can biet: in ra may file, ton bao nhieu
 * met, lap day duoc bao nhieu, va tiet kiem duoc bao nhieu so voi cach in roi cu.
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
    <AppStatTile label="So file xuat ra" :value="String(stats.totalSheets)" unit="file" />
    <AppStatTile label="Tong chieu dai" :value="totalLengthCm" unit="cm" tone="accent" />
    <AppStatTile label="Ty le lap day" :value="fillPct" unit="%" />
    <AppStatTile label="Tiet kiem so voi in roi" :value="savedPct" unit="%" tone="success" />
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
