/** Goi API lien quan toi file in. */
import { http, USE_MOCK, absoluteUrl, API_BASE_URL } from './client'
import { mockPreviewUrl, mockUpload } from './mock'
import type { UploadedFile } from '@/types'

/**
 * Tai nhieu file len may chu.
 *
 * @param files       danh sach file nguoi dung chon
 * @param onProgress  ham nhan tien do 0..100 de hien thanh tien trinh
 */
export async function uploadFiles(
  files: File[],
  onProgress?: (percent: number) => void,
): Promise<UploadedFile[]> {
  if (USE_MOCK) {
    onProgress?.(100)
    return mockUpload(files)
  }

  const form = new FormData()
  files.forEach((file) => form.append('files', file))

  const { data } = await http.post<UploadedFile[]>('/api/v1/files', form, {
    onUploadProgress: (event) => {
      if (event.total) {
        onProgress?.(Math.round((event.loaded / event.total) * 100))
      }
    },
  })

  // Doi duong dan tuong doi thanh URL day du de dung thang trong the <img>.
  return data.map((file) => ({ ...file, previewUrl: absoluteUrl(file.previewUrl) }))
}

/**
 * Duong dan anh xem truoc cua mot file, suy ra tu ma file.
 *
 * <p>Man ket qua chi co ma file chu khong giu duong dan anh: vao thang bang URL hoac tai
 * lai trang thi danh sach o buoc 1 da rong. Suy ra tu ma file thi luc nao cung co.
 *
 * <p>Anh co the khong con neu file da bi don sau han giu - luc do the <img> bao loi va
 * {@link FilePreview} tu an o di.
 */
export function filePreviewUrl(fileId: string): string {
  if (USE_MOCK) {
    return mockPreviewUrl(fileId)
  }
  return `${API_BASE_URL}/api/v1/files/${fileId}/preview`
}
