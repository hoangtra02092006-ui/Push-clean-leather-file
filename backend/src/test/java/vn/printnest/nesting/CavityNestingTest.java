package vn.printnest.nesting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.printnest.nesting.engine.EngineInput;
import vn.printnest.nesting.engine.EngineItem;
import vn.printnest.nesting.engine.NestingEngine;
import vn.printnest.nesting.model.NestResult;
import vn.printnest.nesting.model.Placement;
import vn.printnest.nesting.model.Sheet;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiem thu viec nhoi hinh nho vao HOC LOM cua hinh lon.
 *
 * <p>Tinh huong dung nhu nguoi dung mo ta: mot hinh co khung bao vuong, nhung sau khi bo
 * khoang trang thi goc tren ben phai van con trong. Neu co hinh nho vua chui vao goc do
 * thi phai cho phep, mien la khong cham nhau.
 *
 * <p>Truoc khi co hoc lom, ca khung bao bi coi la dac: hinh nho buoc phai nam ngoai, ton
 * them giay. Cac bai test duoi day do bang cach chay HAI LAN tren cung du lieu - mot lan
 * khai bao hoc lom, mot lan khong - roi so chieu dai.
 */
class CavityNestingTest {

    private static final double SHEET_WIDTH_MM = 570;
    private static final double MARGIN_MM = 5;
    private static final double GAP_MM = 3;

    private static final double EPS = 0.011;

    private final NestingEngine engine = new NestingEngine();

    /**
     * Hinh chu: khung bao 108 x 108 mm, net ve choan hinh chu L, goc tren-phai bo trong
     * mot vuong 50 x 50 mm.
     *
     * <p>Chon 108 mm co chu y: kho kha dung 563 mm chua vua 5 hinh (5 x 111 = 555), chi
     * con thua 8 mm - khong du cho mot hinh nho nao lot vao. Nho vay bai test do DUNG tac
     * dung cua hoc lom, chu khong phai do hinh nho von da co cho trong o bien.
     *
     * <p>O trong an toan (da lui vao mot gap moi phia giap net ve) la (61,61) kich thuoc
     * 47 x 47 mm.
     */
    private static EngineItem hostWithCorner(int quantity, boolean withCavity) {
        List<EngineItem.CavityMm> cavities = withCavity
                ? List.of(new EngineItem.CavityMm(61, 61, 47, 47))
                : List.of();
        return new EngineItem("host", "chu-L.pdf", 108, 108, quantity, true, 0, cavities);
    }

    /** Hinh nho 40 x 40 mm - du nho de chui vua goc trong o tren (can 43 x 43 da no gap). */
    private static EngineItem smallGuest(int quantity) {
        return new EngineItem("guest", "hinh-nho.pdf", 40, 40, quantity, true, 1);
    }

    private NestResult run(boolean withCavity, int hosts, int guests) {
        EngineInput input = new EngineInput(SHEET_WIDTH_MM, MARGIN_MM, GAP_MM, null, true,
                List.of(hostWithCorner(hosts, withCavity), smallGuest(guests)));
        return engine.nest(input);
    }

    @Test
    @DisplayName("Co hoc lom thi ton it giay hon han so voi coi khung bao la dac")
    void cavitiesSaveLength() {
        NestResult without = run(false, 10, 10);
        NestResult with = run(true, 10, 10);

        System.out.printf("[HOC LOM] khong dung = %.1f cm | co dung = %.1f cm | tiet kiem = %.1f%%%n",
                without.stats().totalLengthMm() / 10d,
                with.stats().totalLengthMm() / 10d,
                (1 - with.stats().totalLengthMm() / without.stats().totalLengthMm()) * 100);

        assertThat(with.stats().totalLengthMm())
                .as("dung hoc lom phai ngan hon")
                .isLessThan(without.stats().totalLengthMm());

        assertInvariants(with, 20);
    }

