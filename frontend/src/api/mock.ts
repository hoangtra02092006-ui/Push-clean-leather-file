/**
 * Che do demo: chay ngay tren trinh duyet, khong can backend.
 *
 * Ban Netlify chi host duoc frontend tinh, nen neu khong co che do nay thi trang demo
 * bam vao dau cung bao loi mang - khong xem duoc gi. Voi `VITE_USE_MOCK=true`, module
 * nay thay the toan bo tang API: doc kich thuoc file ngay tren may, chay mot ban RUT GON
 * cua thuat toan nesting bang TypeScript, va tra ve du lieu dung y het schema that.
 *
 * KHAC BIET SO VOI BACKEND (co y, de giu bundle nho va chay muot):
 * - Chi dung mot heuristic (Best Short Side Fit) thay vi duyet hang tram phuong an.
 * - KHONG ho tro hai che do xep long (FREE va TRUE_SHAPE): doc vector de biet cho nao
 *   trong ben trong khung bao can mot bo phan tich PDF day du, qua nang cho trinh duyet.
 *   Che do demo luon xep nhu ORTHOGONAL du nguoi dung chon gi.
 * - Khong co buoc tinh chinh cuc bo, nen ty le lap day thap hon ban that vai phan tram.
 * - Khong xuat duoc PDF that: nut tai ve se bao cho nguoi dung biet dieu do.
 *
 * Vi vay che do demo dung de XEM VA THU GIAO DIEN, khong dung de chay don that.
 */
import type {
  Job,
  NestRequestPayload,
  NestResult,
  Placement,
  Sheet,
  TypeStats,
  UploadedFile,
} from '@/types'

/** Don vi noi bo: 1/100 mm, giong backend, de tranh sai so dau phay dong. */
const CMM = 100

const mockFiles = new Map<string, UploadedFile>()
const mockJobs = new Map<string, Job>()

/** Kich thuoc mac dinh khi khong doc duoc gi tu file. */
const FALLBACK_MM = 100

// ---------------------------------------------------------------------------
// Doc kich thuoc file ngay tren trinh duyet
// ---------------------------------------------------------------------------

/**
 * Doc kich thuoc vat ly cua mot file.
 *
 * Anh: lay so pixel that roi quy doi theo 96 DPI (quy uoc cua trinh duyet).
 * PDF: do trinh duyet khong co san bo doc PDF, ta do tim chuoi `/MediaBox [...]` trong
 * phan dau file. Cach nay an voi phan lon PDF thong thuong; khong an thi dung kich thuoc
 * mac dinh va nguoi dung sua tay o bang - o kich thuoc von da cho sua san.
 */
async function readDimensions(file: File): Promise<{ widthMm: number; heightMm: number }> {
  if (file.type.startsWith('image/')) {
    return readImageDimensions(file)
  }
  return readPdfDimensions(file)
}

function readImageDimensions(file: File): Promise<{ widthMm: number; heightMm: number }> {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(file)
    const image = new Image()
    image.onload = () => {
      URL.revokeObjectURL(url)
      // 96 DPI la quy uoc CSS cua trinh duyet; 1 inch = 25.4 mm.
      const perPixel = 25.4 / 96
      resolve({
        widthMm: round2(image.naturalWidth * perPixel),
        heightMm: round2(image.naturalHeight * perPixel),
      })
    }
    image.onerror = () => {
      URL.revokeObjectURL(url)
      resolve({ widthMm: FALLBACK_MM, heightMm: FALLBACK_MM })
    }
    image.src = url
  })
}

