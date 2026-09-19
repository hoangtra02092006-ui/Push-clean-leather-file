<script setup lang="ts">
/** Buoc 1 - nap file va khai bao so luong. */
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/layout/PageHeader.vue'
import AppStepper from '@/components/ui/AppStepper.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppEmptyState from '@/components/ui/AppEmptyState.vue'
import FileDropzone from '@/components/nesting/FileDropzone.vue'
import ItemTable from '@/components/nesting/ItemTable.vue'
import { formatAreaCm2, formatCount } from '@/api/units'
import { useNestingJobStore } from '@/stores/nestingJob'
import type { UploadedFile } from '@/types'

const router = useRouter()
const store = useNestingJobStore()

const summary = computed(
  () =>
    `Tong: ${formatCount(store.items.length)} loai hinh - ` +
    `${formatCount(store.totalQuantity)} ban - ` +
    `dien tich hinh ${formatAreaCm2(store.totalShapeAreaMm2)} cm2`,
)

function onUploaded(files: UploadedFile[]) {
  store.addFiles(files)
}

function next() {
  router.push({ name: 'nest-settings' })
}
</script>

<template>
  <div class="stack gap-5">
    <AppStepper :current="1" />

    <PageHeader title="Nap file in" subtitle="Chon cac file can in va khai bao so luong moi loai.">
      <template #actions>
        <AppButton variant="ghost" @click="router.push('/')">&larr; Ve trang chu</AppButton>
      </template>
    </PageHeader>

    <FileDropzone @uploaded="onUploaded" />

    <AppEmptyState
      v-if="store.items.length === 0"
      title="Chua co file nao"
      description="Keo tha file PDF, PNG hoac JPG vao khung phia tren. App se tu doc kich thuoc that cua tung file; neu doc sai, ban sua lai truc tiep trong bang."
    />

    <template v-else>
      <ItemTable
        :items="store.items"
        @remove="store.removeItem"
        @reset="store.resetItemSize"
      />

      <!-- Chan trang dinh: tho luon nhin thay tong so va nut di tiep du bang dai the nao. -->
      <div class="footbar">
        <span class="footbar__summary num">{{ summary }}</span>
        <AppButton variant="primary" :disabled="!store.canProceedToSettings" @click="next">
          Tiep tuc &rarr;
        </AppButton>
      </div>
    </template>
  </div>
</template>

<style scoped>
.footbar {
  position: sticky;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--s-4);
  flex-wrap: wrap;
  padding: var(--s-3) var(--s-4);
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  box-shadow: var(--sh-2);
}

.footbar__summary {
  color: var(--c-text-soft);
  font-size: var(--fs-sm);
}
</style>
