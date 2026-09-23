package vn.printnest.common;

import org.springframework.http.HttpStatus;

/**
 * Bo ma loi thong nhat giua backend va frontend.
 *
 * <p>Frontend dua vao {@code name()} de hien thong bao tieng Viet phu hop, khong parse
 * chuoi message.
 */
public enum ErrorCode {

    /** File vuot qua gioi han dung luong cau hinh. */
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    /** Dinh dang file khong nam trong PDF / PNG / JPG. */
    UNSUPPORTED_FORMAT(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    /** Khong doc duoc kich thuoc vat ly tu file. */
    SIZE_UNREADABLE(HttpStatus.UNPROCESSABLE_ENTITY),
    /** Co hinh rong hon kho cuon, ke ca sau khi xoay. */
    ITEM_WIDER_THAN_SHEET(HttpStatus.UNPROCESSABLE_ENTITY),
    /** Khong tim thay job. */
    JOB_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** Khong tim thay file. */
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** Job chua chay xong nen chua co ket qua de tai. */
    JOB_NOT_READY(HttpStatus.CONFLICT),
    /** Tham so dau vao khong hop le. */
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    /** Thuat toan that bai vi ly do khong luong truoc. */
    NESTING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR),
    /** Tam qua lon de raster hoa thanh TIFF o do phan giai dang dat. */
    TIFF_TOO_LARGE(HttpStatus.UNPROCESSABLE_ENTITY),
    /** Loi khong xac dinh. */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
