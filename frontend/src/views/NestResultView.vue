<script setup lang="ts">
/**
 * Bước 3 — kết quả.
 *
 * Màn này có ba trạng thái rõ ràng và không trạng thái nào để màn hình trắng:
 * đang chạy (spinner + dòng trạng thái), thất bại (AppEmptyState thể lỗi, nêu rõ nguyên
 * nhân), và xong (số liệu + bảng + preview).
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/layout/PageHeader.vue'
import AppStepper from '@/components/ui/AppStepper.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppCard from '@/components/ui/AppCard.vue'
import AppSpinner from '@/components/ui/AppSpinner.vue'
import AppEmptyState from '@/components/ui/AppEmptyState.vue'
import AppTable from '@/components/ui/AppTable.vue'
import ResultSummary from '@/components/nesting/ResultSummary.vue'
import SheetPreview from '@/components/nesting/SheetPreview.vue'
import { exportZipUrl, sheetPdfUrl } from '@/api/nesting'
import { USE_MOCK } from '@/api/client'
import { formatCm, formatPercent } from '@/api/units'
import { useNestingJobStore } from '@/stores/nestingJob'
import { useUiStore } from '@/stores/ui'

const props = defineProps<{ jobId: string }>()

const router = useRouter()
const store = useNestingJobStore()
const ui = useUiStore()

const activeSheet = ref(0)

/** Cột của bảng chi tiết: bốn cột đầu là số nên căn phải cho thẳng với dữ liệu. */
const sheetColumns = [
  { label: 'STT', align: 'right' as const },
  { label: 'Kích thước (cm)', align: 'right' as const },
  { label: 'Số hình', align: 'right' as const },
  { label: 'Lấp đầy', align: 'right' as const },
  { label: '', align: 'right' as const },
]

const sheets = computed(() => store.result?.sheets ?? [])
const currentSheet = computed(() => sheets.value[activeSheet.value] ?? null)

onMounted(() => {
  // Vào thẳng bằng URL (hoặc tải lại trang) thì store chưa có gì — phải đọc lại job.
  if (!store.job || store.job.jobId !== props.jobId) {
    store.loadJob(props.jobId)
  }
})

onUnmounted(() => store.stopPolling())

// Kết quả mới về thì luôn hiện tấm đầu tiên.
watch(sheets, () => {
  activeSheet.value = 0
})

/**
 * Chế độ demo không dựng được PDF thật (không có PDFBox trên trình duyệt), nên chặn lại
 * và nói rõ lý do thay vì để người dùng bấm vào một link hỏng.
 */
function guardDownload(event: Event) {
  if (USE_MOCK) {
    event.preventDefault()
    ui.notify(
      'Chế độ demo không xuất được PDF thật. Hãy chạy backend và tắt VITE_USE_MOCK để tải file.',
      'info',
    )
  }
}

function retry() {
  router.push({ name: 'nest-settings' })
}
</script>

<template>
  <div class="stack gap-5">
    <AppStepper :current="3" />

    <PageHeader title="Kết quả ghép" subtitle="Kiểm tra bố trí rồi tải file PDF gửi máy in.">
      <template #actions>
        <AppButton variant="ghost" @click="router.push('/')">&larr; Về trang chủ</AppButton>
      </template>
    </PageHeader>

    <!-- Đang chạy -->
    <AppCard v-if="store.isRunning" class="running">
      <div class="running__inner">
        <AppSpinner :size="28" />
        <div>
          <p class="font-semibold">Đang tính toán bố trí…</p>
          <p class="text-soft text-sm">
            App đang thử hàng trăm phương án xếp để tìm cách tốn ít giấy nhất. Việc này thường
            mất vài giây.
          </p>
        </div>
      </div>
    </AppCard>

    <!-- Thất bại -->
    <AppEmptyState
      v-else-if="store.error"
      variant="error"
      title="Không ghép được"
      :description="store.error.message"
    >
      <AppButton variant="primary" @click="retry">Sửa tham số và thử lại</AppButton>
    </AppEmptyState>

    <!-- Xong -->
    <template v-else-if="store.result">
      <ResultSummary :stats="store.result.stats" />

      <AppCard title="Chi tiết từng file">
        <AppTable :columns="sheetColumns">
          <tr v-for="sheet in sheets" :key="sheet.index">
            <td class="col-num">{{ sheet.index + 1 }}</td>
            <td class="col-num">
              {{ formatCm(sheet.widthMm) }} x {{ formatCm(sheet.lengthMm) }}
            </td>
            <td class="col-num">{{ sheet.placements.length }}</td>
            <td class="col-num">{{ formatPercent(sheet.fillRate) }}%</td>
            <td class="text-right">
              <a
                class="download"
                :href="sheetPdfUrl(jobId, sheet.index)"
                target="_blank"
                rel="noopener"
                @click="guardDownload"
              >
                Tải PDF
              </a>
            </td>
          </tr>
        </AppTable>
      </AppCard>

      <AppCard title="Xem trước bố trí" subtitle="Di chuột lên từng hình để xem tên file và kích thước.">
        <template #actions>
          <div v-if="sheets.length > 1" class="tabs">
            <button
              v-for="sheet in sheets"
              :key="sheet.index"
              type="button"
              :class="['tabs__btn', { 'tabs__btn--active': sheet.index === activeSheet }]"
              @click="activeSheet = sheet.index"
            >
              Tấm {{ sheet.index + 1 }}
            </button>
          </div>
        </template>

        <SheetPreview v-if="currentSheet" :sheet="currentSheet" />
      </AppCard>

      <div class="actions">
        <AppButton variant="secondary" @click="retry">Ghép lại với tham số khác</AppButton>
        <a
          class="btn-link"
          :href="exportZipUrl(jobId)"
          target="_blank"
          rel="noopener"
          @click="guardDownload"
        >
          Tải tất cả (.zip)
        </a>
      </div>
    </template>

    <!-- Chưa có gì (đang đọc lại job) -->
    <AppCard v-else>
      <AppSpinner :size="20" label="Đang đọc kết quả…" />
    </AppCard>
  </div>
</template>

<style scoped>
.running__inner {
  display: flex;
  align-items: center;
  gap: var(--s-4);
}

.tabs {
  display: flex;
  gap: var(--s-1);
  flex-wrap: wrap;
}

.tabs__btn {
  padding: var(--s-1) var(--s-3);
  border: 1px solid var(--c-border);
  border-radius: var(--r-sm);
  background: var(--c-surface);
  color: var(--c-text-soft);
  font-size: var(--fs-sm);
  cursor: pointer;
}

.tabs__btn--active {
  border-color: var(--c-accent);
  background: var(--c-accent-soft);
  color: var(--c-accent);
  font-weight: 550;
}

.download {
  font-size: var(--fs-sm);
  font-weight: 550;
}

.actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: var(--s-2);
}

/* Nút chính của màn này là một thẻ <a> vì nó tải file thật, không phải gọi JS. */
.btn-link {
  display: inline-flex;
  align-items: center;
  height: var(--control-height);
  padding: 0 var(--s-4);
  border-radius: var(--r-sm);
  background: var(--c-accent);
  color: #fff;
  font-size: var(--fs-base);
  font-weight: 550;
  text-decoration: none;
  transition: background var(--t-fast);
}

.btn-link:hover {
  background: var(--c-accent-hover);
  text-decoration: none;
}
</style>
