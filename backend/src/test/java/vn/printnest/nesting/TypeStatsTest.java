package vn.printnest.nesting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.printnest.nesting.engine.EngineInput;
import vn.printnest.nesting.engine.EngineItem;
import vn.printnest.nesting.engine.NestingEngine;
import vn.printnest.nesting.model.NestResult;
import vn.printnest.nesting.model.Placement;
import vn.printnest.nesting.model.Sheet;
import vn.printnest.nesting.model.TypeStats;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Kiem thu bang so lieu tach theo tung loai hinh.
 *
 * <p>Bang nay khong tu tinh lai gi ca - no chi gom cac ban in da dat. Vi vay dieu phai
 * chung minh khong phai "con so co dep khong" ma la <b>no co khop voi bang tong hop
 * khong</b>: cong tat ca cac loai lai phai ra dung con so tong. Lech mot chut la o dau
 * do dang dem thieu hoac dem thua ban in.
 */
class TypeStatsTest {

    private static final double SHEET_WIDTH_MM = 570;
    private static final double EPS = 0.0002;

    private final NestingEngine engine = new NestingEngine();

    /** Ba loai hinh - dung tinh huong nguoi dung mo ta: tai len 3 hinh roi xem thong ke. */
    private static List<EngineItem> threeTypes() {
        return List.of(
                new EngineItem("f1", "the-25x14.9", 250, 149, 15, true, 0),
                new EngineItem("f2", "nhan-5x4.6", 50, 46, 10, true, 1),
                new EngineItem("f3", "nhan-4.4x2.8", 44, 28, 20, true, 2));
    }

