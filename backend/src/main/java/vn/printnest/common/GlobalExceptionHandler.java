package vn.printnest.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

/**
 * Bien moi ngoai le thanh mot cau truc JSON duy nhat kem HTTP status dung ngu nghia.
 *
 * <p>Nho tap trung o day, frontend chi can xu ly mot dang loi va luon co
 * {@code error.code} de phan nhanh.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Loi nghiep vu da biet truoc. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex) {
        log.warn("Loi nghiep vu [{}]: {}", ex.code(), ex.getMessage());
        return ResponseEntity.status(ex.code().status())
                .body(ApiErrorResponse.of(ex.code(), ex.getMessage(), ex.details()));
    }

    /** Tham so gui len khong qua duoc @Valid. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> fields = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fields.put(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.status())
                .body(ApiErrorResponse.of(ErrorCode.INVALID_REQUEST,
                        "Tham so gui len khong hop le.", fields));
    }

    /** File vuot qua gioi han cua Spring Multipart. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(ErrorCode.FILE_TOO_LARGE.status())
                .body(ApiErrorResponse.of(ErrorCode.FILE_TOO_LARGE,
                        "File vuot qua dung luong cho phep.", null));
    }

    /** Luoi an toan cuoi cung: khong de lo stack trace ra ngoai. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        log.error("Loi khong luong truoc", ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(ApiErrorResponse.of(ErrorCode.INTERNAL_ERROR,
                        "Da co loi khong mong muon o may chu.", null));
    }
}
