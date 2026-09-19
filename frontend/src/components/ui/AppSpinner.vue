<script setup lang="ts">
/** Vong quay cho. Bat buoc dung o moi trang thai dang tai - khong de man hinh trang tro. */
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
      :aria-label="label || 'Dang tai'"
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

/* Ton trong nguoi dung da tat hieu ung chuyen dong trong he dieu hanh. */
@media (prefers-reduced-motion: reduce) {
  .spinner {
    animation-duration: 2s;
  }
}
</style>
