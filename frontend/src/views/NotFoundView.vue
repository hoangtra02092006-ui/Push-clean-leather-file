<script setup lang="ts">
/** Trang 404. */
import { useRouter } from 'vue-router'
import AppEmptyState from '@/components/ui/AppEmptyState.vue'
import AppButton from '@/components/ui/AppButton.vue'

const router = useRouter()
</script>

<template>
  <AppEmptyState
    title="Không tìm thấy trang"
    description="Đường dẫn bạn vừa mở không tồn tại. Hãy quay về trang chủ để chọn chức năng."
  >
    <AppButton variant="primary" @click="router.push('/')">Về trang chủ</AppButton>
  </AppEmptyState>
</template>
