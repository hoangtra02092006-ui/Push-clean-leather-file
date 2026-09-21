/**
 * Store trung tâm của luồng ghép file.
 *
 * Giữ toàn bộ dữ liệu wizard 3 bước: danh sách hình (bước 1), tham số in (bước 2), và
 * trạng thái job (bước 3). Đặt chung một chỗ để quay lại bước trước không mất dữ liệu —
 * yêu cầu rõ ràng của nút "Ghép lại với tham số khác".
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { createJob, getJob } from '@/api/nesting'
import { ApiError } from '@/api/client'
import type { ApiErrorBody, Job, NestItem, NestResult, NestSettings, UploadedFile } from '@/types'

/** Tham số mặc định, khớp với thói quen của xưởng. */
const DEFAULT_SETTINGS: NestSettings = {
  // Mặc định giữ chế độ cũ: đây là chế độ đã đo kỹ (478,2 cm / 92,45%) và cho bố trí
  // thành lưới để cắt. Muốn xếp lồng thì người dùng phải chọn rõ ràng.
  mode: 'ORTHOGONAL',
  sheetWidthMm: 570,
  marginMm: 5,
  gapMm: 3,
  // 100 cm: kho giay dai hon mot met la bat dau kho cam tay va de nhan nep khi keo
  // qua may. Muon dai hon thi tho tu nang len, hoac xoa trong de bo gioi han.
  maxSheetLengthMm: 1000,
  allowRotateGlobal: true,
  // Mặc định TẮT: phần lớn đơn cắt bế theo viền nên đường cắt vẽ thêm chỉ làm rối bản in.
  // Ai cần lưới cắt thì bật tay ở bước 2.
  drawCutLines: false,
}

/** Khoảng cách giữa hai lần hỏi trạng thái job. */
const POLL_INTERVAL_MS = 700

export const useNestingJobStore = defineStore('nestingJob', () => {
  const items = ref<NestItem[]>([])
  const settings = ref<NestSettings>({ ...DEFAULT_SETTINGS })

  const jobId = ref<string | null>(null)
  const job = ref<Job | null>(null)
  const submitting = ref(false)

  let pollTimer: number | null = null

  // --- Dẫn xuất ---

  /** Tổng số bản in của tất cả các dòng. */
  const totalQuantity = computed(() => items.value.reduce((sum, item) => sum + item.quantity, 0))

  /** Tổng diện tích hình, đơn vị mm². */
  const totalShapeAreaMm2 = computed(() =>
    items.value.reduce((sum, item) => sum + item.widthMm * item.heightMm * item.quantity, 0),
  )

  /** Đã đủ điều kiện sang bước 2 chưa. */
  const canProceedToSettings = computed(
    () => items.value.length > 0 && items.value.every((item) => item.widthMm > 0 && item.heightMm > 0),
  )

  const result = computed<NestResult | null>(() => job.value?.result ?? null)
  const isRunning = computed(
    () => job.value?.status === 'PENDING' || job.value?.status === 'RUNNING',
  )
  const error = computed<ApiErrorBody | null>(() => job.value?.error ?? null)

  // --- Thao tác trên danh sách hình ---

  /** Thêm các file vừa tải lên vào bảng, mặc định số lượng 1 và cho xoay. */
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
        sourceWidthMm: file.sourceWidthMm,
        sourceHeightMm: file.sourceHeightMm,
        trimmed: file.trimmed,
      })
    }
  }

  function removeItem(fileId: string) {
    items.value = items.value.filter((item) => item.fileId !== fileId)
  }

  /** Trả kích thước một dòng về đúng giá trị đọc được từ file. */
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

  // --- Chạy job ---

  /**
   * Gửi yêu cầu ghép và bắt đầu hỏi trạng thái.
   *
   * @returns mã job để router điều hướng sang bước 3
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
      // Lỗi đồng bộ (ví dụ hình rộng hơn khổ) được dựng thành một job FAILED giả lập,
      // nhờ vậy màn kết quả chỉ có MỘT chỗ để hiện lỗi thay vì hai đường xử lý.
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

  /** Đọc lại trạng thái một job đã có (dùng khi người dùng tải lại trang kết quả). */
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
        // Một lần hỏi thất bại không có nghĩa là job hỏng (có thể chỉ là mạng chập chờn).
        // Cứ để vòng lặp tiếp tục; người dùng vẫn có nút thử lại.
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
