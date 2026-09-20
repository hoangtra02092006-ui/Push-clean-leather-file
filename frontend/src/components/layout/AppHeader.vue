<script setup lang="ts">
/** Thanh đầu trang: logo + tên xưởng bên trái, nút phụ bên phải. */
import { RouterLink } from 'vue-router'
import logoUrl from '@/assets/brand/logo.png'
import { USE_MOCK } from '@/api/client'
import AppBadge from '@/components/ui/AppBadge.vue'
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()

function showHelp() {
  ui.notify(
    'Quy trình: nạp file in → nhập khổ ngang, lề, khoảng cách → bấm Ghép file → tải PDF về gửi máy in.',
    'info',
  )
}
</script>

<template>
  <header class="header">
    <div class="header__inner">
      <RouterLink to="/" class="brand">
        <img class="brand__mark" :src="logoUrl" alt="" width="34" height="34" />
        <span class="brand__text">
          <strong>Xưởng in Minh Trí</strong>
          <span class="brand__sub">Ghép file in tiết kiệm khổ</span>
        </span>
      </RouterLink>

      <div class="header__right">
        <AppBadge v-if="USE_MOCK" tone="warn">Chế độ demo</AppBadge>
        <button type="button" class="header__link" @click="showHelp">Trợ giúp</button>
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

/* Logo la dia tron san; bo tron them de vien khong bao gio lo goc vuong. */
.brand__mark {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  object-fit: contain;
  flex-shrink: 0;
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
