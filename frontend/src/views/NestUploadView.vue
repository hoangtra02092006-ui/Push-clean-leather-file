<script setup lang="ts">
/** Bước 1 — nạp file và khai báo số lượng. */
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
    `Tổng: ${formatCount(store.items.length)} loại hình · ` +
    `${formatCount(store.totalQuantity)} bản · ` +
    `diện tích hình ${formatAreaCm2(store.totalShapeAreaMm2)} cm²`,
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

    <PageHeader title="Nạp file in" subtitle="Chọn các file cần in và khai báo số lượng mỗi loại.">
      <template #actions>
        <AppButton variant="ghost" @click="router.push('/')">&larr; Về trang chủ</AppButton>
      </template>
    </PageHeader>

    <FileDropzone @uploaded="onUploaded" />

    <AppEmptyState
      v-if="store.items.length === 0"
      title="Chưa có file nào"
      description="Kéo thả file PDF, PNG hoặc JPG vào khung phía trên. App sẽ tự đọc kích thước thật của từng file; nếu đọc sai, bạn sửa lại trực tiếp trong bảng."
    />

    <template v-else>
      <ItemTable
        :items="store.items"
        @remove="store.removeItem"
        @reset="store.resetItemSize"
      />

      <!-- Chân trang dính: thợ luôn nhìn thấy tổng số và nút đi tiếp dù bảng dài thế nào. -->
      <div class="footbar">
        <span class="footbar__summary num">{{ summary }}</span>
        <AppButton variant="primary" :disabled="!store.canProceedToSettings" @click="next">
          Tiếp tục &rarr;
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
