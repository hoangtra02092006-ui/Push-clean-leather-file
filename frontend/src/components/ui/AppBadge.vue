<script setup lang="ts">
/** Nhãn nhỏ hiện trạng thái. */
withDefaults(defineProps<{ tone?: 'neutral' | 'accent' | 'success' | 'warn' | 'danger' }>(), {
  tone: 'neutral',
})
</script>

<template>
  <span :class="['badge', `badge--${tone}`]"><slot /></span>
</template>

<style scoped>
.badge {
  display: inline-flex;
  align-items: center;
  padding: 2px var(--s-2);
  border-radius: 999px;
  font-size: var(--fs-xs);
  font-weight: 550;
  line-height: 1.6;
  white-space: nowrap;
}

.badge--neutral {
  background: var(--c-bg);
  color: var(--c-text-soft);
  border: 1px solid var(--c-border);
}

.badge--accent {
  background: var(--c-accent-soft);
  color: var(--c-accent);
}

.badge--success {
  background: var(--c-success-soft);
  color: var(--c-success);
}

.badge--warn {
  background: var(--c-warn-soft);
  color: var(--c-warn);
}

.badge--danger {
  background: var(--c-danger-soft);
  color: var(--c-danger);
}
</style>
