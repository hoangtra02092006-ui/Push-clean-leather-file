package vn.printnest.nesting.engine;

/**
 * Mot loai hinh da duoc giai quyet xong kich thuoc, san sang dua vao thuat toan.
 *
 * <p>Khac voi DTO tang API, o day kich thuoc da duoc chot (uu tien gia tri nguoi dung
 * ghi de, neu khong thi lay tu metadata file) va {@code allowRotate} da gop voi cong
 * tac xoay toan cuc.
 *
 * @param fileId        ma file nguon
 * @param label         ten hien thi
 * @param widthMm       chieu rong that
 * @param heightMm      chieu cao that
 * @param quantity      so ban can in
 * @param allowRotate   co duoc xoay 90 do khong
 * @param shape         hinh dang that duoi dang mat na luoi; null nghia la coi nhu
 *                      khung bao dac hoan toan
 * @param categoryIndex thu tu loai hinh, dung de to mau preview
 * @param cavities      cac o trong ben trong khung bao, don vi MILIMET, toa do theo goc
 *                      trai-duoi cua khung bao. Moi diem trong o da cach net ve it nhat
 *                      mot gap, nen hinh khac dat lot vao do la an toan.
 */
public record EngineItem(
        String fileId,
        String label,
        double widthMm,
        double heightMm,
        int quantity,
        boolean allowRotate,
        int categoryIndex,
        java.util.List<CavityMm> cavities,
        ShapeMask shape
) {

    /** Mot o trong, don vi milimet. */
    public record CavityMm(double xMm, double yMm, double widthMm, double heightMm) {
    }

    /** Ban rut gon cho cac cho goi khong quan tam toi hoc lom (test, hinh dac). */
    public EngineItem(String fileId, String label, double widthMm, double heightMm,
                      int quantity, boolean allowRotate, int categoryIndex) {
        this(fileId, label, widthMm, heightMm, quantity, allowRotate, categoryIndex,
                java.util.List.of(), null);
    }

    /** Ban chi khai hoc lom, chua can hinh dang that. */
    public EngineItem(String fileId, String label, double widthMm, double heightMm,
                      int quantity, boolean allowRotate, int categoryIndex,
                      java.util.List<CavityMm> cavities) {
        this(fileId, label, widthMm, heightMm, quantity, allowRotate, categoryIndex,
                cavities, null);
    }
}