    @Test
    @DisplayName("Hinh nho that su nam LOT trong khung bao cua hinh chu")
    void guestActuallySitsInsideHostBox() {
        NestResult result = run(true, 4, 4);
        assertInvariants(result, 8);

        Sheet sheet = result.sheets().get(0);
        List<Placement> hosts = sheet.placements().stream()
                .filter(p -> "host".equals(p.fileId())).toList();
        List<Placement> guests = sheet.placements().stream()
                .filter(p -> "guest".equals(p.fileId())).toList();

        long tucked = guests.stream()
                .filter(g -> hosts.stream().anyMatch(h -> contains(h, g)))
                .count();

        assertThat(tucked)
                .as("phai co it nhat mot hinh nho nam gon trong khung bao hinh chu")
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("Khong khai hoc lom thi khong hinh nho nao duoc chui vao khung hinh chu")
    void withoutCavitiesNothingOverlapsBoxes() {
        NestResult result = run(false, 4, 4);
        assertInvariants(result, 8);

        Sheet sheet = result.sheets().get(0);
        for (Placement a : sheet.placements()) {
            for (Placement b : sheet.placements()) {
                if (a == b) {
                    continue;
                }
                assertThat(contains(a, b))
                        .as("khong duoc co hinh nao nam trong khung hinh khac")
                        .isFalse();
            }
        }
    }

    @Test
    @DisplayName("Hinh nam trong hoc lom van cach net ve hinh chu du gap")
    void tuckedGuestKeepsGapFromHostInk() {
        NestResult result = run(true, 4, 4);
        Sheet sheet = result.sheets().get(0);

        List<Placement> hosts = sheet.placements().stream()
                .filter(p -> "host".equals(p.fileId())).toList();

        for (Placement guest : sheet.placements()) {
            if (!"guest".equals(guest.fileId())) {
                continue;
            }
            for (Placement host : hosts) {
                if (!contains(host, guest)) {
                    continue;
                }
                // O trong an toan bat dau tu (53,53) trong he toa do hinh chu. Hinh nho
                // nam gon trong do thi mac nhien cach net ve chu it nhat mot gap.
                double localX = guest.xMm() - host.xMm();
                double localY = guest.yMm() - host.yMm();
                assertThat(localX).as("canh trai hinh nho trong he toa do hinh chu")
                        .isGreaterThanOrEqualTo(61 - EPS);
                assertThat(localY).as("canh duoi hinh nho trong he toa do hinh chu")
                        .isGreaterThanOrEqualTo(61 - EPS);
                assertThat(localX + guest.wMm()).isLessThanOrEqualTo(108 + EPS);
                assertThat(localY + guest.hMm()).isLessThanOrEqualTo(108 + EPS);
            }
        }
    }

    // ------------------------------------------------------------------
    // Bat bien
    // ------------------------------------------------------------------

    /** Hinh {@code inner} co nam tron trong khung bao cua {@code outer} khong. */
    private static boolean contains(Placement outer, Placement inner) {
        return inner.xMm() >= outer.xMm() - EPS
                && inner.yMm() >= outer.yMm() - EPS
                && inner.xMm() + inner.wMm() <= outer.xMm() + outer.wMm() + EPS
                && inner.yMm() + inner.hMm() <= outer.yMm() + outer.hMm() + EPS;
    }

    /**
     * Bat bien con lai sau khi co hoc lom.
     *
     * <p>Luu y: KHONG con kiem "hai khung bao bat ky khong chong lan" nua, vi cho phep
     * chui vao hoc lom tuc la khung bao duoc phep long nhau. Thay vao do kiem: hai hinh
     * chi duoc long nhau khi mot hinh nam TRON trong khung bao hinh kia (tuc la no dang
     * ngoi trong hoc lom), con lan nhau mot phan thi van cam tuyet doi.
     */
    private void assertInvariants(NestResult result, int expectedPieces) {
        assertThat(result.stats().totalPieces()).isEqualTo(expectedPieces);

        for (Sheet sheet : result.sheets()) {
            List<Placement> list = sheet.placements();
            for (int i = 0; i < list.size(); i++) {
                Placement a = list.get(i);
                assertThat(a.xMm()).isGreaterThanOrEqualTo(MARGIN_MM - EPS);
                assertThat(a.yMm()).isGreaterThanOrEqualTo(MARGIN_MM - EPS);
                assertThat(a.xMm() + a.wMm()).isLessThanOrEqualTo(sheet.widthMm() - MARGIN_MM + EPS);

                for (int j = i + 1; j < list.size(); j++) {
                    Placement b = list.get(j);
                    boolean separated = a.xMm() + a.wMm() <= b.xMm() + EPS
                            || b.xMm() + b.wMm() <= a.xMm() + EPS
                            || a.yMm() + a.hMm() <= b.yMm() + EPS
                            || b.yMm() + b.hMm() <= a.yMm() + EPS;
                    assertThat(separated || contains(a, b) || contains(b, a))
                            .as("hai hinh chi duoc roi nhau hoac long TRON vao nhau")
                            .isTrue();
                }
            }
        }
    }
}
