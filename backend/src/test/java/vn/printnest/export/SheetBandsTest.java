package vn.printnest.export;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ve theo dai phai cho ra dung bang ve ca trang mot lan.
 *
 * <p>Day la bat bien song con cua cach dung file TIF theo dai: mat na muc trang duoc dung
 * tu chinh anh nay, nen anh lech mot chut la lop trang lech theo. Do that luc cach ve con
 * sai: tren mot tam 6.732 x 9.673, kenh muc trang lech han o 206.114 diem - tuc 0,32% tam
 * bi doi tu "co muc trang" thanh "khong" hoac nguoc lai.
 */
class SheetBandsTest {

    private static final int DPI = 150;

    /** Nguong alpha coi la co hinh, giong {@code app.tiff.white.alpha-threshold}. */
    private static final int ALPHA_THRESHOLD = 128;

    /**
     * Hinh vuong goc - dung kieu ban in xuong ghep len tam.
     *
     * <p>Voi loai nay phep ve theo dai phai cho ket qua Y HET ve ca trang, khong mot diem
     * nao lech. Da doi chieu them o muc file: mot tam that 6.732 x 9.673 xuat bang hai
     * cach cho ra hai file giong nhau TUNG BYTE, ca 325.593.180 byte diem anh.
     */
    @Test
    @DisplayName("Hinh vuong goc: ghep cac dai lai ra dung bang ve ca trang")
    void axisAlignedArtworkStitchesBackExactly() throws IOException {
        try (PDDocument document = Loader.loadPDF(rectanglesPdf())) {
            BufferedImage whole = new PDFRenderer(document)
                    .renderImageWithDPI(0, DPI, ImageType.ARGB);

            for (int bandRows : new int[]{16, 64, 128}) {
                assertThat(differences(document, whole, bandRows).all())
                        .as("chia %d hang: so diem lech so voi ve ca trang", bandRows)
                        .isZero();
            }

            whole.flush();
        }
    }

    /**
     * Net cong thi CO lech mot chut, va day la cho phai noi ro.
     *
     * <p>Java2D khu rang cua theo vung dang ve, nen cat trang thanh dai lam net cheo va net
     * cong doi mot hai muc do dam o RIA. Ve du ra moi phia thi bot di nhung khong het han -
     * do that tren trang nay: ve du 8 hang con lech 1.055 diem, 32 hang con 824, 128 hang
     * con 147.
     *
     * <p>Cai duy nhat co hau qua that la diem nao DOI BEN nguong alpha, vi mat na muc trang
     * cat theo nguong do. Bai nay chot lai con so ay: phai cuc nho. Do duoc 14 diem tren
     * 260.000 - 0,005% - va lop trang von da duoc co vao mot diem anh, nen mot diem ria
     * dich di khong the thanh loi in.
     */
    @Test
    @DisplayName("Net cong: chi vai diem ria doi ben nguong, khong du thanh loi in")
    void antialiasedCurvesDriftOnlyAtTheVeryEdge() throws IOException {
        try (PDDocument document = Loader.loadPDF(curvePdf())) {
            BufferedImage whole = new PDFRenderer(document)
                    .renderImageWithDPI(0, DPI, ImageType.ARGB);
            int pixels = whole.getWidth() * whole.getHeight();

            Diff diff = differences(document, whole, 64);

            assertThat(100.0 * diff.crossingThreshold() / pixels)
                    .as("%d/%d diem doi ben nguong alpha", diff.crossingThreshold(), pixels)
                    .isLessThan(0.05);
            assertThat(diff.maxDelta())
                    .as("muc do dam lech nhieu nhat, tren thang 255")
                    .isLessThan(64);

            whole.flush();
        }
    }

    /**
     * Cho khong co net ve phai TRONG SUOT, khong phai den dac.
     *
     * <p>PDFBox xoa nen trang bang MAU NEN cua {@code Graphics2D}, ma mac dinh cua mot anh
     * ARGB moi tao la den dac. Quen dat mau nen thi ca tam ra {@code ff000000} - mat kenh
     * alpha la mat luon cach phan biet "khong co hinh" voi "hinh mau den", ma ca mat na muc
     * trang lan lop trong suot deu dua vao do.
     */
    @Test
    @DisplayName("Cho trong phai trong suot, khong duoc thanh den dac")
    void emptyAreasStayTransparent() throws IOException {
        try (PDDocument document = Loader.loadPDF(curvePdf())) {
            SheetBands bands = SheetBands.of(document, DPI);
            BufferedImage band = bands.render(0, Math.min(32, bands.height()));

            assertThat(band.getRGB(0, 0))
                    .as("goc tam khong co net ve nao nen phai trong suot hoan toan")
                    .isZero();
        }
    }

    // ------------------------------------------------------------------

    private record Diff(int all, int crossingThreshold, int maxDelta) {
    }

    /** So tung dai voi phan tuong ung cua anh ve ca trang. */
    private static Diff differences(PDDocument document, BufferedImage whole, int bandRows)
            throws IOException {
        SheetBands bands = SheetBands.of(document, DPI);
        assertThat(bands.width()).as("chieu rong").isEqualTo(whole.getWidth());
        assertThat(bands.height()).as("chieu cao").isEqualTo(whole.getHeight());

        int all = 0;
        int crossing = 0;
        int maxDelta = 0;

        for (int row = 0; row < bands.height(); row += bandRows) {
            int rows = Math.min(bandRows, bands.height() - row);
            BufferedImage band = bands.render(row, rows);

            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < bands.width(); x++) {
                    int mine = band.getRGB(x, y);
                    int theirs = whole.getRGB(x, row + y);
                    if (mine == theirs) {
                        continue;
                    }
                    all++;
                    for (int shift = 0; shift < 32; shift += 8) {
                        maxDelta = Math.max(maxDelta,
                                Math.abs(((mine >> shift) & 0xFF) - ((theirs >> shift) & 0xFF)));
                    }
                    if ((mine >>> 24 > ALPHA_THRESHOLD) != (theirs >>> 24 > ALPHA_THRESHOLD)) {
                        crossing++;
                    }
                }
            }
            band.flush();
        }
        return new Diff(all, crossing, maxDelta);
    }

    /** Hinh vuong goc, giong cac ban in xuong ghep len tam. */
    private static byte[] rectanglesPdf() throws IOException {
        return pdf(content -> {
            content.setNonStrokingColor(0.1f, 0.3f, 0.8f);
            content.addRect(20, 20, 70, 110);
            content.fill();
            content.setNonStrokingColor(0.9f, 0.6f, 0.1f);
            content.addRect(110, 160, 60, 90);
            content.fill();
        });
    }

    /** Net cong va net cheo, de phep khu rang cua thuc su lam viec. */
    private static byte[] curvePdf() throws IOException {
        return pdf(content -> {
            content.setNonStrokingColor(0.9f, 0.1f, 0.2f);
            content.moveTo(20, 20);
            content.lineTo(180, 140);
            content.lineTo(60, 280);
            content.closePath();
            content.fill();

            content.setStrokingColor(0f, 0f, 0f);
            content.setLineWidth(1.3f);
            content.moveTo(10, 150);
            content.curveTo(70, 290, 130, 10, 190, 150);
            content.stroke();
        });
    }

    private interface Drawing {
        void draw(PDPageContentStream content) throws IOException;
    }

    private static byte[] pdf(Drawing drawing) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(200, 300));
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                drawing.draw(content);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            document.save(bytes);
            return bytes.toByteArray();
        }
    }
}
