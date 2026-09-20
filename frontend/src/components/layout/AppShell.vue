<script setup lang="ts">
/** Khung chung của mọi trang: header dính, vùng nội dung giới hạn bề ngang, toast. */
import AppHeader from './AppHeader.vue'
import AppToast from '@/components/ui/AppToast.vue'
</script>

<template>
  <div class="shell">
    <AppHeader />
    <main class="shell__main">
      <slot />
    </main>
    <AppToast />
  </div>
</template>

<style scoped>
.shell {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

.shell__main {
  flex: 1;
  width: 100%;
  max-width: var(--layout-max);
  margin: 0 auto;
  /* Gutter 16px ở màn hình hẹp theo đúng quy ước trong design system. */
  padding: var(--s-6) var(--s-4) var(--s-8);
}
</style>
