package vn.printnest.common;

import java.util.Map;

/**
 * Ngoai le nghiep vu mang theo {@link ErrorCode} va thong diep tieng Viet cho nguoi dung.
 *
 * <p>Moi truong hop tra loi cho client deu di qua lop nay de
 * {@link GlobalExceptionHandler} dinh dang thanh mot cau truc JSON duy nhat.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient Map<String, Object> details;

    public ApiException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public ApiException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
