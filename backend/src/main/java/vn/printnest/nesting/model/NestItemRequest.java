package vn.printnest.nesting.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Mot dong trong danh sach can ghep.
 *
 * @param fileId      ma file da upload
 * @param quantity    so ban can in
 * @param allowRotate co cho phep xoay 90 do rieng cho item nay khong
 * @param widthMm     kich thuoc ghi de thu cong (null = dung kich thuoc doc tu file)
 * @param heightMm    kich thuoc ghi de thu cong (null = dung kich thuoc doc tu file)
 */
public record NestItemRequest(
        @NotBlank(message = "Thieu ma file") String fileId,
        @Min(value = 1, message = "So luong toi thieu la 1") int quantity,
        boolean allowRotate,
        @Positive(message = "Chieu rong phai lon hon 0") Double widthMm,
        @Positive(message = "Chieu cao phai lon hon 0") Double heightMm
) {
}
