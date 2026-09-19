/**
 * Sinh bo file in mau de kiem thu PrintNest.
 *
 * Moi file PDF duoc dung bang tay (khong dung thu vien) de kich thuoc vat ly la CHINH XAC
 * tuyet doi: MediaBox va TrimBox deu duoc dat dung so point tuong ung voi so centimet ghi
 * trong ten file. Nho vay khi upload, con so backend doc ra phai khop den tung 0,1 mm -
 * bat duoc ngay neu buoc doc metadata co sai sot.
 *
 * Chay:  node generate.mjs
 */
import { writeFileSync } from 'node:fs'
import { deflateSync } from 'node:zlib'

const PT_PER_MM = 72 / 25.4

/** Bang mau, khop voi --c-cat-* cua design system de nhin preview cho de doi chieu. */
const COLORS = [
  [0.486, 0.227, 0.929], [0.918, 0.345, 0.047], [0.033, 0.569, 0.698],
  [0.792, 0.541, 0.016], [0.859, 0.153, 0.467], [0.086, 0.639, 0.290],
  [0.310, 0.275, 0.898], [0.725, 0.110, 0.110],
]

/** Dung mot file PDF mot trang, dung kich thuoc, co vien va nhan chu o giua. */
function buildPdf(widthCm, heightCm, label, colorIndex) {
  const w = +(widthCm * 10 * PT_PER_MM).toFixed(4)
  const h = +(heightCm * 10 * PT_PER_MM).toFixed(4)
  const [r, g, b] = COLORS[colorIndex % COLORS.length]

  // Co chu vua khung, nhung khong bao gio nho qua 5pt hay to qua 20pt.
  const fontSize = Math.max(5, Math.min(20, Math.min(w / (label.length * 0.62), h / 3)))
  const textWidth = label.length * fontSize * 0.5
  const tx = (w - textWidth) / 2
  const ty = (h - fontSize) / 2

  const content = [
    `${r.toFixed(3)} ${g.toFixed(3)} ${b.toFixed(3)} rg`,
    `0 0 ${w} ${h} re f`,                                  // nen dac
    `1 1 1 RG 1.5 w`,
    `2 2 ${(w - 4).toFixed(3)} ${(h - 4).toFixed(3)} re S`, // vien trang
    `BT /F1 ${fontSize.toFixed(2)} Tf 1 1 1 rg`,
    `${tx.toFixed(2)} ${ty.toFixed(2)} Td (${label}) Tj ET`,
  ].join('\n')

  const objects = [
    `<</Type/Catalog/Pages 2 0 R>>`,
    `<</Type/Pages/Kids[3 0 R]/Count 1>>`,
    // Dat CA MediaBox va TrimBox: backend uu tien TrimBox nen day cung la mot phep thu.
    `<</Type/Page/Parent 2 0 R/MediaBox[0 0 ${w} ${h}]/TrimBox[0 0 ${w} ${h}]` +
      `/Resources<</Font<</F1 5 0 R>>>>/Contents 4 0 R>>`,
    `<</Length ${Buffer.byteLength(content)}>>\nstream\n${content}\nendstream`,
    `<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>`,
  ]

  let pdf = '%PDF-1.4\n'
  const offsets = []
  objects.forEach((body, i) => {
    offsets.push(Buffer.byteLength(pdf))
    pdf += `${i + 1} 0 obj\n${body}\nendobj\n`
  })

  const xrefAt = Buffer.byteLength(pdf)
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`
  for (const off of offsets) {
    pdf += `${String(off).padStart(10, '0')} 00000 n \n`
  }
  pdf += `trailer\n<</Size ${objects.length + 1}/Root 1 0 R>>\nstartxref\n${xrefAt}\n%%EOF\n`

  return Buffer.from(pdf, 'latin1')
}

// --- PNG toi thieu, co khai bao DPI qua chunk pHYs ---

function crc32(buf) {
  let c, crc = 0xffffffff
  for (let n = 0; n < buf.length; n++) {
    c = (crc ^ buf[n]) & 0xff
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
    crc = c ^ (crc >>> 8)
  }
  return (crc ^ 0xffffffff) >>> 0
}

function chunk(type, data) {
  const len = Buffer.alloc(4)
  len.writeUInt32BE(data.length)
  const body = Buffer.concat([Buffer.from(type, 'latin1'), data])
  const crc = Buffer.alloc(4)
  crc.writeUInt32BE(crc32(body))
  return Buffer.concat([len, body, crc])
}

/** PNG mau dac, ghi san DPI de backend quy doi pixel -> milimet dung. */
function buildPng(widthPx, heightPx, dpi, [r, g, b]) {
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(widthPx, 0)
  ihdr.writeUInt32BE(heightPx, 4)
  ihdr[8] = 8    // bit depth
  ihdr[9] = 2    // truecolour RGB
  ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0

  // pHYs: so pixel tren MOT MET, nen DPI / 0.0254.
  const ppm = Math.round(dpi / 0.0254)
  const phys = Buffer.alloc(9)
  phys.writeUInt32BE(ppm, 0)
  phys.writeUInt32BE(ppm, 4)
  phys[8] = 1    // don vi la met

  const row = Buffer.alloc(1 + widthPx * 3)
  for (let x = 0; x < widthPx; x++) {
    row[1 + x * 3] = Math.round(r * 255)
    row[2 + x * 3] = Math.round(g * 255)
    row[3 + x * 3] = Math.round(b * 255)
  }
  const raw = Buffer.concat(Array.from({ length: heightPx }, () => row))

  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('pHYs', phys),
    chunk('IDAT', deflateSync(raw)),
    chunk('IEND', Buffer.alloc(0)),
  ])
}

// --- Sinh file ---

const SHAPES = [
  [7, 3, 30], [5, 7, 10], [13, 4, 20], [4, 17, 15], [16, 13, 20], [20, 18, 50],
]

SHAPES.forEach(([w, h], i) => {
  const name = `nhan-${w}x${h}.pdf`
  writeFileSync(name, buildPdf(w, h, `${w}x${h}cm`, i))
  console.log(`  ${name}`)
})

// Hinh rong hon kho 57 cm ke ca sau khi xoay -> dung de thu bao loi.
writeFileSync('loi-qua-kho-60x59.pdf', buildPdf(60, 59, '60x59cm QUA KHO', 7))
console.log('  loi-qua-kho-60x59.pdf')

// Logo dung de thu co "khong cho xoay".
writeFileSync('logo-30x5.pdf', buildPdf(30, 5, 'LOGO 30x5cm', 6))
console.log('  logo-30x5.pdf')

// Anh 600x400 px @ 300 DPI = 50.8 x 33.87 mm.
writeFileSync('anh-600x400-300dpi.png', buildPng(600, 400, 300, [0.033, 0.569, 0.698]))
console.log('  anh-600x400-300dpi.png')
