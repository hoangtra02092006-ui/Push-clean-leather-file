<script setup lang="ts">
/**
 * Thanh buoc cua wizard.
 *
 * Buoc DA QUA bam quay lai duoc; buoc CHUA TOI bi khoa. Khoa bang `disabled` that chu
 * khong chi lam mo, de bam nham khong nhay sang man hinh thieu du lieu.
 */
import { useRouter } from 'vue-router'

const props = defineProps<{ current: number }>()
const router = useRouter()

const steps = [
  { index: 1, label: 'Nap file', route: 'nest-upload' },
  { index: 2, label: 'Tham so in', route: 'nest-settings' },
  { index: 3, label: 'Ket qua', route: null },
]

function go(step: (typeof steps)[number]) {
  if (step.index >= props.current || !step.route) return
  router.push({ name: step.route })
}
</script>

<template>
  <nav class="stepper" aria-label="Cac buoc ghep file">
    <button
      v-for="step in steps"
      :key="step.index"
      type="button"
      :class="[
        'stepper__step',
        {
          'stepper__step--active': step.index === current,
          'stepper__step--done': step.index < current,
        },
      ]"
      :disabled="step.index >= current || !step.route"
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
