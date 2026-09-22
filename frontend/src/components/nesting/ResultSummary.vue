<script setup lang="ts">
/**
 * Số liệu tổng hợp của một lần ghép.
 *
 * Bốn ô số ở trên trả lời câu "cả đơn này tốn bao nhiêu mét". Bảng phía dưới trả lời câu
 * tiếp theo mà chủ xưởng luôn hỏi: "trong số mét đó, từng mẫu ăn mấy mét" — để chia tiền
 * giấy cho từng khách.
 *
 * Cộng cột chiều dài của mọi mẫu lại đúng bằng tổng chiều dài, vì phần giấy bỏ đi đã
 * được chia đều vào đầu từng mẫu theo tỷ lệ. Không mẫu nào một mình gây ra chỗ trống nên
 * cũng không mẫu nào phải gánh riêng.
 *
 * Chấm màu ở cột đầu khớp đúng màu hình vẽ trong phần xem trước bố trí, nên đọc bảng rồi
 * nhìn xuống hình là nhận ra ngay từng mẫu.
 */
import { computed } from 'vue'
import AppStatTile from '@/components/ui/AppStatTile.vue'
import AppCard from '@/components/ui/AppCard.vue'
import AppTable from '@/components/ui/AppTable.vue'
import FilePreview from '@/components/nesting/FilePreview.vue'
import { filePreviewUrl } from '@/api/files'
import { formatAreaCm2, formatCm, formatCount, formatM, formatPercent } from '@/api/units'
import { categoryColor } from '@/composables/useCategoryColor'
import type { NestStats } from '@/types'

const props = defineProps<{ stats: NestStats }>()

const totalLengthCm = computed(() => formatCm(props.stats.totalLengthMm, 1))
const fillPct = computed(() => formatPercent(props.stats.fillRate, 1))
const savedPct = computed(() => props.stats.savedVsIndividualPct.toFixed(1))

/** Bản cũ của backend chưa có trường này; thiếu thì ẩn bảng chứ không vỡ màn hình. */
const byType = computed(() => props.stats.byType ?? [])

const columns = [
  'Xem trước',
  'Hình',
  { label: 'Kích thước (cm)', align: 'right' as const },
  { label: 'Số bản', align: 'right' as const },
  { label: 'Diện tích (cm²)', align: 'right' as const },
  { label: 'Chiều dài (cm)', align: 'right' as const },
  { label: 'Chiều dài (m)', align: 'right' as const },
]
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
      title="Từng mẫu ăn mấy mét"
      subtitle="Chiều dài thực tế đã tính cả phần giấy bỏ đi, chia đều theo tỷ lệ. Cộng lại đúng bằng tổng chiều dài."
    >
      <AppTable :columns="columns">
        <tr v-for="row in byType" :key="row.categoryIndex">
          <td>
            <FilePreview :src="filePreviewUrl(row.fileId)" :label="row.label">
              <template #caption>
                {{ formatCm(row.widthMm) }} x {{ formatCm(row.heightMm) }} cm ·
                {{ formatCount(row.pieces) }} bản · {{ formatM(row.lengthMm) }} m
              </template>
            </FilePreview>
          </td>
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
          <td class="col-num">{{ formatCm(row.lengthMm, 1) }}</td>
          <td class="col-num">{{ formatM(row.lengthMm) }}</td>
        </tr>

        <!--
          Dòng tổng: để ngay dưới cho thợ tự đối chiếu cột chiều dài cộng lại có khớp tổng
          không, thay vì phải tin suông.
        -->
        <tr class="total">
          <td>&mdash;</td>
          <td>Tổng</td>
          <td class="col-num">&mdash;</td>
          <td class="col-num">{{ formatCount(stats.totalPieces) }}</td>
          <td class="col-num">{{ formatAreaCm2(stats.totalShapeAreaMm2) }}</td>
          <td class="col-num">{{ formatCm(stats.totalLengthMm, 1) }}</td>
          <td class="col-num">{{ formatM(stats.totalLengthMm) }}</td>
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

.total td {
  font-weight: 600;
}
</style>
