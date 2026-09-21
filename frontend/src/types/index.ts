/**
 * Kieu du lieu dung chung cho ca app.
 *
 * QUY UOC DON VI: moi truong ket thuc bang `Mm` la MILIMET va la don vi trao doi voi
 * backend. Giao dien hien thi cho nguoi dung bang CENTIMET; viec quy doi nam o
 * `src/api/units.ts` chu khong rai rac trong component.
 */

/** Loai file nguon. */
export type FileKind = 'PDF' | 'IMAGE'

/** Thong tin mot file da tai len, do backend tra ve. */
export interface UploadedFile {
  id: string
  originalName: string
  /** Kich thuoc DUNG DE XEP - backend da cat bo khoang trang bao quanh. */
  widthMm: number
  heightMm: number
  /** Kich thuoc kho trang nguyen ban, truoc khi cat. */
  sourceWidthMm: number
  sourceHeightMm: number
  /** Co cat duoc khoang trang nao khong - giao dien hien chu thich. */
  trimmed: boolean
  type: FileKind
  pageCount: number
  previewUrl: string
}

/** Mot dong trong bang danh sach can ghep, o trang thai dang soan tren giao dien. */
export interface NestItem {
  /** Ma file tren backend. */
  fileId: string
  /** Ten hien thi. */
  label: string
  /** Kich thuoc doc duoc tu file - giu lai de nut hoan tac quay ve duoc. */
  originalWidthMm: number
  originalHeightMm: number
  /** Kich thuoc dang dung (nguoi dung co the sua tay). */
  widthMm: number
  heightMm: number
  quantity: number
  allowRotate: boolean
  previewUrl: string
  type: FileKind
  /** Kho trang nguyen ban va co da cat khoang trang chua - chi de hien chu thich. */
  sourceWidthMm: number
  sourceHeightMm: number
  trimmed: boolean
}

/** Cach thuat toan duoc phep sap xep hinh. */
export type NestingMode = 'ORTHOGONAL' | 'FREE' | 'TRUE_SHAPE'

/** Tham so khoi in. */
export interface NestSettings {
  /**
   * ORTHOGONAL: chi xoay 0 hoac 90 do, moi hinh chiem tron khung bao chu nhat.
   * FREE: cho phep hinh nho chui LOT HAN vao phan trong ben trong khung bao hinh lon.
   * TRUE_SHAPE: khung bao duoc chong nhau, chi net ve la khong duoc cham; xoay nhieu goc.
   */
  mode: NestingMode
  sheetWidthMm: number
  marginMm: number
  gapMm: number
  /** null = khong gioi han chieu dai moi file. */
  maxSheetLengthMm: number | null
  allowRotateGlobal: boolean
  drawCutLines: boolean
}

/** Vi tri mot hinh tren tam. Goc toa do o TRAI-DUOI cua tam. */
export interface Placement {
  fileId: string
  label: string
  categoryIndex: number
  xMm: number
  yMm: number
  wMm: number
  hMm: number
  rotated: boolean
}

/** Mot tam in - tuong ung mot file PDF xuat ra. */
export interface Sheet {
  index: number
  widthMm: number
  lengthMm: number
  /** Ty le lap day 0..1. */
  fillRate: number
  placements: Placement[]
}

/**
 * So lieu cua MOT LOAI HINH tren toan bo lan ghep.
 *
 * Con so quan trong nhat la `lengthMm`: mau nay an bao nhieu met cuon. Cong lengthMm cua
 * moi loai lai dung bang tong chieu dai - phan giay bo di da duoc chia deu vao dau tung
 * mau theo ty le, vi khong mau nao mot minh gay ra cho trong.
 */
export interface TypeStats {
  fileId: string
  label: string
  /** Thu tu loai hinh - khop voi mau trong preview. */
  categoryIndex: number
  pieces: number
  /** Kich thuoc mot ban, do TRUOC khi xoay. */
  widthMm: number
  heightMm: number
  shapeAreaMm2: number
  /** Chieu dai cuon mau nay an, da gom ca phan giay bo di chia deu. */
  lengthMm: number
}

/** So lieu tong hop cua mot lan ghep. */
export interface NestStats {
  totalSheets: number
  totalLengthMm: number
  totalShapeAreaMm2: number
  usedAreaMm2: number
  fillRate: number
  savedVsIndividualPct: number
  totalPieces: number
  /** Tach theo tung loai hinh, sap theo categoryIndex tang dan. */
  byType: TypeStats[]
}

/** Ket qua day du. */
export interface NestResult {
  sheets: Sheet[]
  stats: NestStats
}

/** Trang thai job. */
export type JobStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED'

/** Ma loi backend co the tra ve. */
export type ErrorCode =
  | 'FILE_TOO_LARGE'
  | 'UNSUPPORTED_FORMAT'
  | 'SIZE_UNREADABLE'
  | 'ITEM_WIDER_THAN_SHEET'
  | 'JOB_NOT_FOUND'
  | 'FILE_NOT_FOUND'
  | 'JOB_NOT_READY'
  | 'INVALID_REQUEST'
  | 'NESTING_FAILED'
  | 'INTERNAL_ERROR'

/** Than loi thong nhat. */
export interface ApiErrorBody {
  code: ErrorCode | string
  message: string
  details?: Record<string, unknown>
}

/** Trang thai mot lan ghep, do backend tra ve. */
export interface Job {
  jobId: string
  status: JobStatus
  progress: number
  result?: NestResult
  error?: ApiErrorBody
  createdAt?: string
  finishedAt?: string
}

/** Mot dong gui len backend khi tao job. */
export interface NestItemPayload {
  fileId: string
  quantity: number
  allowRotate: boolean
  widthMm?: number
  heightMm?: number
}

/** Than yeu cau tao job. */
export interface NestRequestPayload extends NestSettings {
  items: NestItemPayload[]
}
