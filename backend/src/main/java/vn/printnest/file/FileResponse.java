package vn.printnest.file;

/**
 * Thong tin file tra ve cho frontend sau khi upload.
 *
 * @param id           ma file
 * @param originalName ten goc
 * @param widthMm      chieu rong vat ly
 * @param heightMm     chieu cao vat ly
 * @param type         "PDF" hoac "IMAGE"
 * @param pageCount    so trang
 * @param previewUrl   duong dan lay anh xem truoc
 */
public record FileResponse(
        String id,
        String originalName,
        double widthMm,
        double heightMm,
        String type,
        int pageCount,
        String previewUrl
) {
    public static FileResponse from(StoredFile file) {
        return new FileResponse(
                file.id(),
                file.originalName(),
                file.widthMm(),
                file.heightMm(),
                file.type().name(),
                file.pageCount(),
                "/api/v1/files/" + file.id() + "/preview");
    }
}