async function readPdfDimensions(file: File): Promise<{ widthMm: number; heightMm: number }> {
  try {
    // Chi doc phan dau file: MediaBox gan nhu luon nam trong vai chuc KB dau.
    const head = await file.slice(0, 64_000).text()
    const match = head.match(/\/MediaBox\s*\[\s*([\d.-]+)\s+([\d.-]+)\s+([\d.-]+)\s+([\d.-]+)\s*\]/)
    if (match) {
      const widthPt = Math.abs(Number(match[3]) - Number(match[1]))
      const heightPt = Math.abs(Number(match[4]) - Number(match[2]))
      if (widthPt > 0 && heightPt > 0) {
        return { widthMm: round2(ptToMm(widthPt)), heightMm: round2(ptToMm(heightPt)) }
      }
    }
  } catch {
    // Doc that bai thi roi xuong kich thuoc mac dinh ben duoi.
  }
  return { widthMm: FALLBACK_MM, heightMm: FALLBACK_MM }
}

function ptToMm(pt: number): number {
  return (pt * 25.4) / 72
}

function round2(value: number): number {
  return Math.round(value * 100) / 100
}

// ---------------------------------------------------------------------------
// Tang API gia lap
// ---------------------------------------------------------------------------

/** Gia lap upload: doc kich thuoc va giu file trong bo nho tab hien tai. */
export async function mockUpload(files: File[]): Promise<UploadedFile[]> {
  const results: UploadedFile[] = []
  for (const file of files) {
    const { widthMm, heightMm } = await readDimensions(file)
    const uploaded: UploadedFile = {
      id: `demo-${crypto.randomUUID()}`,
      originalName: file.name,
      widthMm,
      heightMm,
      // Che do demo KHONG cat khoang trang: doc content stream cua PDF doi hoi mot bo
      // phan tich day du, qua nang de nhet vao bundle trinh duyet. Bang se bao kich
      // thuoc tron kho trang; ban chay backend that moi co so da cat.
      sourceWidthMm: widthMm,
      sourceHeightMm: heightMm,
      trimmed: false,
      type: file.type.startsWith('image/') ? 'IMAGE' : 'PDF',
      pageCount: 1,
      previewUrl: URL.createObjectURL(file),
    }
    mockFiles.set(uploaded.id, uploaded)
    results.push(uploaded)
  }
  return results
}

/** Gia lap tao job: chay thuat toan ngay lap tuc roi luu ket qua. */
export async function mockCreateJob(payload: NestRequestPayload): Promise<Job> {
  const jobId = `demo-${crypto.randomUUID()}`
  const job: Job = { jobId, status: 'PENDING', progress: 0 }
  mockJobs.set(jobId, job)

  // Cho mot nhip roi moi tinh, de giao dien kip hien trang thai "dang chay" nhu that.
  window.setTimeout(() => {
    try {
      const result = runMockNesting(payload)
      mockJobs.set(jobId, { jobId, status: 'DONE', progress: 100, result })
    } catch (error) {
      mockJobs.set(jobId, {
        jobId,
        status: 'FAILED',
        progress: 0,
        error: {
          code: 'ITEM_WIDER_THAN_SHEET',
          message: error instanceof Error ? error.message : 'Ghep that bai.',
        },
      })
    }
  }, 600)

  return job
}

/** Gia lap hoi trang thai job. */
export async function mockGetJob(jobId: string): Promise<Job> {
  const job = mockJobs.get(jobId)
  if (!job) {
    throw new Error(`Khong tim thay lan ghep ${jobId}.`)
  }
  return job
}

// ---------------------------------------------------------------------------
// Thuat toan rut gon
// ---------------------------------------------------------------------------

interface MockPiece {
  fileId: string
  label: string
  categoryIndex: number
  /** Kich thuoc DA cong gap, don vi 1/100 mm. */
  w: number
  h: number
  realW: number
  realH: number
  allowRotate: boolean
}

interface FreeRect {
  x: number
  y: number
  w: number
  h: number
}

interface MockPlaced {
  piece: MockPiece
  x: number
  y: number
  w: number
  h: number
  rotated: boolean
}

