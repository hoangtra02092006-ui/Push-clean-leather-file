package vn.printnest.file;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.printnest.common.Units;

import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Kiem thu viec tim hop bao vector.
 *
 * <p>Moi file trong day duoc dung tai cho voi toa do BIET TRUOC, nen co the doi chieu
 * den tung milimet thay vi chi kiem "co ra so nao do".
 */
class PdfContentBoxFinderTest {

    /** Sai so cho phep, tinh bang milimet. */
    private static final double EPS = 0.6;

    @Test
    @DisplayName("Mot hinh nho giua trang A4: hop bao om dung hinh, bo het khoang trang")
    void singleSmallShape() throws Exception {
        byte[] pdf = buildPdf(210, 297, content -> {
            content.addRect(pt(90), pt(140), pt(30), pt(30));
            content.fill();
        });

        Rectangle2D box = findBox(pdf);

        assertThat(mm(box.getX())).isCloseTo(90, within(EPS));
        assertThat(mm(box.getY())).isCloseTo(140, within(EPS));
        assertThat(mm(box.getWidth())).isCloseTo(30, within(EPS));
        assertThat(mm(box.getHeight())).isCloseTo(30, within(EPS));
    }

    @Test
    @DisplayName("Nhieu hinh nho roi rac: gop thanh MOT hop om tron tat ca")
    void manySmallShapesStayTogether() throws Exception {
        // Ba hinh nam rai trong vung X 80..200 mm, Y 150..260 mm.
        byte[] pdf = buildPdf(297, 420, content -> {
            content.addRect(pt(80), pt(150), pt(50), pt(40));
            content.fill();
            content.addRect(pt(140), pt(150), pt(60), pt(40));
            content.fill();
            content.addRect(pt(80), pt(200), pt(120), pt(60));
            content.fill();
        });

        Rectangle2D box = findBox(pdf);

        assertThat(mm(box.getX())).as("canh trai").isCloseTo(80, within(EPS));
        assertThat(mm(box.getY())).as("canh duoi").isCloseTo(150, within(EPS));
        assertThat(mm(box.getWidth())).as("chieu rong om ca ba hinh").isCloseTo(120, within(EPS));
        assertThat(mm(box.getHeight())).as("chieu cao om ca ba hinh").isCloseTo(110, within(EPS));
    }

    @Test
    @DisplayName("Hinh to mau TRANG van duoc giu - day la cho quet pixel se cat nham")
    void whiteShapeIsStillContent() throws Exception {
        // Nen trang phu kin 100x80 mm, ben trong co mot cham den nho.
        // Quet pixel se chi thay cham den; doc vector phai thay ca nen trang.
        byte[] pdf = buildPdf(210, 297, content -> {
            content.setNonStrokingColor(1f, 1f, 1f);
            content.addRect(pt(50), pt(100), pt(100), pt(80));
            content.fill();
            content.setNonStrokingColor(0f, 0f, 0f);
            content.addRect(pt(95), pt(135), pt(10), pt(10));
            content.fill();
        });

        Rectangle2D box = findBox(pdf);

        assertThat(mm(box.getWidth()))
                .as("phai om ca nen trang chu khong chi cham den")
                .isCloseTo(100, within(EPS));
        assertThat(mm(box.getHeight())).isCloseTo(80, within(EPS));
    }

    @Test
    @DisplayName("Net ve duoc noi them nua do day, khong bi cat cut")
    void strokeWidthIsIncluded() throws Exception {
        byte[] pdf = buildPdf(210, 297, content -> {
            content.setLineWidth(pt(4));          // net day 4 mm
            content.addRect(pt(50), pt(50), pt(100), pt(100));
            content.stroke();
        });

        Rectangle2D box = findBox(pdf);

        // Duong o giua net, nen hop bao nong ra 2 mm moi phia.
        assertThat(mm(box.getX())).isCloseTo(48, within(EPS));
        assertThat(mm(box.getWidth())).isCloseTo(104, within(EPS));
    }

    @Test
    @DisplayName("Chu cung duoc tinh vao hop bao")
    void textIsIncluded() throws Exception {
        byte[] pdf = buildPdf(210, 297, content -> {
            content.beginText();
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), pt(10));
            content.newLineAtOffset(pt(60), pt(200));
            content.showText("PrintNest");
            content.endText();
        });

        Rectangle2D box = findBox(pdf);

        assertThat(box).as("phai tim thay chu").isNotNull();
        assertThat(mm(box.getX())).as("chu bat dau tu 60 mm").isCloseTo(60, within(3d));
        assertThat(mm(box.getWidth())).as("chuoi 9 ky tu co 10 mm").isGreaterThan(20);
    }

    @Test
    @DisplayName("Trang trang tron tra ve null de ben goi biet ma dung ca kho")
    void blankPageReturnsNull() throws Exception {
        byte[] pdf = buildPdf(210, 297, content -> { });

        assertThat(findBox(pdf)).isNull();
    }

    // ------------------------------------------------------------------
    // Tien ich
    // ------------------------------------------------------------------

    private interface Drawing {
        void draw(PDPageContentStream content) throws IOException;
    }

    private static float pt(double mm) {
        return Units.mmToPt(mm);
    }

    private static double mm(double points) {
        return Units.ptToMm(points);
    }

    private static Rectangle2D findBox(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PdfContentBoxFinder(document.getPage(0)).find();
        }
    }

    private static byte[] buildPdf(double widthMm, double heightMm, Drawing drawing) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(pt(widthMm), pt(heightMm)));
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
