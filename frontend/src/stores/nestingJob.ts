/**
 * Store trung tam cua luong ghep file.
 *
 * Giu toan bo du lieu wizard 3 buoc: danh sach hinh (buoc 1), tham so in (buoc 2), va
 * trang thai job (buoc 3). Dat chung mot cho de quay lai buoc truoc khong mat du lieu -
 * yeu cau ro rang cua nut "Ghep lai voi tham so khac".
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { createJob, getJob } from '@/api/nesting'
import { ApiError } from '@/api/client'
import type { ApiErrorBody, Job, NestItem, NestResult, NestSettings, UploadedFile } from '@/types'

/** Tham so mac dinh, khop voi thoi quen cua xuong. */
const DEFAULT_SETTINGS: NestSettings = {
  sheetWidthMm: 570,
  marginMm: 5,
  gapMm: 3,
  maxSheetLengthMm: 2000,
  allowRotateGlobal: true,
  drawCutLines: true,
}

/** Khoang cach giua hai lan hoi trang thai job. */
const POLL_INTERVAL_MS = 700

export const useNestingJobStore = defineStore('nestingJob', () => {
  const items = ref<NestItem[]>([])
  const settings = ref<NestSettings>({ ...DEFAULT_SETTINGS })

  const jobId = ref<string | null>(null)
  const job = ref<Job | null>(null)
  const submitting = ref(false)

  let pollTimer: number | null = null

  // --- Dan xuat ---

  /** Tong so ban in cua tat ca cac dong. */
  const totalQuantity = computed(() => items.value.reduce((sum, item) => sum + item.quantity, 0))

  /** Tong dien tich hinh, don vi mm2. */
  const totalShapeAreaMm2 = computed(() =>
    items.value.reduce((sum, item) => sum + item.widthMm * item.heightMm * item.quantity, 0),
  )

  /** Da du dieu kien sang buoc 2 chua. */
  const canProceedToSettings = computed(
    () => items.value.length > 0 && items.value.every((item) => item.widthMm > 0 && item.heightMm > 0),
  )

  const result = computed<NestResult | null>(() => job.value?.result ?? null)
  const isRunning = computed(
    () => job.value?.status === 'PENDING' || job.value?.status === 'RUNNING',
  )
  const error = computed<ApiErrorBody | null>(() => job.value?.error ?? null)

  // --- Thao tac tren danh sach hinh ---

  /** Them cac file vua tai len vao bang, mac dinh so luong 1 va cho xoay. */
  function addFiles(files: UploadedFile[]) {
    for (const file of files) {
      items.value.push({
        fileId: file.id,
        label: file.originalName,
        originalWidthMm: file.widthMm,
        originalHeightMm: file.heightMm,
        widthMm: file.widthMm,
        heightMm: file.heightMm,
        quantity: 1,
        allowRotate: true,
        previewUrl: file.previewUrl,
        type: file.type,
      })
    }
  }

  function removeItem(fileId: string) {
    items.value = items.value.filter((item) => item.fileId !== fileId)
  }

  /** Tra kich thuoc mot dong ve dung gia tri doc duoc tu file. */
  function resetItemSize(fileId: string) {
    const item = items.value.find((entry) => entry.fileId === fileId)
    if (item) {
      item.widthMm = item.originalWidthMm
      item.heightMm = item.originalHeightMm
    }
  }

  function clearAll() {
    stopPolling()
    items.value = []
    settings.value = { ...DEFAULT_SETTINGS }
    jobId.value = null
    job.value = null
  }

  // --- Chay job ---

  /**
   * Gui yeu cau ghep va bat dau hoi trang thai.
   *
   * @returns ma job de router dieu huong sang buoc 3
   */
  async function submit(): Promise<string> {
    submitting.value = true
    stopPolling()
    job.value = null

    try {
      const created = await createJob({
        ...settings.value,
        items: items.value.map((item) => ({
          fileId: item.fileId,
          quantity: item.quantity,
          allowRotate: item.allowRotate,
          widthMm: item.widthMm,
          heightMm: item.heightMm,
        })),
      })
      jobId.value = created.jobId
      job.value = created
      startPolling(created.jobId)
      return created.jobId
    } catch (caught) {
      // Loi dong bo (vi du hinh rong hon kho) duoc dung thanh mot job FAILED gia lap,
      // nho vay man ket qua chi co MOT cho de hien loi thay vi hai duong xu ly.
      const body: ApiErrorBody =
        caught instanceof ApiError
          ? { code: caught.code, message: caught.message, details: caught.details }
          : { code: 'INTERNAL_ERROR', message: 'Khong gui duoc yeu cau ghep.' }
      job.value = { jobId: 'local-error', status: 'FAILED', progress: 0, error: body }
      jobId.value = 'local-error'
      throw caught
    } finally {
      submitting.value = false
    }
  }

  /** Doc lai trang thai mot job da co (dung khi nguoi dung tai lai trang ket qua). */
  async function loadJob(id: string) {
    jobId.value = id
    try {
      job.value = await getJob(id)
      if (job.value.status === 'PENDING' || job.value.status === 'RUNNING') {
        startPolling(id)
      }
    } catch (caught) {
      const body: ApiErrorBody =
        caught instanceof ApiError
          ? { code: caught.code, message: caught.message }
          : { code: 'JOB_NOT_FOUND', message: 'Khong tim thay lan ghep nay.' }
      job.value = { jobId: id, status: 'FAILED', progress: 0, error: body }
    }
  }

  function startPolling(id: string) {
    stopPolling()
    pollTimer = window.setInterval(async () => {
      try {
        const latest = await getJob(id)
        job.value = latest
        if (latest.status === 'DONE' || latest.status === 'FAILED') {
          stopPolling()
        }
      } catch {
        // Mot lan hoi that bai khong co nghia la job hong (co the chi la mang chap chon).
        // Cu de vong lap tiep tuc; nguoi dung van co nut thu lai.
      }
    }, POLL_INTERVAL_MS)
  }

  function stopPolling() {
    if (pollTimer !== null) {
      window.clearInterval(pollTimer)
      pollTimer = null
    }
  }

  return {
    items,
    settings,
    jobId,
    job,
    submitting,
    totalQuantity,
    totalShapeAreaMm2,
    canProceedToSettings,
    result,
    isRunning,
    error,
    addFiles,
    removeItem,
    resetItemSize,
    clearAll,
    submit,
    loadJob,
    stopPolling,
  }
})