/** Chay ban rut gon cua thuat toan va dung ket qua dung schema that. */
function runMockNesting(payload: NestRequestPayload): NestResult {
  const gap = Math.ceil(payload.gapMm * CMM)
  const margin = Math.ceil(payload.marginMm * CMM)
  const sheetWidth = Math.floor(payload.sheetWidthMm * CMM)
  // Giong backend: noi vung kha dung them mot gap vi hinh sat bien khong can gap ngoai.
  const usableWidth = sheetWidth - 2 * margin + gap

  const pieces = buildPieces(payload, gap)
  for (const piece of pieces) {
    const canRotate = payload.allowRotateGlobal && piece.allowRotate
    const narrowest = canRotate ? Math.min(piece.w, piece.h) : piece.w
    if (narrowest > usableWidth) {
      throw new Error(
        `Hinh "${piece.label}" ${fmt(piece.realW)}x${fmt(piece.realH)} cm ` +
          `rong hon kho ${fmt(sheetWidth)} cm.`,
      )
    }
  }

  const maxLength =
    payload.maxSheetLengthMm != null
      ? Math.floor(payload.maxSheetLengthMm * CMM) - 2 * margin + gap
      : null

  const packedSheets: MockPlaced[][] = []
  let remaining = [...pieces].sort((a, b) => b.w * b.h - a.w * a.h)

  while (remaining.length > 0) {
    const limit = maxLength ?? sumHeights(remaining)
    const { placed, unplaced } = packOne(remaining, usableWidth, limit, payload.allowRotateGlobal)
    if (placed.length === 0) {
      throw new Error('Khong dat duoc hinh nao vao tam. Hay noi long tham so.')
    }
    packedSheets.push(placed)
    remaining = unplaced
    if (packedSheets.length > 200) {
      throw new Error('So tam vuot qua gioi han an toan.')
    }
  }

  return buildResult(packedSheets, payload, margin, gap, sheetWidth)
}

function buildPieces(payload: NestRequestPayload, gap: number): MockPiece[] {
  const categories = new Map<string, number>()
  const pieces: MockPiece[] = []

  for (const item of payload.items) {
    const file = mockFiles.get(item.fileId)
    const widthMm = item.widthMm ?? file?.widthMm ?? FALLBACK_MM
    const heightMm = item.heightMm ?? file?.heightMm ?? FALLBACK_MM
    if (!categories.has(item.fileId)) {
      categories.set(item.fileId, categories.size)
    }
    const categoryIndex = categories.get(item.fileId) as number
    const realW = Math.ceil(widthMm * CMM)
    const realH = Math.ceil(heightMm * CMM)

    for (let i = 0; i < item.quantity; i += 1) {
      pieces.push({
        fileId: item.fileId,
        label: file?.originalName ?? item.fileId,
        categoryIndex,
        w: realW + gap,
        h: realH + gap,
        realW,
        realH,
        allowRotate: payload.allowRotateGlobal && item.allowRotate,
      })
    }
  }
  return pieces
}

function sumHeights(pieces: MockPiece[]): number {
  return pieces.reduce((total, piece) => total + Math.max(piece.w, piece.h), 0) + 1
}

/** Mot lan xep MaxRects voi heuristic Best Short Side Fit. */
function packOne(
  pieces: MockPiece[],
  binWidth: number,
  binHeight: number,
  allowRotate: boolean,
): { placed: MockPlaced[]; unplaced: MockPiece[] } {
  const freeRects: FreeRect[] = [{ x: 0, y: 0, w: binWidth, h: binHeight }]
  const placed: MockPlaced[] = []
  const unplaced: MockPiece[] = []

  const tryInsert = (piece: MockPiece): boolean => {
    const canRotate = allowRotate && piece.allowRotate
    let best: MockPlaced | null = null
    let bestScore = [Number.MAX_SAFE_INTEGER, Number.MAX_SAFE_INTEGER]

    for (const free of freeRects) {
      const options: Array<[number, number, boolean]> = [[piece.w, piece.h, false]]
      if (canRotate && piece.w !== piece.h) {
        options.push([piece.h, piece.w, true])
      }
      for (const [w, h, rotated] of options) {
        if (w > free.w || h > free.h) continue
        const leftoverH = free.w - w
        const leftoverV = free.h - h
        const score: [number, number] = [
          Math.min(leftoverH, leftoverV),
          Math.max(leftoverH, leftoverV),
        ]
        if (
          score[0] < bestScore[0] ||
          (score[0] === bestScore[0] && score[1] < bestScore[1])
        ) {
          bestScore = score
          best = { piece, x: free.x, y: free.y, w, h, rotated }
        }
      }
    }

    if (!best) return false
    placed.push(best)
    splitFreeRects(freeRects, best)
    return true
  }

  for (const piece of pieces) {
    if (!tryInsert(piece)) {
      unplaced.push(piece)
    }
  }

  // Nhoi lai cac hinh nho con sot vao khe trong con lai.
  const pending = unplaced.splice(0, unplaced.length).sort((a, b) => a.w * a.h - b.w * b.h)
  for (const piece of pending) {
    if (!tryInsert(piece)) {
      unplaced.push(piece)
    }
  }

  return { placed, unplaced }
}

