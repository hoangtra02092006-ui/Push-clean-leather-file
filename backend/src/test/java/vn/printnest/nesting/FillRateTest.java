package vn.printnest.nesting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.printnest.nesting.engine.EngineInput;
import vn.printnest.nesting.engine.EngineItem;
import vn.printnest.nesting.engine.NestingEngine;
import vn.printnest.nesting.model.Coverage;
import vn.printnest.nesting.model.NestResult;
import vn.printnest.nesting.model.Placement;
import vn.printnest.nesting.model.Sheet;
import vn.printnest.nesting.model.TypeStats;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Kiem thu ty le lap day.
 *
 * <p>Sinh ra tu mot loi that: man ket qua hien "ty le lap day 100,3%" va "giay bo di
 * -0,3%". Nguyen nhan la ty le lay bang TONG dien tich cac khung bao chia cho dien tich
 * tam. O che do nhet hinh nho vao cho trong, khung bao duoc phep LONG VAO NHAU - do
 * chinh la muc dich cua che do - nen phan giay bi hai khung bao cung trum bi dem hai lan.
 *
 * <p>Mot to giay khong the bi phu hon 100% chinh no. Day la loai con so ma nguoi dung
 * nhin mot cai la biet app tinh sai, nen mat long tin vao ca nhung con so dung.
 */
class FillRateTest {

    private static final double SHEET_WIDTH_MM = 570;

    private final NestingEngine engine = new NestingEngine();

    /**
     * Hinh lon 108 x 108 mm, net ve hinh chu L, goc tren-phai bo trong 47 x 47 mm, kem
     * hinh nho 40 x 40 mm chui vua vao goc do.
     *
     * <p>Truoc khi sua, cau hinh nay cho ty le lap day 101,6%.
     */
    private static List<EngineItem> hostAndGuest(int hosts, int guests) {
        return List.of(
                new EngineItem("host", "chu-L.pdf", 108, 108, hosts, true, 0,
                        List.of(new EngineItem.CavityMm(61, 61, 47, 47))),
                new EngineItem("guest", "hinh-nho.pdf", 40, 40, guests, true, 1));
    }

    @Test
    @DisplayName("Nhet hinh nho vao cho trong: ty le lap day khong duoc vuot 100%")
    void nestedShapesDoNotPushFillRateOverOne() {
        NestResult result = engine.nest(new EngineInput(
                SHEET_WIDTH_MM, 5, 3, null, true, hostAndGuest(10, 10), false));

        assertThat(result.stats().fillRate())
                .as("ty le lap day chung")
                .isLessThanOrEqualTo(1.0);

        for (Sheet sheet : result.sheets()) {
            assertThat(sheet.fillRate())
                    .as("ty le lap day cua tam %d", sheet.index() + 1)
                    .isLessThanOrEqualTo(1.0);
        }
    }

    @Test
    @DisplayName("Xep long theo hinh that: ty le lap day khong duoc vuot 100%")
    void trueShapeFillRateStaysUnderOne() {
        NestResult result = engine.nest(new EngineInput(
                SHEET_WIDTH_MM, 5, 3, null, true, hostAndGuest(12, 12), true));

        assertThat(result.stats().fillRate()).isLessThanOrEqualTo(1.0);
        for (Sheet sheet : result.sheets()) {
            assertThat(sheet.fillRate()).isLessThanOrEqualTo(1.0);
        }
    }

    /**
     * Cong "lap day" cua moi loai lai phai dung bang ty le lap day chung.
     *
     * <p>Neu khong, dong "Giay bo di" tren man hinh (= 100% tru ty le chung) se khong khop
     * voi cac dong ben tren no.
     */
    @Test
    @DisplayName("Cong lap day cua cac loai lai bang ty le lap day chung")
    void perTypeFillRatesAddUp() {
        NestResult result = engine.nest(new EngineInput(
                SHEET_WIDTH_MM, 5, 3, null, true, hostAndGuest(10, 10), false));

        double sum = result.stats().byType().stream()
                .mapToDouble(TypeStats::fillRate).sum();

        assertThat(sum).isCloseTo(result.stats().fillRate(), within(0.0002));
        assertThat(sum).as("va tat nhien khong duoc vuot 100%").isLessThanOrEqualTo(1.0);
    }

    /**
     * Che do xep luoi khong co khung bao nao chong nhau, nen hop bang DUNG tong.
     *
     * <p>Day la dieu phai giu: sua cach tinh ma lam xe dich con so cua che do xep luoi thi
     * moi so lieu nghiem thu cu deu khong con so sanh duoc nua.
     */
    @Test
    @DisplayName("Xep luoi: dien tich bi phu bang dung tong cac khung bao")
    void orthogonalCoverageEqualsPlainSum() {
        NestResult result = engine.nest(new EngineInput(
                SHEET_WIDTH_MM, 5, 3, null, true,
                List.of(new EngineItem("f1", "the", 250, 149, 15, true, 0),
                        new EngineItem("f2", "nhan", 50, 46, 10, true, 1)),
                false));

        for (Sheet sheet : result.sheets()) {
            double sum = sheet.placements().stream()
                    .mapToDouble(p -> p.wMm() * p.hMm()).sum();
            double covered = Coverage.of(sheet.placements()).totalAreaMm2();

            assertThat(covered)
                    .as("khong co hinh nao chong nhau thi hop = tong")
                    .isCloseTo(sum, within(0.01));
        }
    }

    @Test
    @DisplayName("Hai khung bao long nhau chi tinh phan giay do MOT lan")
    void overlappingBoxesCountOnce() {
        // Mot hinh 100 x 100 va mot hinh 40 x 40 nam gon ben trong no.
        List<Placement> placements = List.of(
                new Placement("a", "lon", 0, 0, 0, 100, 100, false),
                new Placement("b", "nho", 1, 30, 30, 40, 40, false));

        Coverage coverage = Coverage.of(placements);

        assertThat(coverage.totalAreaMm2())
                .as("hop cua hai hinh long nhau")
                .isCloseTo(100 * 100, within(0.01));
        assertThat(coverage.areaOf(0)).isCloseTo(100 * 100, within(0.01));
        assertThat(coverage.areaOf(1))
                .as("hinh nho chui gon vao trong thi khong ton them giay nao")
                .isCloseTo(0, within(0.01));
    }

    @Test
    @DisplayName("Hai khung bao chong nhau mot phan: phan chung chi dem mot lan")
    void partialOverlapCountsOnce() {
        List<Placement> placements = List.of(
                new Placement("a", "A", 0, 0, 0, 100, 100, false),
                new Placement("b", "B", 1, 80, 0, 100, 100, false));

        Coverage coverage = Coverage.of(placements);

        // Hop = 180 x 100; phan chung 20 x 100 chi dem mot lan.
        assertThat(coverage.totalAreaMm2()).isCloseTo(180 * 100, within(0.01));
        assertThat(coverage.areaOf(0)).isCloseTo(100 * 100, within(0.01));
        assertThat(coverage.areaOf(1)).isCloseTo(80 * 100, within(0.01));
    }

    @Test
    @DisplayName("Khong co hinh nao thi dien tich bi phu bang 0")
    void emptySheetCoversNothing() {
        assertThat(Coverage.of(List.of()).totalAreaMm2()).isEqualTo(0);
    }
}
