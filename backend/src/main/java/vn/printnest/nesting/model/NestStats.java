package vn.printnest.nesting.model;

/**
 * So lieu tong hop cua mot lan ghep.
 *
 * @param totalSheets           so tam (= so file PDF xuat ra)
 * @param totalLengthMm         tong chieu dai cuon phai dung
 * @param totalShapeAreaMm2     tong dien tich cac hinh
 * @param usedAreaMm2           tong dien tich giay da dung (rong kho x tong chieu dai)
 * @param fillRate              ty le lap day 0..1
 * @param savedVsIndividualPct  phan tram tiet kiem so voi in roi tung hinh
 * @param totalPieces           tong so ban in da dat (phai bang tong so luong yeu cau)
 * @param byType                so lieu tach theo tung loai hinh, sap theo categoryIndex
 */
public record NestStats(
        int totalSheets,
        double totalLengthMm,
        double totalShapeAreaMm2,
        double usedAreaMm2,
        double fillRate,
        double savedVsIndividualPct,
        int totalPieces,
        java.util.List<TypeStats> byType
) {
}
