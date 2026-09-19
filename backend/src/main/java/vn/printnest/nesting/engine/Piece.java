package vn.printnest.nesting.engine;

/**
 * Mot ban in can xep, da duoc "no" them khoang cach gap.
 *
 * <p>Moi truong hop cua mot item (quantity = 5 sinh ra 5 Piece) la mot doi tuong rieng.
 * Kich thuoc {@code w}/{@code h} la kich thuoc DA CONG gap; kich thuoc that de ve
 * len PDF la {@code realW}/{@code realH}.
 *
 * @param id     ma so duy nhat trong mot lan chay (dung de sap xep on dinh)
 * @param fileId ma file nguon
 * @param label  nhan hien thi (ten file)
 * @param w      chieu rong da no gap, don vi 1/100 mm
 * @param h      chieu cao da no gap, don vi 1/100 mm
 * @param realW  chieu rong that, don vi 1/100 mm
 * @param realH  chieu cao that, don vi 1/100 mm
 * @param allowRotate co duoc xoay 90 do hay khong
 * @param categoryIndex thu tu loai hinh, dung de to mau o preview
 * @param typeIndex     thu tu dong trong danh sach yeu cau, dung khi thu cac phuong an
 *                      co dinh huong theo tung loai hinh
 * @param prerotated    hinh da bi hoan doi w/h truoc khi xep (phuong an ep huong)
 */
public record Piece(
        int id,
        String fileId,
        String label,
        int w,
        int h,
        int realW,
        int realH,
        boolean allowRotate,
        int categoryIndex,
        int typeIndex,
        boolean prerotated
) {
    /** Ban sao da xoay 90 do, dung cho cac phuong an ep huong toan loai hinh. */
    public Piece rotated90() {
        return new Piece(id, fileId, label, h, w, realH, realW,
                allowRotate, categoryIndex, typeIndex, !prerotated);
    }

    public long area() {
        return (long) w * h;
    }

    public int longSide() {
        return Math.max(w, h);
    }

    public int shortSide() {
        return Math.min(w, h);
    }
}