/** Cat cac o trong bi hinh vua dat de len, roi bo o trong nam long nhau. */
function splitFreeRects(freeRects: FreeRect[], used: MockPlaced): void {
  const generated: FreeRect[] = []

  for (let i = freeRects.length - 1; i >= 0; i -= 1) {
    const free = freeRects[i]
    const noOverlap =
      used.x >= free.x + free.w ||
      used.x + used.w <= free.x ||
      used.y >= free.y + free.h ||
      used.y + used.h <= free.y
    if (noOverlap) continue

    if (used.y > free.y && used.y < free.y + free.h) {
      generated.push({ x: free.x, y: free.y, w: free.w, h: used.y - free.y })
    }
    if (used.y + used.h < free.y + free.h && used.y + used.h > free.y) {
      generated.push({
        x: free.x,
        y: used.y + used.h,
        w: free.w,
        h: free.y + free.h - (used.y + used.h),
      })
    }
    if (used.x > free.x && used.x < free.x + free.w) {
      generated.push({ x: free.x, y: free.y, w: used.x - free.x, h: free.h })
    }
    if (used.x + used.w < free.x + free.w && used.x + used.w > free.x) {
      generated.push({
        x: used.x + used.w,
        y: free.y,
        w: free.x + free.w - (used.x + used.w),
        h: free.h,
      })
    }
    freeRects.splice(i, 1)
  }

  freeRects.push(...generated)

  for (let i = 0; i < freeRects.length; i += 1) {
    for (let j = i + 1; j < freeRects.length; j += 1) {
      if (contains(freeRects[j], freeRects[i])) {
        freeRects.splice(i, 1)
        i -= 1
        break
      }
      if (contains(freeRects[i], freeRects[j])) {
        freeRects.splice(j, 1)
        j -= 1
      }
    }
  }
}

function contains(outer: FreeRect, inner: FreeRect): boolean {
  return (
    inner.x >= outer.x &&
    inner.y >= outer.y &&
    inner.x + inner.w <= outer.x + outer.w &&
    inner.y + inner.h <= outer.y + outer.h
  )
}

