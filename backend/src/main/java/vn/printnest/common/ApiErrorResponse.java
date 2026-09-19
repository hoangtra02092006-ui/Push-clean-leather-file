package vn.printnest.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Cau truc loi duy nhat tra ve cho client: {@code { "error": { code, message, details? } }}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(ErrorBody error) {

    /**
     * @param code    ma loi, xem {@link ErrorCode}
     * @param message thong diep tieng Viet hien thang cho nguoi dung
     * @param details thong tin bo sung (vi du hinh nao gay loi)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorBody(String code, String message, Map<String, Object> details) {
    }

    public static ApiErrorResponse of(ErrorCode code, String message, Map<String, Object> details) {
        return new ApiErrorResponse(new ErrorBody(code.name(), message, details));
    }
}
