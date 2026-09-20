/**
 * Kiem thu ban sao thuat toan dung cho che do demo (`src/api/mock.ts`).
 *
 * Chay:  npm run test
 *
 * Vi sao phai co bai nay: `mock.ts` phai tra ve DUNG schema nhu backend, nhung khong co
 * gi bat buoc dieu do - khong loi bien dich, khong test do. Quen mot truong thi ban demo
 * tren Netlify chi hien `undefined` hoac `NaN`, im lang cho toi khi co nguoi nhin thay.
 *
 * Bai test doi chieu chinh cac BAT BIEN ma bang so lieu phai giu, giong het bo test cua
 * backend: cong trong so cac loai lai bang 1, cong ty le lap day lai bang ty le chung.
 */
// `mock.ts` dung `window.setTimeout` de gia lap do tre cua mang. Chay ngoai trinh duyet
// thi phai dung tam cai `window`, va phai dung TRUOC khi nap module - nen nap dong.
globalThis.window ??= globalThis

const { mockCreateJob, mockGetJob } = await import('../dist-test/mock.mjs')

let pass = 0
let fail = 0

function check(label, ok, detail = '') {
  ok ? pass++ : fail++
  console.log(`  ${ok ? 'OK  ' : 'HONG'} ${label}${detail ? ': ' + detail : ''}`)
}

function near(a, b, eps = 0.0005) {
  return Math.abs(a - b) <= eps
}

const payload = {
  mode: 'ORTHOGONAL',
  sheetWidthMm: 570,
  marginMm: 5,
  gapMm: 3,
  maxSheetLengthMm: 2000,
  allowRotateGlobal: true,
  drawCutLines: false,
  items: [
    { fileId: 'f1', label: 'the', widthMm: 250, heightMm: 149, quantity: 15, allowRotate: true },
    { fileId: 'f2', label: 'nhan-to', widthMm: 50, heightMm: 46, quantity: 10, allowRotate: true },
    { fileId: 'f3', label: 'nhan-nho', widthMm: 44, heightMm: 28, quantity: 20, allowRotate: true },
  ],
}

const created = await mockCreateJob(payload)

// Mock gia lap do tre cua mang nen job chua xong ngay; hoi lai cho toi khi co ket qua.
let job = created
for (let i = 0; i < 200 && job.status !== 'DONE' && job.status !== 'FAILED'; i++) {
  await new Promise((resolve) => setTimeout(resolve, 50))
  job = await mockGetJob(created.jobId)
}

if (job.status !== 'DONE') {
  console.log('  HONG job khong chay xong, trang thai: ' + job.status)
  process.exit(1)
}

const stats = job.result.stats

console.log('schema so lieu tong hop:')
for (const field of [
  'totalSheets',
  'totalLengthMm',
  'totalShapeAreaMm2',
  'usedAreaMm2',
  'fillRate',
  'savedVsIndividualPct',
  'totalPieces',
  'byType',
]) {
  const value = stats[field]
  check(`co truong ${field}`, value !== undefined && value !== null, String(value))
}

console.log('\nbang so lieu tung loai hinh:')
const byType = stats.byType ?? []
check('du 3 dong cho 3 hinh tai len', byType.length === 3, `co ${byType.length} dong`)
check(
  'sap theo categoryIndex tang dan',
  byType.every((row, i) => row.categoryIndex === i),
  byType.map((r) => r.categoryIndex).join(','),
)
check(
  'khong con so nao la NaN',
  byType.every((row) =>
    [row.pieces, row.widthMm, row.heightMm, row.shapeAreaMm2, row.shareOfShapes, row.fillRate]
      .every((n) => typeof n === 'number' && Number.isFinite(n)),
  ),
)

const pieces = byType.reduce((sum, row) => sum + row.pieces, 0)
check('tong so ban khop bang tong hop', pieces === stats.totalPieces, `${pieces} / ${stats.totalPieces}`)
check('tong so ban dung bang so luong yeu cau', pieces === 45, String(pieces))

const shareSum = byType.reduce((sum, row) => sum + row.shareOfShapes, 0)
check('cong trong so cac loai lai bang 1', near(shareSum, 1), shareSum.toFixed(4))

const fillSum = byType.reduce((sum, row) => sum + row.fillRate, 0)
check(
  'cong ty le lap day cac loai lai bang ty le chung',
  near(fillSum, stats.fillRate),
  `${fillSum.toFixed(4)} / ${stats.fillRate.toFixed(4)}`,
)

// Kich thuoc phai la kich thuoc GOC, khong doi theo huong packer xoay.
// Tim theo categoryIndex chu khong theo ten: mock lay ten tu kho file da upload, ma bai
// test nay goi thang vao thuat toan nen kho do rong va ten roi ve ma file.
const card = byType.find((row) => row.categoryIndex === 0)
check(
  'kich thuoc la kich thuoc truoc khi xoay',
  card && near(card.widthMm, 250, 0.6) && near(card.heightMm, 149, 0.6),
  card ? `${card.widthMm} x ${card.heightMm}` : 'khong tim thay dong',
)

console.log(`\n${pass} dat / ${fail} hong`)
process.exit(fail === 0 ? 0 : 1)
