package vn.printnest.nesting.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

/**
 * Tham so dau vao cua mot job ghep file. Moi kich thuoc deu la MILIMET.
 *
 * @param sheetWidthMm      chieu rong kho cuon (bat buoc, > 0)
 * @param marginMm          le bien moi phia
 * @param gapMm             khoang cach toi thieu giua hai hinh bat ky
 * @param maxSheetLengthMm  gioi han chieu dai moi file xuat ra; null = khong gioi han
 * @param mode              cach sap xep: chi xoay 90 do, hay cho phep xep long theo hinh that
 * @param allowRotateGlobal cong tac xoay toan cuc; tat se ghi de xuong tung item
 * @param drawCutLines      co ve duong cat mo quanh moi hinh trong PDF khong
 * @param items             danh sach hinh can ghep
 */
public record NestRequest(
        @Positive(message = "Kho ngang phai lon hon 0") double sheetWidthMm,
        @PositiveOrZero(message = "Le bien khong duoc am") double marginMm,
        @PositiveOrZero(message = "Khoang cach khong duoc am") double gapMm,
        @Positive(message = "Chieu dai toi da phai lon hon 0") Double maxSheetLengthMm,
        NestingMode mode,
        boolean allowRotateGlobal,
        Boolean drawCutLines,
        @NotEmpty(message = "Danh sach hinh khong duoc rong")
        @Valid List<NestItemRequest> items
) {
    /** Mac dinh bat duong cat neu client khong gui co nay. */
    public boolean drawCutLinesOrDefault() {
        return drawCutLines == null || drawCutLines;
    }

    /**
     * Che do sap xep, mac dinh la { NestingMode#ORTHOGONAL}.
     *
     * <p>Mac dinh chon che do cu co chu y: day la che do da do ky va cho bo tri de cat.
     * Muon xep long thi phai chon ro rang.
     */
    public NestingMode modeOrDefault() {
        return mode == null ? NestingMode.ORTHOGONAL : mode;
    }
}
