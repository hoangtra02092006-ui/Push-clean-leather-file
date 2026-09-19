<script setup lang="ts">
/**
 * Vung keo tha file.
 *
 * Nhan nhieu file cung luc, hien tien do tung file. Loi cua MOT file khong lam hong ca
 * me: file nao hong thi bao rieng file do, cac file con lai van vao bang.
 */
import { ref } from 'vue'
import { uploadFiles } from '@/api/files'
import { ApiError } from '@/api/client'
import AppButton from '@/components/ui/AppButton.vue'
import { useUiStore } from '@/stores/ui'
import type { UploadedFile } from '@/types'

const emit = defineEmits<{ uploaded: [files: UploadedFile[]] }>()

const ui = useUiStore()
const inputRef = ref<HTMLInputElement | null>(null)
const dragging = ref(false)
const uploading = ref(false)
const progress = ref<Array<{ name: string; percent: number; failed: boolean }>>([])

const ACCEPT = '.pdf,.png,.jpg,.jpeg'

function pickFiles() {
  inputRef.value?.click()
}

function onSelect(event: Event) {
  const input = event.target as HTMLInputElement
  if (input.files) {
    handleFiles(Array.from(input.files))
  }
  // Xoa gia tri de chon lai DUNG file vua roi van kich hoat su kien change.
  input.value = ''
}

function onDrop(event: DragEvent) {
  dragging.value = false
  const dropped = event.dataTransfer?.files
  if (dropped?.length) {
    handleFiles(Array.from(dropped))
  }
}

/** Tai lan luot tung file de con biet file nao hong ma bao dung ten. */
async function handleFiles(files: File[]) {
  if (files.length === 0) return

  uploading.value = true
  progress.value = files.map((file) => ({ name: file.name, percent: 0, failed: false }))
  const uploaded: UploadedFile[] = []

  for (let i = 0; i < files.length; i += 1) {
    try {
      const result = await uploadFiles([files[i]], (percent) => {
        progress.value[i].percent = percent
      })
      progress.value[i].percent = 100
      uploaded.push(...result)
    } catch (error) {
      progress.value[i].failed = true
      const message = error instanceof ApiError ? error.message : 'Khong tai duoc file.'
      ui.error(`${files[i].name}: ${message}`)
    }
  }

  if (uploaded.length > 0) {
    emit('uploaded', uploaded)
    ui.success(`Da nap ${uploaded.length} file.`)
  }

  uploading.value = false
  // Giu thanh tien do mot nhip cho nguoi dung kip thay roi don di.
  window.setTimeout(() => {
    progress.value = []
  }, 1200)
}
</script>

<template>
  <div>
    <div
      :class="['dropzone', { 'dropzone--active': dragging }]"
      @dragover.prevent="dragging = true"
      @dragleave.prevent="dragging = false"
      @drop.prevent="onDrop"
    >
      <span class="dropzone__icon" aria-hidden="true">
        <svg
          width="26"
          height="26"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M12 16V4" />
          <path d="m7 9 5-5 5 5" />
          <path d="M4 16v3a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-3" />
        </svg>
      </span>

      <p class="dropzone__title">Keo tha file in vao day</p>
      <p class="dropzone__hint">Nhan file PDF, PNG, JPG - chon duoc nhieu file cung luc.</p>

      <AppButton variant="secondary" :loading="uploading" @click="pickFiles">
        Chon file
      </AppButton>

      <input
        ref="inputRef"
        class="sr-only"
        type="file"
        multiple
        :accept="ACCEPT"
        @change="onSelect"
      />
    </div>

    <ul v-if="progress.length" class="progress">
      <li v-for="entry in progress" :key="entry.name" class="progress__row">
        <span class="progress__name truncate">{{ entry.name }}</span>
        <span class="progress__bar">
          <span
            :class="['progress__fill', { 'progress__fill--failed': entry.failed }]"
            :style="{ width: `${entry.failed ? 100 : entry.percent}%` }"
          />
        </span>
        <span class="progress__pct num">{{ entry.failed ? 'Loi' : `${entry.percent}%` }}</span>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.dropzone {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--s-2);
  width: 100%;
  padding: var(--s-7) var(--s-5);
  background: var(--c-surface);
  border: 2px dashed var(--c-border-strong);
  border-radius: var(--r-md);
  text-align: center;
  transition: border-color var(--t-fast), background var(--t-fast);
}

.dropzone--active {
  border-color: var(--c-accent);
  background: var(--c-accent-soft);
}

.dropzone__icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--c-bg);
  color: var(--c-accent);
}

.dropzone__title {
  font-size: var(--fs-lg);
  font-weight: 600;
}

.dropzone__hint {
  margin-bottom: var(--s-2);
  color: var(--c-text-soft);
  font-size: var(--fs-sm);
}

.progress {
  list-style: none;
  margin: var(--s-3) 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--s-2);
}

.progress__row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 140px 48px;
  align-items: center;
  gap: var(--s-3);
  font-size: var(--fs-sm);
}

.progress__name {
  color: var(--c-text-soft);
}

.progress__bar {
  height: 6px;
  border-radius: 999px;
  background: var(--c-border);
  overflow: hidden;
}

.progress__fill {
  display: block;
  height: 100%;
  background: var(--c-accent);
  transition: width var(--t-base);
}

.progress__fill--failed {
  background: var(--c-danger);
}

.progress__pct {
  text-align: right;
  color: var(--c-text-mute);
  font-size: var(--fs-xs);
}

@media (max-width: 768px) {
  .progress__row {
    grid-template-columns: minmax(0, 1fr) 60px;
  }

  .progress__bar {
    display: none;
  }
}
</style>
