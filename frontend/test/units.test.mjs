/**
 * Kiem thu phan doc so tu o nhap.
 *
 * Chay:  npm run test
 *
 * Bai test nay sinh ra tu mot loi that: o nhap so o buoc 2 nem TypeError moi lan go, nen
 * kho ngang / le / khoang cach / chieu dai toi da KHONG BAO GIO doi duoc - nguoi dung sua
 * so tren man hinh ma thuat toan van chay bang gia tri mac dinh.
 */
import { parseDecimalInput, cmToMm, mmToCm, mmToM, formatM } from '../dist-test/units.mjs'

let pass = 0
let fail = 0

function check(label, actual, expected) {
  const ok = Object.is(actual, expected)
  ok ? pass++ : fail++
  console.log(`  ${ok ? 'OK  ' : 'HONG'} ${label}: ${JSON.stringify(actual)}`
    + (ok ? '' : ` (mong doi ${JSON.stringify(expected)})`))
}

console.log('parseDecimalInput:')
// Vue ep gia tri sang so voi <input type="number"> - phai chiu duoc ca hai kieu.
check('nhan so 5', parseDecimalInput(5), 5)
check('nhan chuoi "5"', parseDecimalInput('5'), 5)
check('dau cham "0.3"', parseDecimalInput('0.3'), 0.3)
check('dau phay kieu VN "2,5"', parseDecimalInput('2,5'), 2.5)
check('co khoang trang " 57 "', parseDecimalInput(' 57 '), 57)
check('o trong', parseDecimalInput(''), null)
check('null', parseDecimalInput(null), null)
check('chu vo nghia', parseDecimalInput('abc'), null)
check('NaN', parseDecimalInput(Number.NaN), null)

console.log('\nquy doi don vi:')
check('5 cm -> mm', cmToMm(5), 50)
check('3 mm -> cm', mmToCm(3), 0.3)
check('1750 mm -> m', mmToM(1750), 1.75)
// Giay cuon tinh tien theo met dai nen bang ket qua de song song ca cm lan m.
check('hien thi met 2 chu so', formatM(1750), '1.75')
check('hien thi met lam tron', formatM(1301), '1.30')

console.log(`\n${pass} dat / ${fail} hong`)
process.exit(fail === 0 ? 0 : 1)
