<script setup lang="ts">
/**
 * Số liệu tổng hợp của một lần ghép.
 *
 * Bốn ô số ở trên trả lời câu "cả đơn này tốn bao nhiêu mét". Bảng phía dưới trả lời câu
 * tiếp theo mà chủ xưởng luôn hỏi: "trong số mét đó, từng mẫu chiếm bao nhiêu" — để chia
 * tiền giấy cho từng khách, và để thấy mẫu nào đang ăn chỗ nhất.
 *
 * Chấm màu ở cột đầu khớp đúng màu hình vẽ trong phần xem trước bố trí, nên đọc bảng rồi
 * nhìn xuống hình là nhận ra ngay từng mẫu.
 */
import { computed } from 'vue'
import AppStatTile from '@/components/ui/AppStatTile.vue'
import AppCard from '@/components/ui/AppCard.vue'
import AppTable from '@/components/ui/AppTable.vue'
import { formatAreaCm2, formatCm, formatCount, formatPercent } from '@/api/units'
import { categoryColor } from '@/composables/useCategoryColor'
import type { NestStats } from '@/types'

const props = defineProps<{ stats: NestStats }>()

const totalLengthCm = computed(() => formatCm(props.stats.totalLengthMm, 1))
const fillPct = computed(() => formatPercent(props.stats.fillRate, 1))
const savedPct = computed(() => props.stats.savedVsIndividualPct.toFixed(1))

/** Bản cũ của backend chưa có trường này; thiếu thì ẩn bảng chứ không vỡ màn hình. */
const byType = computed(() => props.stats.byType ?? [])

const columns = [
  'Hình',
  { label: 'Kích thước (cm)', align: 'right' as const },
  { label: 'Số bản', align: 'right' as const },
  { label: 'Diện tích (cm²)', align: 'right' as const },
  { label: 'Trọng số', align: 'right' as const },
  { label: 'Lấp đầy', align: 'right' as const },
]

/** Phần giấy không hình nào phủ tới — chính là chỗ bị bỏ đi. */
const wastePct = computed(() => formatPercent(1 - props.stats.fillRate, 1))
</script>

<template>
  <div class="stack gap-4">
    <div class="tiles">
      <AppStatTile label="Số file xuất ra" :value="String(stats.totalSheets)" unit="file" />
      <AppStatTile label="Tổng chiều dài" :value="totalLengthCm" unit="cm" tone="accent" />
      <AppStatTile label="Tỷ lệ lấp đầy" :value="fillPct" unit="%" />
      <AppStatTile label="Tiết kiệm so với in rời" :value="savedPct" unit="%" tone="success" />
    </div>

    <AppCard
      v-if="byType.length > 0"
      title="Từng mẫu chiếm bao nhiêu"
      subtitle="Trọng số là phần của mẫu đó trong tổng diện tích hình; lấp đầy là phần nó phủ trên giấy."
    >
      <AppTable :columns="columns">
        <tr v-for="row in byType" :key="row.categoryIndex">
          <td>
            <span class="name">
              <span
                class="swatch"
                :style="{ background: categoryColor(row.categoryIndex) }"
                aria-hidden="true"
              />
              <span class="truncate" :title="row.label">{{ row.label }}</span>
            </span>
          </td>
          <td class="col-num">{{ formatCm(row.widthMm) }} x {{ formatCm(row.heightMm) }}</td>
          <td class="col-num">{{ formatCount(row.pieces) }}</td>
          <td class="col-num">{{ formatAreaCm2(row.shapeAreaMm2) }}</td>
          <td class="col-num">
            <span class="bar" aria-hidden="true">
              <span
                class="bar__fill"
                :style="{
                  width: `${Math.max(2, row.shareOfShapes * 100)}%`,
                  background: categoryColor(row.categoryIndex),
                }"
              />
            </span>
            {{ formatPercent(row.shareOfShapes, 1) }}%
          </td>
          <td class="col-num">{{ formatPercent(row.fillRate, 1) }}%</td>
        </tr>

        <!--
          Dòng chốt: cộng cột "lấp đầy" của mọi mẫu lại đúng bằng tỷ lệ lấp đầy chung, nên
          để phần giấy bỏ đi ngay cạnh thì người đọc tự đối chiếu được mà không phải tính.
        -->
        <tr class="waste">
          <td>Giấy bỏ đi</td>
          <td class="col-num">&mdash;</td>
          <td class="col-num">&mdash;</td>
          <td class="col-num">&mdash;</td>
          <td class="col-num">&mdash;</td>
          <td class="col-num">{{ wastePct }}%</td>
        </tr>
      </AppTable>
    </AppCard>
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

.name {
  display: flex;
  align-items: center;
  gap: var(--s-2);
  max-width: 260px;
  font-weight: 550;
}

.swatch {
  flex-shrink: 0;
  width: 10px;
  height: 10px;
  border-radius: 2px;
}

/* Thanh nhỏ sau con số: thấy ngay mẫu nào áp đảo mà không phải so từng chữ số. */
.bar {
  display: inline-block;
  width: 54px;
  height: 6px;
  margin-right: var(--s-2);
  border-radius: 999px;
  background: var(--c-bg);
  overflow: hidden;
  vertical-align: middle;
}

.bar__fill {
  display: block;
  height: 100%;
  border-radius: 999px;
}

.waste td {
  color: var(--c-text-mute);
}
</style>
