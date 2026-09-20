<script setup lang="ts">
/** Hàng toast ở góc trên phải. Đặt một lần trong AppShell, dùng cho cả app. */
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()
</script>

<template>
  <div class="toasts" role="status" aria-live="polite">
    <TransitionGroup name="toast">
      <div v-for="toast in ui.toasts" :key="toast.id" :class="['toast', `toast--${toast.kind}`]">
        <span class="toast__text">{{ toast.message }}</span>
        <button type="button" class="toast__close" aria-label="Đóng" @click="ui.dismiss(toast.id)">
          &times;
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toasts {
  position: fixed;
  top: var(--s-4);
  right: var(--s-4);
  z-index: 100;
  display: flex;
  flex-direction: column;
  gap: var(--s-2);
  max-width: min(380px, calc(100vw - var(--s-6)));
}

.toast {
  display: flex;
  align-items: flex-start;
  gap: var(--s-3);
  padding: var(--s-3) var(--s-4);
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-left: 3px solid var(--c-text-mute);
  border-radius: var(--r-sm);
  box-shadow: var(--sh-2);
  font-size: var(--fs-sm);
}

.toast--success {
  border-left-color: var(--c-success);
}

.toast--error {
  border-left-color: var(--c-danger);
}

.toast--info {
  border-left-color: var(--c-accent);
}

.toast__text {
  flex: 1;
}

.toast__close {
  border: 0;
  background: none;
  color: var(--c-text-mute);
  font-size: var(--fs-lg);
  line-height: 1;
  cursor: pointer;
}

.toast__close:hover {
  color: var(--c-text);
}

.toast-enter-active,
.toast-leave-active {
  transition: opacity var(--t-base), transform var(--t-base);
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateX(12px);
}
</style>
