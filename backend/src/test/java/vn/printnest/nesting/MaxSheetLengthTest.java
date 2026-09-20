package vn.printnest.nesting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.printnest.nesting.engine.EngineInput;
import vn.printnest.nesting.engine.EngineItem;
import vn.printnest.nesting.engine.NestingEngine;
import vn.printnest.nesting.model.NestResult;
import vn.printnest.nesting.model.Sheet;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiem thu gioi han "chieu dai toi da moi file".
 *
 * <p>Day la mot loi KHONG BAO GIO duoc phep xay ra am tham: tho dat 200 cm vi may in
 * khong nuot noi file dai hon, ma app van tra ve mot file 205 cm thi ho chi phat hien
 * khi file da o tren may.
 *
 * <p>Vi vay ngoai vai ca cu the, o day con co mot bai quet NGAU NHIEN co hat co dinh:
 * no la thu duy nhat bat duoc loi goc, vi loi chi lo ra o che do xep long voi khoang
 * cach giua cac hinh lon.
 */
class MaxSheetLengthTest {

    private static final double SHEET_WIDTH_MM = 570;

    private final NestingEngine engine = new NestingEngine();

    /**
     * Ca do duoc tu ban quet: gioi han 50 cm, hinh 7 x 3 cm, khoang cach 5 cm.
     *
     * <p>Truoc khi sua, tam dau tien ra 52,0 cm - vuot dung bang mot khoang cach.
     */
    @Test
    @DisplayName("Xep long theo hinh that: khong tam nao duoc dai hon gioi han")
    void trueShapeRespectsLimit() {
        double limitMm = 500;
        NestResult result = engine.nest(new EngineInput(
                SHEET_WIDTH_MM, 5, 50, limitMm, true,
                List.of(new EngineItem("f", "nhan", 70, 30, 45, true, 0)),
                true));

        assertThat(result.sheets()).isNotEmpty();
        for (Sheet sheet : result.sheets()) {
            assertThat(sheet.lengthMm())
                    .as("chieu dai tam %d", sheet.index() + 1)
                    .isLessThanOrEqualTo(limitMm);
        }
    }

    /** Ca thu hai tu ban quet: gioi han 80 cm, khoang cach nho - truoc khi sua ra 80,2 cm. */
    @Test
    @DisplayName("Xep long theo hinh that: gap nho van khong duoc tran qua gioi han")
    void trueShapeRespectsLimitWithSmallGap() {
        double limitMm = 800;
        NestResult result = engine.nest(new EngineInput(
                SHEET_WIDTH_MM, 5, 3, limitMm, true,
                List.of(new EngineItem("f", "logo", 300, 50, 45, true, 0)),
                true));

        for (Sheet sheet : result.sheets()) {
            assertThat(sheet.lengthMm())
                    .as("chieu dai tam %d", sheet.index() + 1)
                    .isLessThanOrEqualTo(limitMm);
        }
    }

    @Test
    @DisplayName("Xep luoi chu nhat: khong tam nao duoc dai hon gioi han")
    void orthogonalRespectsLimit() {
        double limitMm = 2000;
        NestResult result = engine.nest(new EngineInput(
                SHEET_WIDTH_MM, 5, 3, limitMm, true,
                List.of(new EngineItem("f", "nhan", 200, 180, 40, true, 0)),
                false));

        assertThat(result.sheets().size()).isGreaterThan(1);
        for (Sheet sheet : result.sheets()) {
            assertThat(sheet.lengthMm()).isLessThanOrEqualTo(limitMm);
        }
    }

    /**
     * Quet nhieu cau hinh tron nhieu loai hinh.
     *
     * <p>Hat co dinh nen bai test chay lai bao nhieu lan cung ra dung nhung cau hinh do.
     * Neu mot ngay nao do cong thuc tinh chieu dai bi doi lech mot chut, bai nay do ngay.
     */
    @Test
    @DisplayName("Quet nhieu cau hinh: khong cau hinh nao tra ve tam vuot gioi han")
    void randomisedSweepRespectsLimit() {
        Random random = new Random(20260920);
        List<String> failures = new ArrayList<>();
        int checked = 0;

        for (int trial = 0; trial < 22; trial++) {
            double limitCm = 40 + random.nextInt(24) * 10;
            double gapMm = new double[]{0, 1, 3, 10, 30, 50}[random.nextInt(6)];
            double marginMm = new double[]{0, 5, 20, 50}[random.nextInt(4)];
            int types = 1 + random.nextInt(2);

            List<EngineItem> items = new ArrayList<>();
            for (int t = 0; t < types; t++) {
                double widthCm = 2 + random.nextInt(400) / 10.0;
                double heightCm = 2 + random.nextInt(400) / 10.0;
                items.add(new EngineItem("f" + t, "hinh" + t,
                        widthCm * 10, heightCm * 10, 1 + random.nextInt(10), true, t));
            }

            for (boolean trueShape : new boolean[]{false, true}) {
                NestResult result;
                try {
                    result = engine.nest(new EngineInput(
                            SHEET_WIDTH_MM, marginMm, gapMm, limitCm * 10, true, items, trueShape));
                } catch (RuntimeException ex) {
                    // Tu choi thang thung (hinh cao hon gioi han, kho qua hep...) la dung;
                    // chi tra ve tam qua dai moi la loi.
                    continue;
                }
                checked++;
                for (Sheet sheet : result.sheets()) {
                    if (sheet.lengthMm() > limitCm * 10 + 0.001) {
                        failures.add(String.format(
                                "%s | gioi han %.0fcm le %.0fmm gap %.0fmm -> tam%d = %.1fcm",
                                trueShape ? "TRUE_SHAPE" : "ORTHOGONAL", limitCm, marginMm, gapMm,
                                sheet.index() + 1, sheet.lengthMm() / 10));
                    }
                }
            }
        }

        assertThat(checked).as("so cau hinh thuc su chay duoc").isGreaterThan(20);
        assertThat(failures).as("cac cau hinh tra ve tam vuot gioi han").isEmpty();
    }
}
