package vn.printnest.nesting.engine;

/**
 * Ket qua dat mot {@link Piece} vao kho: goc trai-duoi cua hinh chu nhat DA NO gap.
 *
 * @param piece   ban in goc
 * @param x       toa do X cua o da no gap, don vi 1/100 mm, goc toa do la goc vung kha dung
 * @param y       toa do Y cua o da no gap, don vi 1/100 mm
 * @param w       chieu rong o da no gap sau khi xoay (neu co)
 * @param h       chieu cao o da no gap sau khi xoay (neu co)
 * @param rotated hinh co bi xoay 90 do hay khong
 */
public record PlacedPiece(Piece piece, int x, int y, int w, int h, boolean rotated) {

    /** Canh phai cua o (khong bao gom). */
    public int right() {
        return x + w;
    }

    /** Canh tren cua o (khong bao gom). */
    public int top() {
        return y + h;
    }
}
