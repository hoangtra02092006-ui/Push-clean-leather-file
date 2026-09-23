package vn.printnest.export;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiem thu mat na muc trang (kenh W1) cho in DTF.
 *
 * <p>May in DTF phun mot lop trang lot xuong truoc roi moi phun mau. Sai mat na nay thi
 * hoac ao ra thieu lop lot (mau chim tren vai toi), hoac lo vien trang quanh hinh - ca
 * hai deu chi phat hien khi muc da len ao.
 */
class WhiteChannelTest {

    private static final int INK = 255;
    private static final int NONE = 0;

    /**
     * Mat na muc trang: do vung phu roi co vao trong.
     *
     * <p>Ghep hai buoc o day chu khong o {@code WhiteChannel}, vi ban CAT can vung phu
     * CHUA co vao nen ben do goi tung buoc rieng.
     */
    private static byte[] whiteMask(BufferedImage image, int threshold, int choke,
                                    int whiteTolerance) {
        return WhiteChannel.choke(WhiteChannel.coverage(image, threshold, whiteTolerance),
                image.getWidth(), image.getHeight(), choke);
    }

    /** Gia tri mat na tai mot diem, doi sang 0..255 cho de doc. */
    private static int at(byte[] mask, int width, int x, int y) {
        return mask[y * width + x] & 0xFF;
    }

    @Test
    @DisplayName("Vung trong suot khong duoc lot trang")
    void transparentAreasGetNoWhite() {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(5, 5, 10, 10);
        g.dispose();

        byte[] mask = whiteMask(image, 128, 0, 6);

        assertThat(at(mask, 20, 10, 10)).as("giua hinh").isEqualTo(INK);
        assertThat(at(mask, 20, 1, 1)).as("goc trong suot").isEqualTo(NONE);
    }

    /**
     * Cho quan trong nhat cua ca bai: chu trang nam giua logo.
     *
     * <p>Neu chi lay quy tac "diem trang la nen" thi o vuong trang nay se mat lop lot -
     * in len ao mau toi la bien mat. Loang tu mep thi no khong bi dung toi, vi bi mau do
     * bao quanh.
     */
    @Test
    @DisplayName("Chu trang nam giua logo van duoc lot trang")
    void whiteDetailInsideArtworkKeepsItsUnderbase() {
        BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 40, 40);
        g.setColor(Color.RED);
        g.fillRect(8, 8, 24, 24);
        g.setColor(Color.WHITE);
        g.fillRect(16, 16, 8, 8);
        g.dispose();

        byte[] mask = whiteMask(image, 128, 0, 10);

