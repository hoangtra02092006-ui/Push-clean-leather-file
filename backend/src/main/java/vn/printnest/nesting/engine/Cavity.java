package vn.printnest.nesting.engine;

/**
 * Mot o TRONG nam ben trong khung bao cua mot hinh.
 *
 * <p>Vi du de hinh dung: mot hinh co khung bao vuong 10x10 cm nhung net ve that chi choan
 * hinh chu L, goc tren ben phai bo trong 5x5 cm. Truoc day ca o 10x10 bi coi la dac, khong
 * ai duoc dat vao goc trong do. Hoc lom cho phep tra lai phan goc ay cho thuat toan, de
 * mot hinh nho khac chui vua vao.
 *
 * <p><b>Bat bien quan trong:</b> moi diem cua o nay da cach net ve cua hinh chu it nhat
 * {@code gap}. Nho vay bat cu hinh nao duoc dat LOT HAN trong o nay deu tu dong du khoang
 * ho voi hinh chu, khong can kiem tra gi them. Viec bao dam dieu do thuoc ve khau trich
 * xuat hoc lom tu file nguon, khong phai viec cua packer.
 *
 * <p>Toa do tinh theo goc trai-duoi cua khung bao hinh chu, don vi 1/100 mm.
 *
 * @param x toa do X trong he toa do cua hinh chu
 * @param y toa do Y trong he toa do cua hinh chu
 * @param w chieu rong o trong
 * @param h chieu cao o trong
 */
public record Cavity(int x, int y, int w, int h) {

    /**
     * Ban sao khi hinh chu bi xoay 90 do trong packer.
     *
     * <p>Packer xoay hinh bang cach hoan doi w/h; phep xoay that la 90 do nguoc chieu kim
     * dong ho roi day sang phai mot chieu rong - dung khop voi cach {@code PdfComposer} ve
     * ra file. Diem {@code (u,v)} cua hinh chu di toi {@code (H - v, u)} voi {@code H} la
     * chieu cao hinh chu truoc khi xoay.
     *
     * @param hostHeight chieu cao khung bao hinh chu TRUOC khi xoay
     */
    public Cavity rotated90(int hostHeight) {
        return new Cavity(hostHeight - y - h, x, h, w);
    }
}
