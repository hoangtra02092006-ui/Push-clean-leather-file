/** Goi API lien quan toi file in. */
import { http, USE_MOCK, absoluteUrl } from './client'
import { mockUpload } from './mock'
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
