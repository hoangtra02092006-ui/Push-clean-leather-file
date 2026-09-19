<script setup lang="ts">
/** Trang 404. */
import { useRouter } from 'vue-router'
import AppEmptyState from '@/components/ui/AppEmptyState.vue'
import AppButton from '@/components/ui/AppButton.vue'

const router = useRouter()
</script>

<template>
  <AppEmptyState
    title="Khong tim thay trang"
    description="Duong dan ban vua mo khong ton tai. Hay quay ve trang chu de chon chuc nang."
  >
    <AppButton variant="primary" @click="router.push('/')">Ve trang chu</AppButton>
  </AppEmptyState>
</template>
