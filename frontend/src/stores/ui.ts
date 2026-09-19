/** Store cho cac thanh phan giao dien dung chung (hien tai la toast). */
import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ToastKind = 'success' | 'error' | 'info'

export interface Toast {
  id: number
  kind: ToastKind
  message: string
}

/** Toast tu an sau khoang thoi gian nay. */
const AUTO_DISMISS_MS = 4000

export const useUiStore = defineStore('ui', () => {
  const toasts = ref<Toast[]>([])
  let nextId = 1

  /** Hien mot toast o goc tren phai; tu an sau 4 giay. */
  function notify(message: string, kind: ToastKind = 'info') {
    const id = nextId++
    toasts.value.push({ id, kind, message })
    window.setTimeout(() => dismiss(id), AUTO_DISMISS_MS)
  }

  function success(message: string) {
    notify(message, 'success')
  }

  function error(message: string) {
    notify(message, 'error')
  }

  function dismiss(id: number) {
    toasts.value = toasts.value.filter((toast) => toast.id !== id)
  }

  return { toasts, notify, success, error, dismiss }
})
