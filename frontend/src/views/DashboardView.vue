<script setup lang="ts">
/**
 * Màn quản lý chung — cửa ngõ duy nhất của app.
 *
 * Vào app là thấy màn này; phải bấm vào một ô chức năng mới đi tiếp. Các ô "Sắp có" được
 * để sẵn chứ không ẩn đi, để người dùng thấy hướng phát triển và không đi tìm chức năng
 * ở nơi khác.
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
    title: 'Ghép file in',
    description: 'Tự dàn khuôn nhiều file lên khổ cuộn sao cho tốn ít mét nhất.',
    icon: 'layout-grid',
    available: true,
    route: 'nest-upload',
  },
  {
    key: 'library',
    title: 'Thư viện file',
    description: 'Lưu các mẫu in hay dùng để không phải tải lại mỗi lần.',
    icon: 'folder',
    available: false,
  },
  {
    key: 'history',
    title: 'Lịch sử ghép',
    description: 'Xem lại các lần ghép trước và tải lại file đã xuất.',
    icon: 'history',
    available: false,
  },
  {
    key: 'preset',
    title: 'Preset khổ in',
    description: 'Lưu sẵn bộ tham số cho từng loại cuộn và từng máy in.',
    icon: 'sliders',
    available: false,
  },
  {
    key: 'report',
    title: 'Báo cáo tiết kiệm',
    description: 'Thống kê số mét và số tiền đã tiết kiệm theo tháng.',
    icon: 'bar-chart',
    available: false,
  },
]

/** Bắt đầu luồng mới: xoá dữ liệu cũ để không lẫn sang đơn trước. */
function open(card: FeatureCard) {
  if (!card.available || !card.route) return
  store.clearAll()
  router.push({ name: card.route })
}
</script>

<template>
  <div>
    <PageHeader
      title="Công cụ sản xuất"
      subtitle="Chọn một chức năng để bắt đầu. Hiện tại chức năng ghép file đã sẵn sàng dùng."
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
          <!-- Icon vẽ tay theo bộ Lucide: SVG inline, nét stroke, 24px. Không nạp font
               icon để không thêm một request và một phụ thuộc chỉ để vẽ 5 hình. -->
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
          <AppBadge v-if="!card.available" tone="neutral">Sắp có</AppBadge>
          <AppBadge v-else tone="success">Hoạt động</AppBadge>
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

/* Ô đang hoạt động: viền đậm hơn một bậc, hover nhấc lên 2px. */
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

/* Ô chưa làm: nền xám, chữ mờ, con trỏ báo không bấm được. */
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
