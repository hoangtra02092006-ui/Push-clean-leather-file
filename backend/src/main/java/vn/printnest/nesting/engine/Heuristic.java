package vn.printnest.nesting.engine;

/**
 * Cac ham cham diem vi tri dat cua thuat toan MaxRects.
 *
 * <p>Voi moi o trong kha dung, packer tinh cap diem (primary, secondary); vi tri co
 * diem nho nhat duoc chon. Chay ca 4 heuristic roi lay ket qua tot nhat vi khong
 * heuristic nao thang tuyet doi tren moi bo du lieu.
 */
public enum Heuristic {
    /** Best Short Side Fit: uu tien o trong sat canh ngan nhat. */
    BSSF,
    /** Best Long Side Fit: uu tien o trong sat canh dai nhat. */
    BLSF,
    /** Best Area Fit: uu tien o trong co dien tich thua it nhat. */
    BAF,
    /** Bottom-Left: uu tien vi tri thap nhat, sau do sang trai nhat. */
    BL
}
