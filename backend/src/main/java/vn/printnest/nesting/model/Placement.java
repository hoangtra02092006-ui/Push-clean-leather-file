package vn.printnest.nesting.model;

/**
 * Vi tri dat mot ban in tren tam, don vi milimet, goc toa do la goc TRAI-DUOI cua tam.
 *
 * @param fileId        ma file nguon
 * @param label         ten hien thi
 * @param categoryIndex thu tu loai hinh (0..n), frontend dung de to mau preview
 * @param xMm           toa do X goc trai-duoi cua hinh
 * @param yMm           toa do Y goc trai-duoi cua hinh
 * @param wMm           chieu rong sau khi xoay (neu co)
 * @param hMm           chieu cao sau khi xoay (neu co)
  * @param rotated       hinh co bi xoay 90 do hay khong
 * @param angleDeg      goc xoay tu do, don vi do nguoc chieu kim dong ho. Chi khac 0 o
 *                      che do xep long theo hinh that; luc do {@code rotated} luon false
 * @param sourceWMm     chieu rong hinh TRUOC khi xoay - dung de tinh ty le phong khi ve
 *                      ra PDF. Khong co truong nay thi voi goc cheo se tinh nham ty le
 *                      va lam meo hinh
 * @param sourceHMm     chieu cao hinh truoc khi xoay
 */
public record Placement(
        String fileId,
        String label,
        int categoryIndex,
        double xMm,
        double yMm,
        double wMm,
        double hMm,
        boolean rotated,
        double angleDeg,
        double sourceWMm,
        double sourceHMm
) {

    /**
     * Ban rut gon cho cac che do chi xoay 0 hoac 90 do.
     *
     * <p>Kich thuoc chua xoay suy nguoc tu co {@code rotated}: xoay 90 do thi w/h da bi
     * hoan doi nen phai doi lai.
     */
    public Placement(String fileId, String label, int categoryIndex, double xMm, double yMm,
                     double wMm, double hMm, boolean rotated) {
        this(fileId, label, categoryIndex, xMm, yMm, wMm, hMm, rotated, 0,
                rotated ? hMm : wMm, rotated ? wMm : hMm);
    }
}