/** Doi ket qua noi bo sang DTO dung schema cua backend. */
function buildResult(
  packedSheets: MockPlaced[][],
  payload: NestRequestPayload,
  margin: number,
  gap: number,
  sheetWidth: number,
): NestResult {
  const sheets: Sheet[] = []
  let totalLengthMm = 0
  let totalShapeAreaMm2 = 0
  let totalPieces = 0

  packedSheets.forEach((placedList, index) => {
    const contentTop = placedList.reduce((max, p) => Math.max(max, p.y + p.h), 0)
    const sheetLength = Math.max(0, contentTop - gap) + 2 * margin
    const widthMm = round2(sheetWidth / CMM)
    const lengthMm = round2(sheetLength / CMM)

    const placements: Placement[] = placedList
      .slice()
      .sort((a, b) => a.y - b.y || a.x - b.x)
      .map((p) => {
        const realW = p.rotated ? p.piece.realH : p.piece.realW
        const realH = p.rotated ? p.piece.realW : p.piece.realH
        return {
          fileId: p.piece.fileId,
          label: p.piece.label,
          categoryIndex: p.piece.categoryIndex,
          xMm: round2((margin + p.x) / CMM),
          yMm: round2((margin + p.y) / CMM),
          wMm: round2(realW / CMM),
          hMm: round2(realH / CMM),
          rotated: p.rotated,
        }
      })

    const shapeArea = placements.reduce((sum, p) => sum + p.wMm * p.hMm, 0)
    const sheetArea = widthMm * lengthMm

    sheets.push({
      index,
      widthMm,
      lengthMm,
      fillRate: sheetArea > 0 ? round4(shapeArea / sheetArea) : 0,
      placements,
    })

    totalLengthMm += lengthMm
    totalShapeAreaMm2 += shapeArea
    totalPieces += placements.length
  })

  const widthMm = round2(sheetWidth / CMM)
  const usedAreaMm2 = widthMm * totalLengthMm
  const individualLength = sheets.reduce(
    (sum, sheet) =>
      sum +
      sheet.placements.reduce((inner, p) => inner + (p.rotated ? p.wMm : p.hMm) + payload.gapMm, 0),
    2 * payload.marginMm,
  )

  return {
    sheets,
    stats: {
      totalSheets: sheets.length,
      totalLengthMm: round2(totalLengthMm),
      totalShapeAreaMm2: round2(totalShapeAreaMm2),
      usedAreaMm2: round2(usedAreaMm2),
      fillRate: usedAreaMm2 > 0 ? round4(totalShapeAreaMm2 / usedAreaMm2) : 0,
      savedVsIndividualPct:
        individualLength > 0
          ? round4(Math.max(0, ((individualLength - totalLengthMm) / individualLength) * 100))
          : 0,
      totalPieces,
      byType: buildTypeStats(sheets, usedAreaMm2),
    },
  }
}

/**
 * Gom cac ban in lai theo loai hinh.
 *
 * <p>Ban sao cua `TypeStats.from` ben backend. Gom theo `categoryIndex` chu khong theo
 * ten file, va sap theo khoa do, de bang so lieu va hinh ve luon noi ve cung mot thu.
 */
function buildTypeStats(sheets: Sheet[], usedAreaMm2: number): TypeStats[] {
  const rows = new Map<number, TypeStats>()
  let totalShapeArea = 0

  for (const sheet of sheets) {
    for (const p of sheet.placements) {
      let row = rows.get(p.categoryIndex)
      if (!row) {
        row = {
          fileId: p.fileId,
          label: p.label,
          categoryIndex: p.categoryIndex,
          pieces: 0,
          // Kich thuoc TRUOC khi xoay: xoay 90 do thi w/h da bi hoan doi nen doi lai.
          widthMm: p.rotated ? p.hMm : p.wMm,
          heightMm: p.rotated ? p.wMm : p.hMm,
          shapeAreaMm2: 0,
          shareOfShapes: 0,
          fillRate: 0,
        }
        rows.set(p.categoryIndex, row)
      }
      const area = p.wMm * p.hMm
      row.pieces += 1
      row.shapeAreaMm2 += area
      totalShapeArea += area
    }
  }

  return [...rows.values()]
    .sort((a, b) => a.categoryIndex - b.categoryIndex)
    .map((row) => ({
      ...row,
      shapeAreaMm2: round2(row.shapeAreaMm2),
      shareOfShapes: totalShapeArea > 0 ? round4(row.shapeAreaMm2 / totalShapeArea) : 0,
      fillRate: usedAreaMm2 > 0 ? round4(row.shapeAreaMm2 / usedAreaMm2) : 0,
    }))
}

function round4(value: number): number {
  return Math.round(value * 10000) / 10000
}

function fmt(cmm: number): string {
  return (cmm / CMM / 10).toFixed(1)
}
