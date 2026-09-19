package vn.printnest.file;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.springframework.stereotype.Component;
import vn.printnest.common.ApiException;
import vn.printnest.common.ErrorCode;
import vn.printnest.common.Units;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Doc kich thuoc VAT LY (milimet) cua file nguon.
 *
 * <p>Day la buoc de sai nhat cua ca luong: mot file "to" tren man hinh chua chac to khi
 * in. Quy tac:
 * <ul>
 *   <li><b>PDF</b>: lay hop theo thu tu uu tien TrimBox &rarr; CropBox &rarr; MediaBox.
 *       TrimBox moi la kich thuoc thanh pham sau khi xen; MediaBox thuong con ca vung
 *       tran le nen dung no se ra hinh to hon thuc te.</li>
 *   <li><b>Anh</b>: doi pixel sang milimet theo DPI ghi trong metadata. Khong co DPI thi
 *       coi la 72 DPI - quy uoc cua Illustrator/Photoshop khi xuat web.</li>
 * </ul>
 */
@Component
public class FileMetadataReader {

    /** DPI mac dinh khi anh khong khai bao do phan giai. */
    private static final double DEFAULT_DPI = 72d;

    private static final double MM_PER_INCH = 25.4d;

    /**
     * Doc kich thuoc vat ly cua file.
     *
     * @param path duong dan file tren dia
     * @param type loai file
     * @return mang {@code [widthMm, heightMm, pageCount]}
     * @throws ApiException khi khong doc duoc kich thuoc
     */
    public Dimensions read(Path path, FileType type, String originalName) {
        try {
            return type == FileType.PDF ? readPdf(path) : readImage(path);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(ErrorCode.SIZE_UNREADABLE,
                    "Khong doc duoc kich thuoc cua file \"" + originalName + "\". "
                            + "Hay kiem tra file co bi hong khong, hoac nhap kich thuoc thu cong.");
        }
    }

    /** Kich thuoc vat ly doc duoc. */
    public record Dimensions(double widthMm, double heightMm, int pageCount) {
    }

    private Dimensions readPdf(Path path) throws IOException {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            if (document.getNumberOfPages() == 0) {
                throw new ApiException(ErrorCode.SIZE_UNREADABLE, "File PDF khong co trang nao.");
            }
            PDPage page = document.getPage(0);
            PDRectangle box = effectiveBox(page);

            // Trang co the duoc danh dau xoay 90/270 do; kich thuoc hien thi bi hoan doi.
            int rotation = ((page.getRotation() % 360) + 360) % 360;
            boolean swapped = rotation == 90 || rotation == 270;
            double widthPt = swapped ? box.getHeight() : box.getWidth();
            double heightPt = swapped ? box.getWidth() : box.getHeight();

            return new Dimensions(
                    Units.round2(Units.ptToMm(widthPt)),
                    Units.round2(Units.ptToMm(heightPt)),
                    document.getNumberOfPages());
        }
    }

    /** TrimBox truoc, roi CropBox, cuoi cung MediaBox. */
    static PDRectangle effectiveBox(PDPage page) {
        PDRectangle trim = page.getTrimBox();
        if (isUsable(trim)) {
            return trim;
        }
        PDRectangle crop = page.getCropBox();
        if (isUsable(crop)) {
            return crop;
        }
        return page.getMediaBox();
    }

    private static boolean isUsable(PDRectangle box) {
        return box != null && box.getWidth() > 0 && box.getHeight() > 0;
    }

    private Dimensions readImage(Path path) throws IOException {
        try (ImageInputStream stream = ImageIO.createImageInputStream(path.toFile())) {
            if (stream == null) {
                throw new ApiException(ErrorCode.SIZE_UNREADABLE, "Khong mo duoc file anh.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new ApiException(ErrorCode.UNSUPPORTED_FORMAT, "Dinh dang anh khong duoc ho tro.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true);
                int widthPx = reader.getWidth(0);
                int heightPx = reader.getHeight(0);
                double[] dpi = readDpi(reader);

                return new Dimensions(
                        Units.round2(widthPx / dpi[0] * MM_PER_INCH),
                        Units.round2(heightPx / dpi[1] * MM_PER_INCH),
                        1);
            } finally {
                reader.dispose();
            }
        }
    }

    /**
     * Rut DPI ngang/doc tu metadata chuan cua ImageIO.
     *
     * <p>Node {@code HorizontalPixelSize} ghi kich thuoc mot pixel bang MILIMET, nen DPI
     * la {@code 25.4 / giaTri}.
     */
    private double[] readDpi(ImageReader reader) {
        double horizontal = DEFAULT_DPI;
        double vertical = DEFAULT_DPI;
        try {
            IIOMetadata metadata = reader.getImageMetadata(0);
            if (metadata == null) {
                return new double[]{horizontal, vertical};
            }
            Element root = (Element) metadata.getAsTree("javax_imageio_1.0");
            double h = pixelSizeToDpi(root, "HorizontalPixelSize");
            double v = pixelSizeToDpi(root, "VerticalPixelSize");
            if (h > 0) {
                horizontal = h;
            }
            if (v > 0) {
                vertical = v;
            }
        } catch (IOException | RuntimeException ignored) {
            // Khong doc duoc metadata thi dung 72 DPI - van tot hon la bao loi ca file.
        }
        return new double[]{horizontal, vertical};
    }

    private double pixelSizeToDpi(Element root, String nodeName) {
        NodeList nodes = root.getElementsByTagName(nodeName);
        if (nodes.getLength() == 0) {
            return -1;
        }
        String value = ((Element) nodes.item(0)).getAttribute("value");
        try {
            double millimetresPerPixel = Double.parseDouble(value);
            return millimetresPerPixel > 0 ? MM_PER_INCH / millimetresPerPixel : -1;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /** Doc anh de tao file xem truoc. */
    public BufferedImage loadImage(File file) throws IOException {
        return ImageIO.read(file);
    }
}
