package vn.printnest.file;

/**
 * Mot file da upload va doc xong kich thuoc vat ly.
 *
 * @param id           ma sinh tu dong
 * @param originalName ten file goc nguoi dung upload
 * @param storedPath   duong dan tuyet doi tren dia
 * @param type         PDF hay anh
 * @param widthMm      chieu rong vat ly doc duoc, don vi milimet
 * @param heightMm     chieu cao vat ly doc duoc, don vi milimet
 * @param pageCount    so trang (anh luon la 1)
 * @param sizeBytes    dung luong file
 */
public record StoredFile(
        String id,
        String originalName,
        String storedPath,
        FileType type,
        double widthMm,
        double heightMm,
        int pageCount,
        long sizeBytes
) {
}
