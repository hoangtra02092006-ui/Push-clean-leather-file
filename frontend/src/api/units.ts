/**
 * Quy doi don vi giua tang hien thi va tang API.
 *
 * Backend lam viec hoan toan bang MILIMET. Nguoi dung o xuong quen nghi bang CENTIMET.
 * Toan bo viec doi don vi tap trung o day de khong lap lai `/10` rai rac khap component -
 * cho de quen nhat khi them man hinh moi.
 */

/** Milimet -> centimet. */
export function mmToCm(mm: number): number {
  return mm / 10
}

/** Centimet -> milimet. */
export function cmToMm(cm: number): number {
  return cm * 10
}

/** Hien thi so cm gon gang: bo duoi 0 thua, toi da 2 chu so thap phan. */
export function formatCm(mm: number, digits = 1): string {
  const value = mmToCm(mm)
  return value.toFixed(digits).replace(/\.0+$/, '').replace(/(\.\d*[1-9])0+$/, '$1')
}

/** Hien thi dien tich mm2 duoi dang cm2 co dau phan cach hang nghin. */
export function formatAreaCm2(mm2: number): string {
  return Math.round(mm2 / 100).toLocaleString('vi-VN')
}

/** Hien thi ty le 0..1 thanh phan tram. */
export function formatPercent(ratio: number, digits = 1): string {
  return (ratio * 100).toFixed(digits)
}

/** Hien thi so nguyen co dau phan cach hang nghin. */
export function formatCount(value: number): string {
  return value.toLocaleString('vi-VN')
}

/**
 * Doc mot so tu chuoi nguoi dung go vao o nhap.
 *
 * <p>Tach rieng ra khoi component de co the kiem thu duoc. Mot lan o nhap so bi hong am
 * tham suot nhieu phien ban ma khong ai biet, vi khong co cho nao kiem duoc phan nay.
 *
 * <p>Chap nhan ca dau phay lan dau cham lam dau thap phan: nguoi Viet go "2,5" theo thoi
 * quen, va khong co ly do gi bat ho doi thoi quen.
 *
 * @param raw chuoi tho tu o nhap; cung chap nhan so phong khi ben goi da tu doi kieu
 * @returns so doc duoc, hoac null neu o trong / khong doc duoc
 */
export function parseDecimalInput(raw: string | number | null | undefined): number | null {
  if (typeof raw === 'number') {
    return Number.isNaN(raw) ? null : raw
  }
  const trimmed = String(raw ?? '').trim()
  if (trimmed === '') {
    return null
  }
  const parsed = Number(trimmed.replace(',', '.'))
  return Number.isNaN(parsed) ? null : parsed
}
