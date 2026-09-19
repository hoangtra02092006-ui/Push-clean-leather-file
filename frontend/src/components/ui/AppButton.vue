<script setup lang="ts">
/**
 * Nut bam dung chung.
 *
 * QUY UOC: moi man hinh chi duoc co DUY NHAT mot nut `primary`. Nhieu nut dam cung luc
 * lam nguoi dung khong biet hanh dong chinh la gi.
 */
import AppSpinner from './AppSpinner.vue'

withDefaults(
  defineProps<{
    variant?: 'primary' | 'secondary' | 'ghost'
    size?: 'md' | 'sm'
    type?: 'button' | 'submit'
    disabled?: boolean
    loading?: boolean
    block?: boolean
  }>(),
  {
    variant: 'secondary',
    size: 'md',
    type: 'button',
    disabled: false,
    loading: false,
    block: false,
  },
)
</script>

<template>
  <button
    :type="type"
    :class="['btn', `btn--${variant}`, `btn--${size}`, { 'btn--block': block }]"
    :disabled="disabled || loading"
  >
    <AppSpinner v-if="loading" :size="14" inline />
    <slot />
  </button>
</template>

<style scoped>
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--s-2);
  height: var(--control-height);
  padding: 0 var(--s-4);
  border: 1px solid transparent;
  border-radius: var(--r-sm);
  font-size: var(--fs-base);
  font-weight: 550;
  cursor: pointer;
  white-space: nowrap;
  transition: background var(--t-fast), border-color var(--t-fast), color var(--t-fast);
}

.btn--sm {
  height: 30px;
  padding: 0 var(--s-3);
  font-size: var(--fs-sm);
}

.btn--block {
  width: 100%;
}

.btn:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.btn--primary {
  background: var(--c-accent);
  color: #fff;
}

.btn--primary:hover:not(:disabled) {
  background: var(--c-accent-hover);
}

.btn--secondary {
  background: var(--c-surface);
  border-color: var(--c-border-strong);
  color: var(--c-text);
}

.btn--secondary:hover:not(:disabled) {
  background: var(--c-bg);
  border-color: var(--c-text-mute);
}

.btn--ghost {
  background: transparent;
  color: var(--c-text-soft);
}

.btn--ghost:hover:not(:disabled) {
  background: var(--c-bg);
  color: var(--c-text);
}
</style>
