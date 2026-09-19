<script setup lang="ts">
/**
 * Trang thai rong / loi.
 *
 * BAT BUOC dung o moi cho co the khong co du lieu. Mot khung trang tro khien nguoi dung
 * tuong app hong; mot dong huong dan thi ho biet phai lam gi tiep.
 */
withDefaults(defineProps<{ title: string; description?: string; variant?: 'empty' | 'error' }>(), {
  variant: 'empty',
})
</script>

<template>
  <div :class="['empty', `empty--${variant}`]">
    <div class="empty__icon" aria-hidden="true">
      <svg
        v-if="variant === 'error'"
        width="28"
        height="28"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <circle cx="12" cy="12" r="9" />
        <path d="M12 8v4" />
        <path d="M12 16h.01" />
      </svg>
      <svg
        v-else
        width="28"
        height="28"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="1.8"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <rect x="3" y="3" width="7" height="7" rx="1" />
        <rect x="14" y="3" width="7" height="7" rx="1" />
        <rect x="14" y="14" width="7" height="7" rx="1" />
        <rect x="3" y="14" width="7" height="7" rx="1" />
      </svg>
    </div>
    <h3 class="empty__title">{{ title }}</h3>
    <p v-if="description" class="empty__desc">{{ description }}</p>
    <div v-if="$slots.default" class="empty__actions">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: var(--s-2);
  padding: var(--s-7) var(--s-5);
  border: 1px dashed var(--c-border-strong);
  border-radius: var(--r-md);
  background: var(--c-surface);
}

.empty__icon {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: var(--c-bg);
  color: var(--c-text-mute);
  margin-bottom: var(--s-1);
}

.empty--error {
  border-color: var(--c-danger);
  border-style: solid;
  background: var(--c-danger-soft);
}

.empty--error .empty__icon {
  background: var(--c-surface);
  color: var(--c-danger);
}

.empty__title {
  font-size: var(--fs-lg);
  font-weight: 600;
}

.empty__desc {
  max-width: 46ch;
  color: var(--c-text-soft);
  font-size: var(--fs-sm);
}

.empty__actions {
  margin-top: var(--s-3);
  display: flex;
  gap: var(--s-2);
}
</style>
