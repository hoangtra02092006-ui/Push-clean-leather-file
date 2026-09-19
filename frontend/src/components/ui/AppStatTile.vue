<script setup lang="ts">
/** O so lieu: nhan nho o tren, con so to o duoi, don vi nho hon con so. */
defineProps<{ label: string; value: string; unit?: string; tone?: 'default' | 'accent' | 'success' }>()
</script>

<template>
  <div :class="['tile', tone ? `tile--${tone}` : 'tile--default']">
    <span class="tile__label">{{ label }}</span>
    <span class="tile__value num">
      {{ value }}<span v-if="unit" class="tile__unit">{{ unit }}</span>
    </span>
  </div>
</template>

<style scoped>
.tile {
  display: flex;
  flex-direction: column;
  gap: var(--s-1);
  padding: var(--s-4);
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
}

.tile__label {
  font-size: var(--fs-xs);
  color: var(--c-text-mute);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.tile__value {
  font-size: var(--fs-2xl);
  font-weight: 620;
  line-height: 1.2;
  color: var(--c-text);
}

.tile__unit {
  margin-left: 3px;
  font-size: var(--fs-base);
  font-weight: 500;
  color: var(--c-text-soft);
}

.tile--accent .tile__value {
  color: var(--c-accent);
}

.tile--success .tile__value {
  color: var(--c-success);
}
</style>
