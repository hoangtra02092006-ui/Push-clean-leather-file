/** Goi API tao va theo doi lan ghep, va tai file ket qua. */
import { http, USE_MOCK, API_BASE_URL } from './client'
import { mockCreateJob, mockGetJob } from './mock'
import type { Job, NestRequestPayload } from '@/types'

/** Tao job ghep moi. Backend tra 202 kem jobId, viec tinh chay o luong nen. */
export async function createJob(payload: NestRequestPayload): Promise<Job> {
  if (USE_MOCK) {
    return mockCreateJob(payload)
  }
  const { data } = await http.post<Job>('/api/v1/nesting/jobs', payload)
  return data
}

/** Hoi trang thai mot job. */
export async function getJob(jobId: string): Promise<Job> {
  if (USE_MOCK) {
    return mockGetJob(jobId)
  }
  const { data } = await http.get<Job>(`/api/v1/nesting/jobs/${jobId}`)
  return data
}

/** URL tai PDF cua mot tam. */
export function sheetPdfUrl(jobId: string, index: number): string {
  return `${API_BASE_URL}/api/v1/nesting/jobs/${jobId}/sheets/${index}/pdf`
}

/** URL tai tat ca cac tam duoi dang .zip. */
export function exportZipUrl(jobId: string): string {
  return `${API_BASE_URL}/api/v1/nesting/jobs/${jobId}/export.zip`
}
