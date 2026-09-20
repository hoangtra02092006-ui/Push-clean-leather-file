<script setup lang="ts">
/** Vòng quay chờ. Bắt buộc dùng ở mọi trạng thái đang tải — không để màn hình trắng trơ. */
withDefaults(defineProps<{ size?: number; label?: string; inline?: boolean }>(), {
  size: 20,
  label: '',
  inline: false,
})
</script>

<template>
  <span :class="['spinner-wrap', { 'spinner-wrap--inline': inline }]">
    <span
      class="spinner"
      :style="{ width: `${size}px`, height: `${size}px` }"
      role="status"
      :aria-label="label || 'Đang tải'"
    />
    <span v-if="label" class="text-soft text-sm">{{ label }}</span>
  </span>
</template>

<style scoped>
.spinner-wrap {
  display: inline-flex;
  align-items: center;
  gap: var(--s-2);
}

.spinner {
  display: inline-block;
  border: 2px solid var(--c-border-strong);
  border-top-color: var(--c-accent);
  border-radius: 50%;
  animation: spin 700ms linear infinite;
}

.spinner-wrap--inline .spinner {
  border-top-color: currentColor;
  border-color: rgba(255, 255, 255, 0.4);
  border-top-color: #fff;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* Tôn trọng người dùng đã tắt hiệu ứng chuyển động trong hệ điều hành. */
@media (prefers-reduced-motion: reduce) {
  .spinner {
    animation-duration: 2s;
  }
}
</style>
