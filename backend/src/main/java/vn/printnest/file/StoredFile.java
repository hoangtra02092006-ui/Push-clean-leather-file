package vn.printnest.file;

/**
 * Mot file da upload va doc xong kich thuoc vat ly.
 *
 * @param id           ma sinh tu dong
 * @param originalName ten file goc nguoi dung upload
 * @param storedPath   duong dan tuyet doi tren dia
 * @param type         PDF hay anh
 * @param widthMm      chieu rong DUNG DE XEP - da cat bo khoang trang bao quanh
 * @param heightMm     chieu cao DUNG DE XEP - da cat bo khoang trang bao quanh
 * @param sourceWidthMm  chieu rong nguyen ban cua kho trang, truoc khi cat
 * @param sourceHeightMm chieu cao nguyen ban cua kho trang, truoc khi cat
 * @param contentBox   vung co net ve trong he toa do goc; null neu khong cat (anh)
 * @param occupancy    ban do cho nao co net ve ben trong khung bao; null voi anh
 *                     (anh khong doc duoc vector nen luon coi la dac)
 * @param pageRotation goc xoay khai bao o trang PDF: 0, 90, 180 hoac 270
 * @param pageCount    so trang (anh luon la 1)
 * @param opaqueRaster anh DET khong co kenh trong suot (anh JPG chang han). Chi rieng
 *                     loai nay moi coi mang trang bao quanh la nen can bo; PDF va anh
 *                     co kenh trong suot thi mau trang la net ve co chu y
 * @param sizeBytes    dung luong file
 * @param uploadedAt   thoi diem nhan file, dung de biet khi nao duoc phep don di
 */
public record StoredFile(
        String id,
        String originalName,
        String storedPath,
        FileType type,
        double widthMm,
        double heightMm,
        double sourceWidthMm,
        double sourceHeightMm,
        ContentBox contentBox,
        OccupancyMask occupancy,
        int pageRotation,
        int pageCount,
        boolean opaqueRaster,
        long sizeBytes,
        java.time.Instant uploadedAt
) {
    /** Co cat bo duoc khoang trang nao khong (chenh lech dang ke so voi kho goc). */
    public boolean trimmed() {
        return sourceWidthMm - widthMm > 0.5 || sourceHeightMm - heightMm > 0.5;
    }
}
