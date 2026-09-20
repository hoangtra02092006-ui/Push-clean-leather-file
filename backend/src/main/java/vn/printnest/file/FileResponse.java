package vn.printnest.file;

import vn.printnest.common.Units;

/**
 * Thong tin file tra ve cho frontend sau khi upload.
 *
 * @param id             ma file
 * @param originalName   ten goc
 * @param widthMm        chieu rong dung de xep (da cat khoang trang)
 * @param heightMm       chieu cao dung de xep (da cat khoang trang)
 * @param sourceWidthMm  chieu rong kho trang nguyen ban
 * @param sourceHeightMm chieu cao kho trang nguyen ban
 * @param trimmed        da cat bo khoang trang hay chua - giao dien hien chu thich
 * @param type           "PDF" hoac "IMAGE"
 * @param pageCount      so trang
 * @param previewUrl     duong dan lay anh xem truoc
 */
public record FileResponse(
        String id,
        String originalName,
        double widthMm,
        double heightMm,
        double sourceWidthMm,
        double sourceHeightMm,
        boolean trimmed,
        String type,
        int pageCount,
        String previewUrl
) {
    public static FileResponse from(StoredFile file) {
        return new FileResponse(
                file.id(),
                file.originalName(),
                Units.round2(file.widthMm()),
                Units.round2(file.heightMm()),
                Units.round2(file.sourceWidthMm()),
                Units.round2(file.sourceHeightMm()),
                file.trimmed(),
                file.type().name(),
                file.pageCount(),
                "/api/v1/files/" + file.id() + "/preview");
    }
}
