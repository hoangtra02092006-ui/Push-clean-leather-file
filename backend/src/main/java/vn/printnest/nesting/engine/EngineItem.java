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
 * @param categoryIndex thu tu loai hinh, dung de to mau preview
 */
public record EngineItem(
        String fileId,
        String label,
        double widthMm,
        double heightMm,
        int quantity,
        boolean allowRotate,
        int categoryIndex
) {
}
