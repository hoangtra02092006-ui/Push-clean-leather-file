package vn.printnest.file;

/**
 * Vung co net ve that su cua mot file nguon, trong he toa do goc cua file do.
 *
 * <p>Voi PDF: don vi la POINT, goc toa do la goc trai-duoi cua trang.
 *
 * <p>Giu lai hop nay la bat buoc chu khong phai cho vui: sau khi cat bo khoang trang,
 * kich thuoc bao ra ngoai la kich thuoc cua HOP NAY, nen luc dung file PDF thanh pham
 * phai dich dung goc hop nay ve vi tri dat. Neu khong, hinh se bi lech di dung bang
 * khoang trang da cat.
 *
 * @param xPt      toa do X goc trai-duoi
 * @param yPt      toa do Y goc trai-duoi
 * @param widthPt  chieu rong
 * @param heightPt chieu cao
 */
public record ContentBox(double xPt, double yPt, double widthPt, double heightPt) {
}
