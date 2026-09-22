<script setup lang="ts">
/**
 * Ô ảnh xem trước của một file, bấm vào mở to giữa màn hình.
 *
 * Tách riêng vì cả bước 1 (bảng danh sách hình) lẫn bước 3 (bảng từng mẫu ăn mấy mét)
 * đều cần đúng thứ này. Chép thành hai bản là cách chắc chắn nhất để vài lần sửa nữa
 * hai bên lệch nhau — sửa cỡ ảnh ở một chỗ, quên chỗ kia.
 *
 * Phần chú thích dưới ảnh to do nơi gọi truyền vào qua slot, vì bước 1 muốn nói về khổ
 * gốc đã cắt trắng, còn bước 3 muốn nói về số bản và số mét.
 */
import { onBeforeUnmount, onMounted, ref } from 'vue'

defineProps<{
  /** Đường dẫn ảnh. Rỗng nghĩa là không có ảnh để hiện. */
  src: string
  /** Tên file, dùng cho tooltip và cho bộ đọc màn hình. */
  label: string
}>()

const zoomed = ref(false)

/**
 * Ảnh hỏng thì ẩn hẳn ô đi thay vì để trình duyệt vẽ icon ảnh vỡ.
 *
 * Chuyện này xảy ra thật: file bị dọn sau 48 giờ, nhưng người dùng vẫn đang mở màn hình
 * kết quả cũ. Lúc đó không có ảnh là đúng, không phải lỗi cần báo.
 */
const broken = ref(false)

// Nghe Esc ở cấp cửa sổ: bắt trên chính thẻ <div> chỉ chạy khi nó đang được focus, mà
// người dùng vừa bấm chuột vào ảnh nên focus vẫn nằm ở nút thu nhỏ.
function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    zoomed.value = false
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <button
    v-if="src && !broken"
    type="button"
    class="thumb"
    :title="`Bấm để xem to ${label}`"
    @click="zoomed = true"
  >
    <img
      :src="src"
      :alt="`Xem trước ${label}`"
      loading="lazy"
      @error="broken = true"
    />
  </button>
  <span v-else class="thumb thumb--empty" aria-hidden="true">&mdash;</span>

  <div
    v-if="zoomed"
    class="zoom"
    role="dialog"
    aria-modal="true"
    :aria-label="`Xem trước ${label}`"
    @click.self="zoomed = false"
  >
    <div class="zoom__panel">
      <div class="zoom__head">
        <strong class="truncate" :title="label">{{ label }}</strong>
        <button type="button" class="zoom__close" aria-label="Đóng" @click="zoomed = false">
          &times;
        </button>
      </div>

      <div class="zoom__stage">
        <img :src="src" :alt="`Xem trước ${label}`" />
      </div>

      <p v-if="$slots.caption" class="zoom__foot num">
        <slot name="caption" />
      </p>
    </div>
  </div>
</template>

<style scoped>
.thumb {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  padding: 0;
  border: 1px solid var(--c-border);
  border-radius: var(--r-sm);
  background: var(--c-bg);
  overflow: hidden;
  cursor: zoom-in;
  transition: border-color var(--t-fast);
}

.thumb:hover {
  border-color: var(--c-accent);
}

.thumb img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.thumb--empty {
  color: var(--c-text-mute);
  cursor: default;
}

.zoom {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: grid;
  place-items: center;
  padding: var(--s-5);
  background: rgb(15 23 42 / 55%);
}

.zoom__panel {
  display: flex;
  flex-direction: column;
  gap: var(--s-3);
  max-width: min(680px, 90vw);
  padding: var(--s-4);
  background: var(--c-surface);
  border-radius: var(--r-md);
  box-shadow: var(--sh-2);
}

.zoom__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--s-3);
}

.zoom__close {
  border: none;
  background: none;
  color: var(--c-text-soft);
  font-size: var(--fs-xl);
  line-height: 1;
  cursor: pointer;
}

/* Nền ô carô để thấy rõ đâu là phần trong suốt, đâu là nét trắng của hình. */
.zoom__stage {
  display: grid;
  place-items: center;
  padding: var(--s-3);
  border: 1px solid var(--c-border);
  border-radius: var(--r-sm);
  background-color: var(--c-bg);
  background-image:
    linear-gradient(45deg, var(--c-border) 25%, transparent 25%),
    linear-gradient(-45deg, var(--c-border) 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, var(--c-border) 75%),
    linear-gradient(-45deg, transparent 75%, var(--c-border) 75%);
  background-size: 14px 14px;
  background-position: 0 0, 0 7px, 7px -7px, -7px 0;
}

.zoom__stage img {
  max-width: 100%;
  max-height: 60vh;
  object-fit: contain;
}

.zoom__foot {
  margin: 0;
  font-size: var(--fs-sm);
  color: var(--c-text-soft);
}
</style>
