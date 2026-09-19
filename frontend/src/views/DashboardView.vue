<script setup lang="ts">
/**
 * Man quan ly chung - cua ngo duy nhat cua app.
 *
 * Vao app la thay man nay; phai bam vao mot o chuc nang moi di tiep. Cac o "Sap co" duoc
 * de san chu khong an di, de nguoi dung thay huong phat trien va khong di tim chuc nang
 * o noi khac.
 */
import { useRouter } from 'vue-router'
import PageHeader from '@/components/layout/PageHeader.vue'
import AppBadge from '@/components/ui/AppBadge.vue'
import { useNestingJobStore } from '@/stores/nestingJob'

type IconName = 'layout-grid' | 'folder' | 'history' | 'sliders' | 'bar-chart'

interface FeatureCard {
  key: string
  title: string
  description: string
  icon: IconName
  available: boolean
  route?: string
}

const router = useRouter()
const store = useNestingJobStore()

const features: FeatureCard[] = [
  {
    key: 'nest',
    title: 'Ghep file in',
    description: 'Tu dan khuon nhieu file len kho cuon sao cho ton it met nhat.',
    icon: 'layout-grid',
    available: true,
    route: 'nest-upload',
  },
  {
    key: 'library',
    title: 'Thu vien file',
    description: 'Luu cac mau in hay dung de khong phai tai lai moi lan.',
    icon: 'folder',
    available: false,
  },
  {
    key: 'history',
    title: 'Lich su ghep',
    description: 'Xem lai cac lan ghep truoc va tai lai file da xuat.',
    icon: 'history',
    available: false,
  },
  {
    key: 'preset',
    title: 'Preset kho in',
    description: 'Luu san bo tham so cho tung loai cuon va tung may in.',
    icon: 'sliders',
    available: false,
  },
  {
    key: 'report',
    title: 'Bao cao tiet kiem',
    description: 'Thong ke so met va so tien da tiet kiem theo thang.',
    icon: 'bar-chart',
    available: false,
  },
]

/** Bat dau luong moi: xoa du lieu cu de khong lan sang don truoc. */
function open(card: FeatureCard) {
  if (!card.available || !card.route) return
  store.clearAll()
  router.push({ name: card.route })
}
</script>

<template>
  <div>
    <PageHeader
      title="Cong cu san xuat"
      subtitle="Chon mot chuc nang de bat dau. Hien tai chuc nang ghep file da san sang dung."
    />

    <div class="grid">
      <component
        :is="card.available ? 'button' : 'div'"
        v-for="card in features"
        :key="card.key"
        :type="card.available ? 'button' : undefined"
        :class="['card', card.available ? 'card--live' : 'card--soon']"
        :disabled="card.available ? false : undefined"
        :aria-disabled="card.available ? undefined : 'true'"
        @click="open(card)"
      >
        <span class="card__icon" aria-hidden="true">
          <!-- Icon ve tay theo bo Lucide: SVG inline, net stroke, 24px. Khong nap font
               icon de khong them mot request va mot phu thuoc chi de ve 5 hinh. -->
          <svg
            width="24"
            height="24"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <template v-if="card.icon === 'layout-grid'">
              <rect x="3" y="3" width="7" height="7" rx="1" />
              <rect x="14" y="3" width="7" height="7" rx="1" />
              <rect x="14" y="14" width="7" height="7" rx="1" />
              <rect x="3" y="14" width="7" height="7" rx="1" />
            </template>
            <template v-else-if="card.icon === 'folder'">
              <path d="M4 20h16a1 1 0 0 0 1-1V8a1 1 0 0 0-1-1h-7.5L10 4H4a1 1 0 0 0-1 1v14a1 1 0 0 0 1 1Z" />
            </template>
            <template v-else-if="card.icon === 'history'">
              <path d="M3 12a9 9 0 1 0 3-6.7L3 8" />
              <path d="M3 3v5h5" />
              <path d="M12 7v5l3 2" />
            </template>
            <template v-else-if="card.icon === 'sliders'">
              <path d="M4 6h16" />
              <path d="M4 12h16" />
              <path d="M4 18h16" />
              <circle cx="9" cy="6" r="2" />
              <circle cx="15" cy="12" r="2" />
              <circle cx="8" cy="18" r="2" />
            </template>
            <template v-else>
              <path d="M3 21h18" />
              <rect x="5" y="11" width="3.5" height="7" rx="1" />
              <rect x="10.25" y="6" width="3.5" height="12" rx="1" />
              <rect x="15.5" y="14" width="3.5" height="4" rx="1" />
            </template>
          </svg>
        </span>

        <span class="card__body">
          <span class="card__title">{{ card.title }}</span>
          <span class="card__desc">{{ card.description }}</span>
        </span>

        <span class="card__foot">
          <AppBadge v-if="!card.available" tone="neutral">Sap co</AppBadge>
          <AppBadge v-else tone="success">Hoat dong</AppBadge>
          <span v-if="card.available" class="card__arrow" aria-hidden="true">&rarr;</span>
        </span>
      </component>
    </div>
  </div>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--s-4);
}

@media (max-width: 1024px) {
  .grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .grid {
    grid-template-columns: 1fr;
  }
}

.card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--s-3);
  min-height: 172px;
  padding: var(--s-5);
  border-radius: var(--r-md);
  text-align: left;
  font: inherit;
  transition: border-color var(--t-fast), transform var(--t-fast), box-shadow var(--t-fast);
}

/* O dang hoat dong: vien dam hon mot bac, hover nhac len 2px. */
.card--live {
  background: var(--c-surface);
  border: 1px solid var(--c-border-strong);
  color: var(--c-text);
  cursor: pointer;
}

.card--live:hover {
  border-color: var(--c-accent);
  transform: translateY(-2px);
  box-shadow: var(--sh-2);
}

/* O chua lam: nen xam, chu mo, con tro bao khong bam duoc. */
.card--soon {
  background: var(--c-bg);
  border: 1px dashed var(--c-border);
  color: var(--c-text-mute);
  cursor: not-allowed;
}

.card__icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: var(--r-sm);
  background: var(--c-bg);
  color: var(--c-text-soft);
}

.card--live .card__icon {
  background: var(--c-accent-soft);
  color: var(--c-accent);
}

.card__body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--s-1);
}

.card__title {
  font-size: var(--fs-lg);
  font-weight: 600;
}

.card__desc {
  font-size: var(--fs-sm);
  color: inherit;
  opacity: 0.85;
}

.card--live .card__desc {
  color: var(--c-text-soft);
  opacity: 1;
}

.card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.card__arrow {
  color: var(--c-accent);
  font-size: var(--fs-lg);
}

@media (prefers-reduced-motion: reduce) {
  .card--live:hover {
    transform: none;
  }
}
</style>
