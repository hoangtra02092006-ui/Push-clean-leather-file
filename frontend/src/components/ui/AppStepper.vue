<script setup lang="ts">
/**
 * Thanh bước của wizard.
 *
 * Một bước bấm được khi nó đã có đủ dữ liệu để hiển thị, chứ không phải khi nó nằm
 * trước bước hiện tại. Nhờ vậy sau khi có kết quả, thợ nhảy tự do giữa bước 1, 2 và 3
 * để sửa số lượng hay tham số rồi quay lại xem kết quả cũ mà không mất gì.
 *
 * Bước chưa đủ dữ liệu vẫn bị khoá bằng `disabled` thật chứ không chỉ làm mờ, để bấm
 * nhầm không nhảy sang màn hình trống.
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useNestingJobStore } from '@/stores/nestingJob'

const props = defineProps<{ current: number }>()
const router = useRouter()
const store = useNestingJobStore()

const steps = computed(() => [
  { index: 1, label: 'Nạp file', to: { name: 'nest-upload' }, open: true },
  {
    index: 2,
    label: 'Tham số in',
    to: { name: 'nest-settings' },
    open: store.items.length > 0,
  },
  {
    index: 3,
    label: 'Kết quả',
    to: { name: 'nest-result', params: { jobId: store.jobId ?? '' } },
    // Chỉ mở khi đã thực sự chạy một job; nếu không, route sẽ thiếu jobId.
    open: store.jobId !== null,
  },
])

type Step = (typeof steps.value)[number]

function enabled(step: Step): boolean {
  return step.open && step.index !== props.current
}

function go(step: Step) {
  if (!enabled(step)) return
  router.push(step.to)
}
</script>

<template>
  <nav class="stepper" aria-label="Các bước ghép file">
    <button
      v-for="step in steps"
      :key="step.index"
      type="button"
      :class="[
        'stepper__step',
        {
          'stepper__step--active': step.index === current,
          'stepper__step--done': step.index !== current && step.open,
        },
      ]"
      :disabled="!enabled(step)"
      :aria-current="step.index === current ? 'step' : undefined"
      @click="go(step)"
    >
      <span class="stepper__num">{{ step.index }}</span>
      <span>{{ step.label }}</span>
    </button>
  </nav>
</template>

<style scoped>
.stepper {
  display: flex;
  align-items: center;
  gap: var(--s-2);
  flex-wrap: wrap;
}

.stepper__step {
  display: inline-flex;
  align-items: center;
  gap: var(--s-2);
  padding: var(--s-2) var(--s-3);
  border: 1px solid var(--c-border);
  border-radius: 999px;
  background: var(--c-surface);
  color: var(--c-text-mute);
  font-size: var(--fs-sm);
  cursor: default;
}

.stepper__step--done {
  color: var(--c-text-soft);
  cursor: pointer;
}

.stepper__step--done:hover {
  border-color: var(--c-accent);
  color: var(--c-accent);
}

.stepper__step--active {
  border-color: var(--c-accent);
  background: var(--c-accent-soft);
  color: var(--c-accent);
  font-weight: 550;
}

.stepper__num {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--c-bg);
  font-size: var(--fs-xs);
  font-weight: 600;
}

.stepper__step--active .stepper__num {
  background: var(--c-accent);
  color: #fff;
}

.stepper__step--done .stepper__num {
  background: var(--c-success-soft);
  color: var(--c-success);
}
</style>
