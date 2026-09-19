<script setup lang="ts">
/** Tieu de trang: ten + phu de mot dong, cho phep gan hanh dong ben phai. */
defineProps<{ title: string; subtitle?: string }>()
</script>

<template>
  <div class="page-header">
    <div>
      <h1>{{ title }}</h1>
      <p v-if="subtitle" class="page-header__sub">{{ subtitle }}</p>
    </div>
    <div v-if="$slots.actions" class="page-header__actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--s-4);
  flex-wrap: wrap;
  margin-bottom: var(--s-5);
}

.page-header__sub {
  margin-top: var(--s-1);
  color: var(--c-text-soft);
}

.page-header__actions {
  display: flex;
  gap: var(--s-2);
}
</style>