        assertThat(at(mask, 40, 20, 20))
                .as("o vuong trang giua logo phai duoc lot trang")
                .isEqualTo(INK);
        assertThat(at(mask, 40, 12, 12)).as("phan mau do").isEqualTo(INK);
        assertThat(at(mask, 40, 2, 2))
                .as("nen trang noi ra mep thi khong lot")
                .isEqualTo(NONE);
    }

    @Test
    @DisplayName("Anh JPG nen trang: nen bi loai, hinh giu lai")
    void opaqueWhiteBackgroundIsRemoved() {
        BufferedImage image = new BufferedImage(30, 30, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        // 252 chu khong phai 255: anh JPG bi nen mat du lieu nen nen khong phang tuyet doi.
        g.setColor(new Color(252, 253, 252));
        g.fillRect(0, 0, 30, 30);
        g.setColor(new Color(20, 80, 200));
        g.fillOval(8, 8, 14, 14);
        g.dispose();

        byte[] mask = whiteMask(image, 128, 0, 10);

        assertThat(at(mask, 30, 15, 15)).as("giua hinh tron").isEqualTo(INK);
        assertThat(at(mask, 30, 0, 0)).as("goc nen").isEqualTo(NONE);
    }

    /**
     * Co vao trong: lop trang phai nho hon lop mau.
     *
     * <p>Hai lop bang nhau thi chi can may keo phim lech nua milimet la lo vien trang
     * quanh hinh - nhin thay ro tren ao mau toi.
     */
    @Test
    @DisplayName("Co vao trong dung so diem da dat")
    void maskIsChokedInwards() {
        BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(10, 10, 20, 20);
        g.dispose();

        byte[] mask = whiteMask(image, 128, 2, 6);

        assertThat(at(mask, 40, 10, 20)).as("dung mep trai cua hinh").isEqualTo(NONE);
        assertThat(at(mask, 40, 11, 20)).as("vao trong 1 diem").isEqualTo(NONE);
        assertThat(at(mask, 40, 12, 20)).as("vao trong 2 diem: bat dau co muc").isEqualTo(INK);
        assertThat(at(mask, 40, 20, 20)).as("giua hinh").isEqualTo(INK);
    }

    /**
     * Hinh cham mep tam cung phai bi co vao.
     *
     * <p>Ban dau toi coi diem ngoai mep la "co muc", nen hinh cham mep khong bi co - ma
     * do dung la cho de lo vien trang nhat.
     */
    @Test
    @DisplayName("Hinh cham mep tam van bi co vao, khong dinh nguyen o mep")
    void shapeTouchingTheEdgeIsStillChoked() {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 20, 20);
        g.dispose();

        byte[] mask = whiteMask(image, 128, 2, 6);

        assertThat(at(mask, 20, 0, 10)).as("dung mep tam").isEqualTo(NONE);
        assertThat(at(mask, 20, 1, 10)).as("vao trong 1 diem").isEqualTo(NONE);
        assertThat(at(mask, 20, 2, 10)).as("vao trong 2 diem").isEqualTo(INK);
    }

    /**
     * Muc co MAC DINH la 1 diem, khop voi cai tho dang lam tay.
     *
     * <p>Trong Photoshop la Select &gt; Modify &gt; Contract roi go so 1. Con so nay tinh
     * bang diem anh o do phan giai cua tai lieu, nen no chi khop khi file xuat ra cung do
     * phan giai - hien tai ca hai deu 300 DPI.
     */
    @Test
    @DisplayName("Co 1 diem: dung bang thao tac Contract 1 cua tho")
    void defaultChokeOfOnePixel() {
        BufferedImage image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(10, 10, 20, 20);
        g.dispose();

        byte[] mask = whiteMask(image, 128, 1, 6);

        assertThat(at(mask, 40, 10, 20)).as("dung mep hinh: da bi co").isEqualTo(NONE);
        assertThat(at(mask, 40, 11, 20)).as("vao trong 1 diem: co muc").isEqualTo(INK);
        assertThat(at(mask, 40, 29, 20)).as("mep phai cua hinh").isEqualTo(NONE);
        assertThat(at(mask, 40, 28, 20)).as("vao trong 1 diem tu ben phai").isEqualTo(INK);
    }

    @Test
    @DisplayName("Nguong alpha dat cao thi vung mo bi loai")
    void alphaThresholdIsRespected() {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                image.setRGB(x, y, (100 << 24) | 0xFF0000);
            }
        }

        assertThat(at(whiteMask(image, 50, 0, 6), 10, 5, 5))
                .as("alpha 100 > nguong 50").isEqualTo(INK);
        assertThat(at(whiteMask(image, 150, 0, 6), 10, 5, 5))
                .as("alpha 100 < nguong 150").isEqualTo(NONE);
    }

    /**
     * Kenh ghi vao file TIFF nam NGUOC voi mat na.
     *
     * <p>Doc thang du lieu diem anh cua file mau {@code samples/IN6 2209.tif} - giai nen
     * LZW roi go predictor - tren nam dai anh, 234.115 diem: cho co hinh thi W1 trung binh
     * la 3, cho trong thi 255. Tuc la 0 moi la co muc trang.
     *
     * <p>Dat nguoc chieu nay thi lop trang ra AM BAN: may phun trang day vao khoang trong
     * quanh hinh va bo trang chinh cho co hinh. File van mo ra binh thuong, van du nam
     * kenh, van dung ten - chi den luc muc len ao moi lo ra. Day la ly do bai test nay ton
     * tai.
     */
    @Test
    @DisplayName("Ghi vao file thi 0 moi la co muc trang, dung nhu file mau")
    void spotBandIsWrittenInverted() {
        byte[] mask = {(byte) 255, 0, (byte) 255};
        byte[] samples = new byte[3 * 5];

        WhiteChannel.writeSpotBand(mask, samples, 4, 5);

        assertThat(samples[4] & 0xFF).as("co lot trang -> ghi 0").isEqualTo(0);
        assertThat(samples[9] & 0xFF).as("khong lot trang -> ghi 255").isEqualTo(255);
        assertThat(samples[14] & 0xFF).as("co lot trang -> ghi 0").isEqualTo(0);
    }

    @Test
    @DisplayName("Ghi kenh thu nam khong dung den bon kenh mau")
    void spotBandLeavesColourChannelsAlone() {
        byte[] samples = new byte[2 * 5];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (byte) 77;
        }

        WhiteChannel.writeSpotBand(new byte[]{(byte) 255, 0}, samples, 4, 5);

        for (int pixel = 0; pixel < 2; pixel++) {
            for (int band = 0; band < 4; band++) {
                assertThat(samples[pixel * 5 + band])
                        .as("diem %d kenh mau %d phai giu nguyen", pixel, band)
                        .isEqualTo((byte) 77);
            }
        }
    }

}
