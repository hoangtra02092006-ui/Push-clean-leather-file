<script setup lang="ts">
/**
 * Buoc 3 - ket qua.
 *
 * Man nay co ba trang thai ro rang va khong trang thai nao de man hinh trang:
 * dang chay (spinner + dong trang thai), that bai (AppEmptyState the loi, neu ro nguyen
 * nhan), va xong (so lieu + bang + preview).
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

const sheets = computed(() => store.result?.sheets ?? [])
const currentSheet = computed(() => sheets.value[activeSheet.value] ?? null)

onMounted(() => {
  // Vao thang bang URL (hoac tai lai trang) thi store chua co gi - phai doc lai job.
  if (!store.job || store.job.jobId !== props.jobId) {
    store.loadJob(props.jobId)
  }
})

onUnmounted(() => store.stopPolling())

// Ket qua moi ve thi luon hien tam dau tien.
watch(sheets, () => {
  activeSheet.value = 0
})

/**
 * Che do demo khong dung duoc PDF that (khong co PDFBox tren trinh duyet), nen chan lai
 * va noi ro ly do thay vi de nguoi dung bam vao mot link hong.
 */
function guardDownload(event: Event) {
  if (USE_MOCK) {
    event.preventDefault()
    ui.notify(
      'Che do demo khong xuat duoc PDF that. Hay chay backend va tat VITE_USE_MOCK de tai file.',
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

    <PageHeader title="Ket qua ghep" subtitle="Kiem tra bo tri roi tai file PDF gui may in.">
      <template #actions>
        <AppButton variant="ghost" @click="router.push('/')">&larr; Ve trang chu</AppButton>
      </template>
    </PageHeader>

    <!-- Dang chay -->
    <AppCard v-if="store.isRunning" class="running">
      <div class="running__inner">
        <AppSpinner :size="28" />
        <div>
          <p class="font-semibold">Dang tinh toan bo tri...</p>
          <p class="text-soft text-sm">
            App dang thu hang tram phuong an xep de tim cach ton it giay nhat. Viec nay thuong
            mat vai giay.
          </p>
        </div>
      </div>
    </AppCard>

    <!-- That bai -->
    <AppEmptyState
      v-else-if="store.error"
      variant="error"
      title="Khong ghep duoc"
      :description="store.error.message"
    >
      <AppButton variant="primary" @click="retry">Sua tham so va thu lai</AppButton>
    </AppEmptyState>

    <!-- Xong -->
    <template v-else-if="store.result">
      <ResultSummary :stats="store.result.stats" />

      <AppCard title="Chi tiet tung file">
        <AppTable :columns="['STT', 'Kich thuoc (cm)', 'So hinh', 'Lap day', '']">
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
                Tai PDF
              </a>
            </td>
          </tr>
        </AppTable>
      </AppCard>

      <AppCard title="Xem truoc bo tri" subtitle="Di chuot len tung hinh de xem ten file va kich thuoc.">
        <template #actions>
          <div v-if="sheets.length > 1" class="tabs">
            <button
              v-for="sheet in sheets"
              :key="sheet.index"
              type="button"
              :class="['tabs__btn', { 'tabs__btn--active': sheet.index === activeSheet }]"
              @click="activeSheet = sheet.index"
            >
              Tam {{ sheet.index + 1 }}
            </button>
          </div>
        </template>

        <SheetPreview v-if="currentSheet" :sheet="currentSheet" />
      </AppCard>

      <div class="actions">
        <AppButton variant="secondary" @click="retry">Ghep lai voi tham so khac</AppButton>
        <a
          class="btn-link"
          :href="exportZipUrl(jobId)"
          target="_blank"
          rel="noopener"
          @click="guardDownload"
        >
          Tai tat ca (.zip)
        </a>
      </div>
    </template>

    <!-- Chua co gi (dang doc lai job) -->
    <AppCard v-else>
      <AppSpinner :size="20" label="Dang doc ket qua..." />
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

/* Nut chinh cua man nay la mot the <a> vi no tai file that, khong phai goi JS. */
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
