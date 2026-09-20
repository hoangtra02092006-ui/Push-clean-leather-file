<script setup lang="ts">
/** Khối nội dung có viền. Phân cấp bằng viền và nền, không dùng đổ bóng nặng. */
defineProps<{ title?: string; subtitle?: string }>()
</script>

<template>
  <section class="card">
    <header v-if="title || $slots.actions" class="card__head">
      <div>
        <h3 v-if="title" class="card__title">{{ title }}</h3>
        <p v-if="subtitle" class="card__subtitle">{{ subtitle }}</p>
      </div>
      <slot name="actions" />
    </header>
    <slot />
  </section>
</template>

<style scoped>
.card {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  padding: var(--s-5);
}

.card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--s-4);
  margin-bottom: var(--s-4);
}

.card__title {
  font-size: var(--fs-lg);
  font-weight: 600;
}

.card__subtitle {
  margin-top: var(--s-1);
  color: var(--c-text-soft);
  font-size: var(--fs-sm);
}
</style>
