/**
 * Định tuyến.
 *
 * Màn Dashboard là cửa ngõ duy nhất; ba bước của wizard là ba route riêng để người dùng
 * bấm Back của trình duyệt vẫn đúng ý. Các bước sau CÓ CANH GÁC: vào thẳng
 * `/nest/settings` khi chưa có hình nào sẽ bị đẩy về bước 1, tránh màn hình trắng trơ.
 */
import { createRouter, createWebHistory } from 'vue-router'
import { useNestingJobStore } from '@/stores/nestingJob'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'dashboard',
      component: () => import('@/views/DashboardView.vue'),
      meta: { title: 'Công cụ sản xuất' },
    },
    {
      path: '/nest/upload',
      name: 'nest-upload',
      component: () => import('@/views/NestUploadView.vue'),
      meta: { title: 'Nạp file' },
    },
    {
      path: '/nest/settings',
      name: 'nest-settings',
      component: () => import('@/views/NestSettingsView.vue'),
      meta: { title: 'Tham số in', requiresItems: true },
    },
    {
      path: '/nest/result/:jobId',
      name: 'nest-result',
      component: () => import('@/views/NestResultView.vue'),
      props: true,
      meta: { title: 'Kết quả' },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { title: 'Không tìm thấy trang' },
    },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => {
  if (to.meta.requiresItems) {
    const store = useNestingJobStore()
    if (store.items.length === 0) {
      return { name: 'nest-upload' }
    }
  }
  return true
})

router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} - Xưởng in Minh Trí` : 'Xưởng in Minh Trí'
})

export default router
