/**
 * Dinh tuyen.
 *
 * Man Dashboard la cua ngo duy nhat; ba buoc cua wizard la ba route rieng de nguoi dung
 * bam Back cua trinh duyet van dung y. Cac buoc sau CO CANH GAC: vao thang
 * `/nest/settings` khi chua co hinh nao se bi day ve buoc 1, tranh man hinh trang tro.
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
      meta: { title: 'Cong cu san xuat' },
    },
    {
      path: '/nest/upload',
      name: 'nest-upload',
      component: () => import('@/views/NestUploadView.vue'),
      meta: { title: 'Nap file' },
    },
    {
      path: '/nest/settings',
      name: 'nest-settings',
      component: () => import('@/views/NestSettingsView.vue'),
      meta: { title: 'Tham so in', requiresItems: true },
    },
    {
      path: '/nest/result/:jobId',
      name: 'nest-result',
      component: () => import('@/views/NestResultView.vue'),
      props: true,
      meta: { title: 'Ket qua' },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { title: 'Khong tim thay trang' },
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
  document.title = title ? `${title} - PrintNest` : 'PrintNest'
})

export default router
