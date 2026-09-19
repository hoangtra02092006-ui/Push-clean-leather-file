<script setup lang="ts">
/** Buoc 2 - nhap tham so kho in roi chay ghep. */
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
    // Loi dong bo (vi du hinh rong hon kho) da duoc store ghi vao job.error; van bao mot
    // toast de nguoi dung thay ngay ma khong phai doi chuyen man hinh.
    ui.error(error instanceof ApiError ? error.message : 'Khong gui duoc yeu cau ghep.')
  }
}
</script>

<template>
  <div class="stack gap-5">
    <AppStepper :current="2" />

    <PageHeader
      title="Tham so in"
      subtitle="Khai bao kho cuon va khoang cach. Day la nhung so quyet dinh so met giay phai dung."
    >
      <template #actions>
        <AppButton variant="ghost" @click="router.push('/')">&larr; Ve trang chu</AppButton>
      </template>
    </PageHeader>

    <div class="layout">
      <SettingsForm ref="form" v-model="store.settings" />

      <!-- Bang tom tat chi doc: de tho doi chieu lai danh sach ma khong phai lui buoc. -->
      <AppCard title="Danh sach se ghep" :subtitle="`${formatCount(store.items.length)} loai hinh`">
        <ul class="summary">
          <li v-for="item in store.items" :key="item.fileId" class="summary__row">
            <span class="summary__name truncate" :title="item.label">{{ item.label }}</span>
            <span class="summary__meta num">
              {{ formatCm(item.widthMm) }} x {{ formatCm(item.heightMm) }} cm
              <AppBadge tone="neutral">x{{ item.quantity }}</AppBadge>
              <AppBadge v-if="!item.allowRotate" tone="warn">khoa xoay</AppBadge>
            </span>
          </li>
        </ul>

        <div class="summary__total num">
          <span>Tong ban in</span>
          <strong>{{ formatCount(store.totalQuantity) }}</strong>
        </div>
        <div class="summary__total num">
          <span>Dien tich hinh</span>
          <strong>{{ formatAreaCm2(store.totalShapeAreaMm2) }} cm2</strong>
        </div>
      </AppCard>
    </div>

    <div class="actions">
      <AppButton variant="secondary" @click="router.push({ name: 'nest-upload' })">
        &larr; Quay lai
      </AppButton>
      <AppButton variant="primary" :disabled="!canSubmit" :loading="store.submitting" @click="submit">
        Ghep file
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
