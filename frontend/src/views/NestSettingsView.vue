<script setup lang="ts">
/** Bước 2 — nhập tham số khổ in rồi chạy ghép. */
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/layout/PageHeader.vue'
import AppStepper from '@/components/ui/AppStepper.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppCard from '@/components/ui/AppCard.vue'
import AppBadge from '@/components/ui/AppBadge.vue'
import SettingsForm from '@/components/nesting/SettingsForm.vue'
import { formatAreaCm2, formatCm, formatCount } from '@/api/units'
import { ApiError } from '@/api/client'
import { useNestingJobStore } from '@/stores/nestingJob'
import { useUiStore } from '@/stores/ui'

const router = useRouter()
const store = useNestingJobStore()
const ui = useUiStore()

const form = ref<InstanceType<typeof SettingsForm> | null>(null)
const canSubmit = computed(() => form.value?.isValid !== false && store.items.length > 0)

async function submit() {
  try {
    const jobId = await store.submit()
    router.push({ name: 'nest-result', params: { jobId } })
  } catch (error) {
    // Lỗi đồng bộ (ví dụ hình rộng hơn khổ) đã được store ghi vào job.error; vẫn báo một
    // toast để người dùng thấy ngay mà không phải đợi chuyển màn hình.
    ui.error(error instanceof ApiError ? error.message : 'Không gửi được yêu cầu ghép.')
  }
}
</script>

<template>
  <div class="stack gap-5">
    <AppStepper :current="2" />

    <PageHeader
      title="Tham số in"
      subtitle="Khai báo khổ cuộn và khoảng cách. Đây là những số quyết định số mét giấy phải dùng."
    >
      <template #actions>
        <AppButton variant="ghost" @click="router.push('/')">&larr; Về trang chủ</AppButton>
      </template>
    </PageHeader>

    <div class="layout">
      <SettingsForm ref="form" v-model="store.settings" />

      <!-- Bảng tóm tắt chỉ đọc: để thợ đối chiếu lại danh sách mà không phải lùi bước. -->
      <AppCard title="Danh sách sẽ ghép" :subtitle="`${formatCount(store.items.length)} loại hình`">
        <ul class="summary">
          <li v-for="item in store.items" :key="item.fileId" class="summary__row">
            <span class="summary__name truncate" :title="item.label">{{ item.label }}</span>
            <span class="summary__meta num">
              {{ formatCm(item.widthMm) }} x {{ formatCm(item.heightMm) }} cm
              <AppBadge tone="neutral">x{{ item.quantity }}</AppBadge>
              <AppBadge v-if="!item.allowRotate" tone="warn">khoá xoay</AppBadge>
            </span>
          </li>
        </ul>

        <div class="summary__total num">
          <span>Tổng bản in</span>
          <strong>{{ formatCount(store.totalQuantity) }}</strong>
        </div>
        <div class="summary__total num">
          <span>Diện tích hình</span>
          <strong>{{ formatAreaCm2(store.totalShapeAreaMm2) }} cm²</strong>
        </div>
      </AppCard>
    </div>

    <div class="actions">
      <AppButton variant="secondary" @click="router.push({ name: 'nest-upload' })">
        &larr; Quay lại
      </AppButton>
      <AppButton variant="primary" :disabled="!canSubmit" :loading="store.submitting" @click="submit">
        Ghép file
      </AppButton>
    </div>
  </div>
</template>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr);
  gap: var(--s-4);
  align-items: start;
}

@media (max-width: 900px) {
  .layout {
    grid-template-columns: 1fr;
  }
}

.summary {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  max-height: 320px;
  overflow-y: auto;
}

.summary__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--s-3);
  padding: var(--s-2) 0;
  border-bottom: 1px solid var(--c-border);
  font-size: var(--fs-sm);
}

.summary__name {
  max-width: 45%;
}

.summary__meta {
  display: flex;
  align-items: center;
  gap: var(--s-2);
  color: var(--c-text-soft);
  font-size: var(--fs-xs);
}

.summary__total {
  display: flex;
  justify-content: space-between;
  padding-top: var(--s-3);
  font-size: var(--fs-sm);
  color: var(--c-text-soft);
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--s-2);
}
</style>
