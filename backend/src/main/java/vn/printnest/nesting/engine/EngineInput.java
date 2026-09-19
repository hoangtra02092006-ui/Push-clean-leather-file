package vn.printnest.nesting.engine;

import java.util.List;

/**
 * Dau vao thuan tuy cua thuat toan - khong phu thuoc Spring hay tang web, nho vay
 * unit test goi thang duoc.
 *
 * @param sheetWidthMm     chieu rong kho cuon
 * @param marginMm         le bien moi phia
 * @param gapMm            khoang ho toi thieu giua hai hinh
 * @param maxSheetLengthMm gioi han chieu dai moi tam; null = khong gioi han
 * @param allowRotateGlobal cong tac xoay toan cuc
 * @param items            danh sach loai hinh
 */
public record EngineInput(
        double sheetWidthMm,
        double marginMm,
        double gapMm,
        Double maxSheetLengthMm,
        boolean allowRotateGlobal,
        List<EngineItem> items
) {
}
