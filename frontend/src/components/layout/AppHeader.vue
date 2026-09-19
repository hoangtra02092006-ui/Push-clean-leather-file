<script setup lang="ts">
/** Thanh dau trang: logo + ten xuong ben trai, nut phu ben phai. */
import { RouterLink } from 'vue-router'
import { USE_MOCK } from '@/api/client'
import AppBadge from '@/components/ui/AppBadge.vue'
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()

function showHelp() {
  ui.notify(
    'Quy trinh: nap file in -> nhap kho ngang, le, khoang cach -> bam Ghep file -> tai PDF ve gui may in.',
    'info',
  )
}
</script>

<template>
  <header class="header">
    <div class="header__inner">
      <RouterLink to="/" class="brand">
        <span class="brand__mark" aria-hidden="true">
          <svg
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <rect x="3" y="3" width="7" height="7" rx="1" />
            <rect x="14" y="3" width="7" height="7" rx="1" />
            <rect x="14" y="14" width="7" height="7" rx="1" />
            <rect x="3" y="14" width="7" height="7" rx="1" />
          </svg>
        </span>
        <span class="brand__text">
          <strong>PrintNest</strong>
          <span class="brand__sub">Xuong in Hoang Gia</span>
        </span>
      </RouterLink>

      <div class="header__right">
        <AppBadge v-if="USE_MOCK" tone="warn">Che do demo</AppBadge>
        <button type="button" class="header__link" @click="showHelp">Tro giup</button>
      </div>
    </div>
  </header>
</template>

<style scoped>
.header {
  position: sticky;
  top: 0;
  z-index: 20;
  background: var(--c-surface);
  border-bottom: 1px solid var(--c-border);
}

.header__inner {
  max-width: var(--layout-max);
  margin: 0 auto;
  padding: var(--s-3) var(--s-4);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--s-4);
}

.brand {
  display: flex;
  align-items: center;
  gap: var(--s-3);
  color: var(--c-text);
  text-decoration: none;
}

.brand:hover {
  text-decoration: none;
}

.brand__mark {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: var(--r-sm);
  background: var(--c-accent-soft);
  color: var(--c-accent);
}

.brand__text strong {
  display: block;
  font-size: var(--fs-lg);
  font-weight: 650;
  letter-spacing: -0.01em;
  line-height: 1.2;
}

.brand__sub {
  display: block;
  font-size: var(--fs-xs);
  color: var(--c-text-mute);
}

.header__right {
  display: flex;
  align-items: center;
  gap: var(--s-3);
}

.header__link {
  border: 0;
  background: none;
  color: var(--c-text-soft);
  font-size: var(--fs-sm);
  cursor: pointer;
}

.header__link:hover {
  color: var(--c-accent);
}
</style>
