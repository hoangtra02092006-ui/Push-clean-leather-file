package vn.printnest.export;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;

/**
 * Ve mot tam ra thanh tung DAI NGANG thay vi ca tam mot luc.
 *
 * <p><b>Vi sao can.</b> Ve ca tam 57 x 100 cm o 300 DPI ra anh ARGB la 318 MB, va do moi
 * chi la mang dau tien trong bon mang phai cung ton tai de dung file TIF. Tren may chu
 * 2 GB, tong lai la vuot han - tien trinh bi he dieu hanh giet, khong phai JVM nem
 * {@code OutOfMemoryError} nen khong cho nao bat duoc.
 *
 * <p>Ve theo dai thi bo nho khong con phu thuoc CHIEU DAI tam nua: mot dai 1.024 hang o
 * kho 57 cm la 27 MB, bao nhieu met cuon cung van 27 MB.
 *
 * <p><b>Cach ve: day trang len bang mot phep tinh tien SO NGUYEN roi cat.</b> Nho tinh
 * tien nguyen diem anh ma dai nay ghep voi dai kia ra dung bang ve ca trang mot lan -
 * khong lech mot ly nao.
 *
 * <p>Cach hien nhien hon - thu hep CropBox cho tung dai - thi KHONG dung duoc: khung
 * tinh bang point, ma bien dai lai tinh bang diem anh, nen moi dai bi xe dich mot phan
 * diem anh. Do that tren mot tam 6.732 x 9.673: net ve o vien bi khu rang cua khac di,
 * va kenh muc trang lech han o 206.114 diem - tuc la 0,32% diem anh bi doi han tu "co
 * muc" thanh "khong muc" hoac nguoc lai.
 *
 * <p><b>Va phai xoa nen ve TRONG SUOT truoc khi ve.</b> PDFBox xoa nen trang bang MAU
 * NEN cua {@code Graphics2D}, ma mac dinh cua mot anh ARGB moi tao la DEN DAC. De nguyen
 * thi ca tam ra {@code ff000000}: mat kenh alpha la mat luon cach phan biet "khong co
 * hinh" voi "hinh mau den", ma ca mat na muc trang lan lop trong suot deu dua vao do.
 *
 * <p>Toa do dai tinh bang DIEM ANH cua ca tam, goc o tren-trai giong quy uoc anh.
 */
final class SheetBands {

    /**
     * So hang ve DU ra moi phia truoc khi cat lay phan giua.
     *
     * <p>Do that: lech chi xuat hien trong vong mot hai hang sat mep vung ve. Lay 8 cho
     * rong rai - moi dai chi ton them 8 hang, khong dang ke.
     */
    private static final int OVERLAP = 32;

    /** Nen trong suot hoan toan, de PDFBox xoa trang bang mau nay. */
    private static final Color CLEAR = new Color(0, 0, 0, 0);

    private final PDFRenderer renderer;
    private final int width;
    private final int height;
    private final float scale;

    private SheetBands(PDFRenderer renderer, int width, int height, int dpi) {
        this.renderer = renderer;
        this.width = width;
        this.height = height;
        this.scale = dpi / 72f;
    }

    /**
     * Do kich thuoc tam roi mo duong ve theo dai.
     *
     * <p>Kich thuoc KHONG tu tinh lay ma hoi chinh PDFBox, bang cach ve thu mot dai cao
     * mot hang. Tinh tay tung sai mot cot: cong thuc cho 1.616 diem con PDFBox ve ra
     * 1.615. Lech mot cot la toan bo mat na truot di mot diem moi hang.
     */
    static SheetBands of(PDDocument document, int dpi) throws IOException {
        PDFRenderer renderer = new PDFRenderer(document);
        PDPage page = document.getPage(0);
        PDRectangle crop = page.getCropBox();
        float scale = dpi / 72f;

        // Ve mot hang de hoi chieu rong. Chi ton mot luot doc luong noi dung, khong ton
        // bo nho: anh tra ve cao dung mot hang.
        PDRectangle saved = new PDRectangle(crop.getLowerLeftX(), crop.getLowerLeftY(),
                crop.getWidth(), crop.getHeight());
        int width;
        try {
            page.setCropBox(new PDRectangle(saved.getLowerLeftX(),
                    saved.getUpperRightY() - 72f / dpi, saved.getWidth(), 72f / dpi));
            BufferedImage probe = renderer.renderImage(0, scale, ImageType.ARGB);
            width = probe.getWidth();
            probe.flush();
        } finally {
            page.setCropBox(saved);
        }

        int height = (int) Math.max(Math.floor(saved.getHeight() * scale), 1);
        return new SheetBands(renderer, width, height, dpi);
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    /**
     * Ve mot dai.
     *
     * @param firstRow hang dau cua dai, tinh tu dinh tam
     * @param rows     so hang
     * @return anh ARGB dung {@code rows} hang; cho khong co net ve la TRONG SUOT
     */
    BufferedImage render(int firstRow, int rows) throws IOException {
        // Ve DU ra tren duoi roi chi lay phan giua. Net cheo hay net cong bi cat ngang o
        // mep vung ve thi phep khu rang cua tinh khac di - do that tren mot trang co net
        // cong: khoang 200 diem lech moi duong cat, du da tinh tien nguyen diem anh. Ve du
        // ra thi moi hang duoc giu deu nam sau trong vung ve, khong cham mep nao.
        int above = Math.min(OVERLAP, firstRow);
        int below = Math.min(OVERLAP, height - (firstRow + rows));
        int tallRows = above + rows + below;

        BufferedImage tall = new BufferedImage(width, tallRows, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = tall.createGraphics();
        try {
            // Phai dat truoc khi ve: PDFBox xoa trang bang chinh mau nen nay.
            graphics.setBackground(CLEAR);
            graphics.clearRect(0, 0, width, tallRows);
            // Day trang len de hang can ve roi dung vao hang 0 cua vung ve. So NGUYEN,
            // nen khong sinh sai so duoi mot diem anh.
            graphics.translate(0, -(firstRow - above));
            renderer.renderPageToGraphics(0, graphics, scale, scale);
        } finally {
            graphics.dispose();
        }

        if (tallRows == rows) {
            return tall;
        }

        // Chep phan giua ra anh rieng. Khong dung getSubimage: no tra ve mot KHUNG NHIN
        // dung chung mang voi anh me, ma cho doc mat na lai lay thang mang do ra - se doc
        // nham sang phan da cat bo.
        BufferedImage band = new BufferedImage(width, rows, BufferedImage.TYPE_INT_ARGB);
        System.arraycopy(
                ((DataBufferInt) tall.getRaster().getDataBuffer()).getData(), above * width,
                ((DataBufferInt) band.getRaster().getDataBuffer()).getData(), 0,
                rows * width);
        tall.flush();
        return band;
    }
}
