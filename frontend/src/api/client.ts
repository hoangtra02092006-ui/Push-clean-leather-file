/**
 * Axios client dung chung.
 *
 * Hai viec quan trong:
 * 1. Lay base URL tu bien moi truong nen doi backend khong phai sua code.
 * 2. Chuan hoa loi: moi loi nem ra deu la `ApiError` co `code` va `message` tieng Viet,
 *    nho vay tang giao dien khong phai doan cau truc loi tung truong hop.
 */
import axios, { AxiosError } from 'axios'
import type { ApiErrorBody } from '@/types'

/** Bat che do demo: chay thuat toan rut gon ngay tren trinh duyet, khong can backend. */
export const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

/** URL goc cua backend. */
export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

/** Loi da chuan hoa, dung chung toan app. */
export class ApiError extends Error {
  readonly code: string
  readonly details?: Record<string, unknown>

  constructor(body: ApiErrorBody) {
    super(body.message)
    this.name = 'ApiError'
    this.code = body.code
    this.details = body.details
  }
}

export const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 120_000,
})

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<{ error?: ApiErrorBody }>) => {
    // Backend tra dung cau truc { error: { code, message } } -> dung nguyen.
    const body = error.response?.data?.error
    if (body) {
      return Promise.reject(new ApiError(body))
    }
    // Khong co phan hoi: thuong la backend chua chay hoac CORS chan.
    if (error.code === 'ERR_NETWORK' || !error.response) {
      return Promise.reject(
        new ApiError({
          code: 'NETWORK_ERROR',
          message:
            'Khong ket noi duoc toi may chu. Hay kiem tra backend da chay chua, ' +
            'hoac bat che do demo (VITE_USE_MOCK=true).',
        }),
      )
    }
    return Promise.reject(
      new ApiError({
        code: 'INTERNAL_ERROR',
        message: `May chu tra ve loi ${error.response.status}.`,
      }),
    )
  },
)

/** Ghep duong dan tuong doi cua backend thanh URL day du de dung trong <img> hay tai file. */
export function absoluteUrl(path: string): string {
  if (path.startsWith('http') || path.startsWith('data:') || path.startsWith('blob:')) {
    return path
  }
  return `${API_BASE_URL}${path}`
}
