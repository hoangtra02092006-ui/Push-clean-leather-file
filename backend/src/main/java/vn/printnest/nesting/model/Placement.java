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
 */
public record Placement(
        String fileId,
        String label,
        int categoryIndex,
        double xMm,
        double yMm,
        double wMm,
        double hMm,
        boolean rotated
) {
}
