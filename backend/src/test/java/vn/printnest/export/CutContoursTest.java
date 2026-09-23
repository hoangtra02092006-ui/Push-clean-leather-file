package vn.printnest.export;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiem thu phan dua tu mat na diem anh sang duong cat.
 *
 * <p>Sai o day thi may cat di sai duong: hoac cat vao net ve, hoac cat hut ra ngoai phim.
 * Ca hai chi phat hien khi dao da ha xuong vat lieu.
 */
class CutContoursTest {

    private static final byte INK = (byte) 255;

    /** Dung mat na tu tranh ASCII cho de doc: dau '#' la co hinh. */
    private static byte[] mask(String... rows) {
        int width = rows[0].length();
        byte[] mask = new byte[width * rows.length];
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < width; x++) {
                mask[y * width + x] = rows[y].charAt(x) == '#' ? INK : 0;
            }
        }
        return mask;
    }

    private static int inkCount(byte[] mask) {
        int count = 0;
        for (byte b : mask) {
            if (b != 0) {
                count++;
            }
        }
        return count;
    }

    @Test
    @DisplayName("No ra mot diem moi phia")
    void dilateGrowsByOnePixel() {
        byte[] source = mask(
                ".....",
                ".....",
                "..#..",
                ".....",
                ".....");

        byte[] grown = CutContours.dilate(source, 5, 5, 1);

        assertThat(inkCount(grown)).as("mot diem no ra thanh o 3x3").isEqualTo(9);
        assertThat(grown[2 * 5 + 2]).as("tam van co").isEqualTo(INK);
        assertThat(grown[1 * 5 + 1]).as("goc tren trai cua o 3x3").isEqualTo(INK);
        assertThat(grown[0]).as("ngoai o 3x3").isEqualTo((byte) 0);
    }

    /**
     * Hinh cham mep tam KHONG duoc no lan ra ngoai trang.
     *
     * <p>Nguoc voi phep co cua kenh muc trang: o do diem ngoai tam coi nhu khoang trong de
     * hinh cham mep bi co vao. O day coi nhu khong co muc, de duong cat khong chay ra
     * ngoai kho giay.
     */
    @Test
    @DisplayName("Hinh cham mep khong no ra ngoai trang")
    void dilateStopsAtTheEdge() {
        byte[] source = mask(
                "#..",
                "...",
                "...");

        byte[] grown = CutContours.dilate(source, 3, 3, 1);

        assertThat(inkCount(grown)).as("chi no duoc ve phia trong tam").isEqualTo(4);
        assertThat(grown[0]).isEqualTo(INK);
        assertThat(grown[1]).isEqualTo(INK);
        assertThat(grown[3]).isEqualTo(INK);
        assertThat(grown[4]).isEqualTo(INK);
    }

    @Test
    @DisplayName("Mot khoi vuong cho ra mot duong khep kin")
    void tracesOneClosedPathPerBlock() {
        byte[] source = mask(
                ".....",
                ".###.",
                ".###.",
                ".###.",
                ".....");

        List<CutContours.Path> paths = CutContours.trace(source, 5, 5);

        assertThat(paths).as("dung mot duong").hasSize(1);
        assertThat(paths.get(0).points())
                .as("o vuong chi can bon goc, cac dinh thang hang da bi gop").hasSize(4);
        assertThat(paths.get(0).signedArea())
                .as("bao dung dien tich o 3x3").isEqualTo(9.0);
    }

    @Test
    @DisplayName("Hai khoi roi nhau cho ra hai duong")
    void tracesEachBlockSeparately() {
        byte[] source = mask(
                ".......",
                ".#...#.",
                ".......");

        assertThat(CutContours.trace(source, 7, 3)).hasSize(2);
    }

    /**
     * Hai hinh qua sat nhau thi hai duong cat dinh lam mot.
     *
     * <p>Day khong phai loi ma la hanh vi mong muon: hai duong cat cat nhau thi may cat
     * di lung tung o cho giao nhau. Dinh lien thanh mot duong bao ca hai la ket qua dung,
     * nhung phai BAO cho tho biet vi hai mieng decal se dinh lien nhau.
     */
    @Test
    @DisplayName("Hai hinh sat nhau: no ra thi dinh thanh mot duong")
    void nearbyShapesMergeIntoOnePath() {
        byte[] source = mask(
                ".......",
                ".#...#.",
                ".......");

        assertThat(CutContours.trace(source, 7, 3))
                .as("chua no thi van la hai").hasSize(2);
        assertThat(CutContours.trace(CutContours.dilate(source, 7, 3, 2), 7, 3))
                .as("no ra 2 diem thi dinh lam mot").hasSize(1);
    }

    /**
     * Vien ngoai va vien lo thung phai phan biet duoc bang dau dien tich.
     *
     * <p>Mieng decal duoc cat vong quanh chu khong khoet ruot, nen chi giu vien ngoai.
     */
    @Test
    @DisplayName("Vien ngoai va vien lo thung nguoc dau dien tich")
    void holeWindsTheOtherWay() {
        byte[] source = mask(
                ".....",
                ".###.",
                ".#.#.",
                ".###.",
                ".....");

        List<CutContours.Path> paths = CutContours.trace(source, 5, 5);
        assertThat(paths).as("mot vien ngoai va mot vien lo").hasSize(2);

        double outer = paths.stream().mapToDouble(CutContours.Path::signedArea)
                .max().orElseThrow();
        double hole = paths.stream().mapToDouble(CutContours.Path::signedArea)
                .min().orElseThrow();

        assertThat(outer).as("vien ngoai").isPositive();
        assertThat(hole).as("vien lo thung").isNegative();
        assertThat(outer).as("vien ngoai bao dien tich 3x3").isEqualTo(9.0);
        assertThat(Math.abs(hole)).as("lo thung 1x1").isEqualTo(1.0);
    }

    @Test
    @DisplayName("Bo bot dinh nam thang hang")
    void simplifyDropsCollinearPoints() {
        List<double[]> line = List.of(
                new double[]{0, 0}, new double[]{1, 0}, new double[]{2, 0},
                new double[]{3, 0}, new double[]{4, 0});

        assertThat(CutContours.simplify(line, 0.01))
                .as("mot doan thang chi can hai dau").hasSize(2);
    }

    @Test
    @DisplayName("Giu lai dinh lech xa hon nguong")
    void simplifyKeepsRealCorners() {
        List<double[]> corner = List.of(
                new double[]{0, 0}, new double[]{2, 0}, new double[]{4, 0},
                new double[]{4, 3}, new double[]{4, 6});

        assertThat(CutContours.simplify(corner, 0.5))
                .as("hai dau cong mot goc").hasSize(3);
    }

    /**
     * Duong rang cua that dai: khong duoc tran ngan xep.
     *
     * <p>Ban dau toi viet {@code simplify} kieu de quy. Voi duong nay thi moi dinh deu dang
     * giu nen phep chia doi lech han ve mot ben, do sau de quy bang so dinh - tran ngan xep.
     * Ma no chi tran voi file phuc tap cua khach chu khong bao gio tran luc thu bang hinh
     * don gian.
     *
     * <p>So dinh de 20.000 chu khong hon: day la truong hop XAU NHAT cua Douglas-Peucker,
     * chi phi tang theo binh phuong. Duong vien that khong bao gio nhu vay vi da qua buoc
     * gop dinh thang hang truoc do.
     */
    @Test
    @DisplayName("Duong rang cua rat dai khong lam tran ngan xep")
    void simplifyHandlesVeryLongPaths() {
        List<double[]> zigzag = new java.util.ArrayList<>();
        for (int i = 0; i < 20_000; i++) {
            zigzag.add(new double[]{i, i % 2});
        }

        assertThat(CutContours.simplify(zigzag, 0.1))
                .as("moi dinh deu that su lech nen phai giu gan het")
                .hasSizeGreaterThan(1000);
    }

    /**
     * Gop dinh thang hang KHONG duoc lam sai lech hinh dang.
     *
     * <p>Khac buoc {@code simplify}: buoc do chap nhan sai so trong nguong, buoc nay phai
     * chinh xac tuyet doi vi no chay tren moi duong, khong co nguong nao de khong che.
     */
    @Test
    @DisplayName("Gop dinh thang hang giu nguyen dien tich")
    void collapsingCollinearPointsKeepsTheShape() {
        byte[] source = mask(
                "........",
                ".######.",
                ".######.",
                ".##..##.",
                ".##..##.",
                "........");

        List<CutContours.Path> paths = CutContours.trace(source, 8, 6);
        double outer = paths.stream().mapToDouble(CutContours.Path::signedArea)
                .max().orElseThrow();

        // 20 chu khong phai 24: duong cat om theo phan KHUYET cua chu U chu khong cat
        // ngang qua no, nen phan khuyet nam ngoai duong.
        assertThat(outer).as("dien tich duong bao om sat chu U").isEqualTo(20.0);
        assertThat(paths.get(0).points())
                .as("hinh chu U co 8 goc").hasSize(8);
    }
}