    @Test
    @DisplayName("Moi loai hinh tai len deu co mot dong trong bang so lieu")
    void everyTypeGetsARow() {
        NestResult result = engine.nest(new EngineInput(
                SHEET_WIDTH_MM, 5, 3, null, true, threeTypes(), false));

        List<TypeStats> byType = result.stats().byType();

        assertThat(byType).hasSize(3);
        assertThat(byType).extracting(TypeStats::label)
                .containsExactly("the-25x14.9", "nhan-5x4.6", "nhan-4.4x2.8");
        assertThat(byType).extracting(TypeStats::pieces)
                .containsExactly(15, 10, 20);
        // Thu tu phai theo categoryIndex tang dan - preview to mau theo khoa nay.
        assertThat(byType).extracting(TypeStats::categoryIndex)
                .containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("Cong trong so cua moi loai lai dung bang 1")
    void sharesAddUpToOne() {
        NestResult result = engine.nest(new EngineInput(
                SHEET_WIDTH_MM, 5, 3, null, true, threeTypes(), false));

        double sum = result.stats().byType().stream()
                .mapToDouble(TypeStats::shareOfShapes).sum();

        assertThat(sum).as("tong trong so cac loai").isCloseTo(1.0, within(EPS));
    }

    @Test
    @DisplayName("Cong ty le lap day cua moi loai lai dung bang ty le lap day chung")
    void fillRatesAddUpToOverallFillRate() {
        NestResult result = engine.nest(new EngineInput(
                SHEET_WIDTH_MM, 5, 3, null, true, threeTypes(), false));

        double sum = result.stats().byType().stream()
                .mapToDouble(TypeStats::fillRate).sum();

        assertThat(sum).as("tong ty le lap day cua cac loai")
                .isCloseTo(result.stats().fillRate(), within(EPS));
    }

    @Test
    @DisplayName("Tong so ban in va tong dien tich cua cac loai khop voi bang tong hop")
    void totalsMatchOverallStats() {
        NestResult result = engine.nest(new EngineInput(
                SHEET_WIDTH_MM, 5, 3, null, true, threeTypes(), false));

        int pieces = result.stats().byType().stream().mapToInt(TypeStats::pieces).sum();
        double area = result.stats().byType().stream()
                .mapToDouble(TypeStats::shapeAreaMm2).sum();

        assertThat(pieces).isEqualTo(result.stats().totalPieces()).isEqualTo(45);
        assertThat(area).isCloseTo(result.stats().totalShapeAreaMm2(), within(1.0));
    }

    /**
     * Kich thuoc trong bang phai la kich thuoc TRUOC khi xoay.
     *
     * <p>Neu lay nham kich thuoc da xoay thi mot loai hinh se hien 14,9 x 25 cm o lan
     * chay nay va 25 x 14,9 cm o lan chay khac, tuy packer quyet dinh xoay hay khong -
     * tho nhin vao se tuong app doc sai file.
     */
    @Test
    @DisplayName("Kich thuoc trong bang la kich thuoc goc, khong doi theo huong xoay")
    void sizesAreUnrotated() {
        NestResult result = engine.nest(new EngineInput(
                SHEET_WIDTH_MM, 5, 3, null, true, threeTypes(), false));

        TypeStats card = result.stats().byType().get(0);
        assertThat(card.widthMm()).isCloseTo(250, within(0.6));
        assertThat(card.heightMm()).isCloseTo(149, within(0.6));

        // Va phai co it nhat mot ban bi xoay thi bai test nay moi co y nghia.
        boolean anyRotated = result.sheets().stream()
                .flatMap(sheet -> sheet.placements().stream())
                .anyMatch(Placement::rotated);
        assertThat(anyRotated).as("phai co ban bi xoay thi moi kiem duoc dieu tren").isTrue();
    }

    @Test
    @DisplayName("Che do xep long theo hinh that cung co bang so lieu nay")
    void trueShapeAlsoReportsByType() {
        NestResult result = engine.nest(new EngineInput(
                SHEET_WIDTH_MM, 5, 3, null, true, threeTypes(), true));

        assertThat(result.stats().byType()).hasSize(3);
        assertThat(result.stats().byType().stream().mapToInt(TypeStats::pieces).sum())
                .isEqualTo(result.stats().totalPieces());
        assertThat(result.stats().byType().stream().mapToDouble(TypeStats::shareOfShapes).sum())
                .isCloseTo(1.0, within(EPS));
    }

    @Test
    @DisplayName("Ghep ra nhieu tam thi bang gom ca cac tam lai, khong dem sot")
    void aggregatesAcrossSheets() {
        NestResult result = engine.nest(new EngineInput(
                SHEET_WIDTH_MM, 5, 3, 600.0, true, threeTypes(), false));

        assertThat(result.sheets().size()).as("phai ra nhieu tam thi bai test moi co nghia")
                .isGreaterThan(1);

        int piecesOnSheets = result.sheets().stream()
                .mapToInt(sheet -> sheet.placements().size()).sum();
        int piecesInTable = result.stats().byType().stream().mapToInt(TypeStats::pieces).sum();

        assertThat(piecesInTable).isEqualTo(piecesOnSheets).isEqualTo(45);
    }

    @Test
    @DisplayName("Chi mot loai hinh thi loai do chiem tron trong so")
    void singleTypeTakesFullShare() {
        NestResult result = engine.nest(new EngineInput(
                SHEET_WIDTH_MM, 5, 3, null, true,
                List.of(new EngineItem("f", "nhan", 200, 180, 8, true, 0)), false));

        assertThat(result.stats().byType()).hasSize(1);
        TypeStats only = result.stats().byType().get(0);
        assertThat(only.shareOfShapes()).isCloseTo(1.0, within(EPS));
        assertThat(only.fillRate()).isCloseTo(result.stats().fillRate(), within(EPS));
    }

    /** Tat dinh: chay lai nhieu lan phai ra dung mot bang, dung ca thu tu dong. */
    @Test
    @DisplayName("Chay lai nhieu lan cho ra dung mot bang so lieu")
    void isDeterministic() {
        EngineInput input = new EngineInput(
                SHEET_WIDTH_MM, 5, 3, null, true, threeTypes(), false);

        List<TypeStats> first = engine.nest(input).stats().byType();
        List<TypeStats> second = engine.nest(input).stats().byType();

        assertThat(second).isEqualTo(first);
    }

    /** Bang so lieu phai noi ve cung mot thu voi hinh ve: gom theo categoryIndex. */
    @Test
    @DisplayName("Moi ban in tren tam deu thuoc ve mot dong trong bang")
    void everyPlacementBelongsToARow() {
        NestResult result = engine.nest(new EngineInput(
                SHEET_WIDTH_MM, 5, 3, null, true, threeTypes(), false));

        List<Integer> rows = result.stats().byType().stream()
                .map(TypeStats::categoryIndex).toList();

        for (Sheet sheet : result.sheets()) {
            for (Placement p : sheet.placements()) {
                assertThat(rows).as("ban in %s khong co dong nao trong bang", p.label())
                        .contains(p.categoryIndex());
            }
        }
    }
}
