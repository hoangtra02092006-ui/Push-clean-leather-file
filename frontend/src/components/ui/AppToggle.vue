<script setup lang="ts">
/** Công tắc bật/tắt. Dùng thẻ <button role="switch"> để bộ đọc màn hình hiểu đúng. */
import { useId } from '@/composables/useId'

defineProps<{ label?: string; hint?: string; disabled?: boolean }>()
const model = defineModel<boolean>({ default: false })
const id = useId('toggle')
</script>

<template>
  <div class="toggle-row">
    <button
      :id="id"
      type="button"
      role="switch"
      :aria-checked="model"
      :aria-label="label"
      :disabled="disabled"
      :class="['toggle', { 'toggle--on': model }]"
      @click="model = !model"
    >
      <span class="toggle__knob" />
    </button>
    <label v-if="label" class="toggle__label" :for="id">
      {{ label }}
      <span v-if="hint" class="toggle__hint">{{ hint }}</span>
    </label>
  </div>
</template>

<style scoped>
.toggle-row {
  display: flex;
  align-items: center;
  gap: var(--s-3);
}

.toggle {
  flex-shrink: 0;
  width: 38px;
  height: 22px;
  padding: 2px;
  border: 1px solid var(--c-border-strong);
  border-radius: 999px;
  background: var(--c-bg);
  cursor: pointer;
  transition: background var(--t-fast), border-color var(--t-fast);
}

.toggle--on {
  background: var(--c-accent);
  border-color: var(--c-accent);
}

.toggle:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.toggle__knob {
  display: block;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--c-surface);
  box-shadow: var(--sh-1);
  transition: transform var(--t-fast);
}

.toggle--on .toggle__knob {
  transform: translateX(16px);
}

.toggle__label {
  font-size: var(--fs-sm);
  color: var(--c-text);
  cursor: pointer;
}

.toggle__hint {
  display: block;
  color: var(--c-text-mute);
  font-size: var(--fs-xs);
}
</style>
