package vn.printnest.file;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.printnest.common.ApiException;
import vn.printnest.common.AppProperties;
import vn.printnest.common.ErrorCode;
import vn.printnest.common.Units;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.geom.Rectangle2D;
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
 *   <li><b>PDF</b>: doc content stream de tim vung CO NET VE THAT SU, bo qua khoang trang
 *       bao quanh (xem {@link PdfContentBoxFinder}). Khong tim duoc thi lui ve hop trang
 *       theo thu tu uu tien TrimBox &rarr; CropBox &rarr; MediaBox.</li>
 *   <li><b>Anh</b>: doi pixel sang milimet theo DPI ghi trong metadata. Khong co DPI thi
 *       coi la 72 DPI - quy uoc cua Illustrator/Photoshop khi xuat web.</li>
 * </ul>
 */
@Component
public class FileMetadataReader {

    private static final Logger log = LoggerFactory.getLogger(FileMetadataReader.class);

    /** DPI mac dinh khi anh khong khai bao do phan giai. */
    private static final double DEFAULT_DPI = 72d;

    private static final double MM_PER_INCH = 25.4d;

    /**
     * Canh nho nhat con duoc coi la ket qua cat hop le, tinh bang milimet.
     *
     * <p>Hop bao be hon nguong nay gan nhu chac chan la doc nham (file dung cau truc la,
     * hoac chi co mot dau cham vo tinh); luc do thà lay ca kho trang con hon cat mat hinh.
     */
    private static final double MIN_TRIMMED_MM = 1.0;

    private final AppProperties properties;

    public FileMetadataReader(AppProperties properties) {
        this.properties = properties;
    }

    /**
     * Doc kich thuoc vat ly cua file.
     *
     * @param path         duong dan file tren dia
     * @param type         loai file
     * @param originalName ten goc, chi dung de bao loi cho de hieu
     * @return kich thuoc da doc duoc
     * @throws ApiException khi khong doc duoc kich thuoc
     */
    public Metadata read(Path path, FileType type, String originalName) {
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

    /**
     * Ket qua doc duoc tu mot file nguon.
     *
     * @param widthMm        chieu rong dung de xep (da cat khoang trang)
     * @param heightMm       chieu cao dung de xep
     * @param sourceWidthMm  chieu rong kho trang nguyen ban
     * @param sourceHeightMm chieu cao kho trang nguyen ban
     * @param contentBox     vung co net ve, he toa do goc cua file; null neu khong cat
     * @param occupancy      ban do chiem cho ben trong khung bao; null voi anh
     * @param pageRotation   goc xoay khai bao o trang PDF
     * @param pageCount      so trang
     */
    public record Metadata(
            double widthMm,
            double heightMm,
            double sourceWidthMm,
            double sourceHeightMm,
            ContentBox contentBox,
            OccupancyMask occupancy,
            int pageRotation,
            int pageCount,
            boolean opaqueRaster
    ) {
    }

    private Metadata readPdf(Path path) throws IOException {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            if (document.getNumberOfPages() == 0) {
                throw new ApiException(ErrorCode.SIZE_UNREADABLE, "File PDF khong co trang nao.");
            }
            PDPage page = document.getPage(0);
            PDRectangle pageBox = effectiveBox(page);
            int rotation = ((page.getRotation() % 360) + 360) % 360;
            boolean swapped = rotation == 90 || rotation == 270;

            ContentBox box = new ContentBox(pageBox.getLowerLeftX(), pageBox.getLowerLeftY(),
                    pageBox.getWidth(), pageBox.getHeight());
            OccupancyMask occupancy = null;

            if (properties.trim().enabled()) {
                PdfContentBoxFinder finder = runFinder(page);
                ContentBox trimmed = finder == null ? null : findContentBox(finder, pageBox);
                if (trimmed != null) {
                    box = trimmed;
                    // Ban do chiem cho chi co y nghia khi biet chac khung bao; dung lai
                    // ket qua cua CUNG mot lan duyet content stream, khong duyet hai lan.
                    occupancy = OccupancyMask.build(finder.shapes(), box,
                            OccupancyMask.DEFAULT_CELL_MM);
                }
            }

            double widthMm = Units.ptToMm(box.widthPt());
            double heightMm = Units.ptToMm(box.heightPt());
            double sourceWidthMm = Units.ptToMm(pageBox.getWidth());
            double sourceHeightMm = Units.ptToMm(pageBox.getHeight());

            return new Metadata(
                    Units.round2(swapped ? heightMm : widthMm),
                    Units.round2(swapped ? widthMm : heightMm),
                    Units.round2(swapped ? sourceHeightMm : sourceWidthMm),
                    Units.round2(swapped ? sourceWidthMm : sourceHeightMm),
                    box,
                    occupancy,
                    rotation,
                    document.getNumberOfPages(),
                    // PDF luon coi nhu co do trong suot: mau trang trong do la net ve.
                    false);
        }
    }

    /**
     * Tim hop bao net ve, da kiem tra tinh hop ly.
     *
     * @return hop bao da cat, hoac null neu nen giu nguyen ca kho trang
     */
    private PdfContentBoxFinder runFinder(PDPage page) {
        try {
            PdfContentBoxFinder finder = new PdfContentBoxFinder(page);
            finder.find();
            return finder;
        } catch (IOException | RuntimeException ex) {
            // File la, phong hong, content stream khong doc duoc... Khong sao: lui ve
            // dung ca kho trang, van in duoc, chi la ton giay hon.
            log.warn("Khong doc duoc vung net ve, dung ca kho trang: {}", ex.toString());
            return null;
        }
    }

    private ContentBox findContentBox(PdfContentBoxFinder finder, PDRectangle pageBox) {
        Rectangle2D ink = finder.bounds();
        if (ink == null) {
            return null;
        }

        // Net ve co the tran ra ngoai kho trang; phan tran ra do se bi xen khi in nen
        // khong duoc tinh vao kich thuoc.
        Rectangle2D clipped = ink.createIntersection(new Rectangle2D.Double(
                pageBox.getLowerLeftX(), pageBox.getLowerLeftY(),
                pageBox.getWidth(), pageBox.getHeight()));

        if (clipped.getWidth() <= 0 || clipped.getHeight() <= 0) {
            return null;
        }
        if (Units.ptToMm(clipped.getWidth()) < MIN_TRIMMED_MM
                || Units.ptToMm(clipped.getHeight()) < MIN_TRIMMED_MM) {
            log.warn("Vung net ve nho bat thuong ({} x {} pt), dung ca kho trang de an toan",
                    Math.round(clipped.getWidth()), Math.round(clipped.getHeight()));
            return null;
        }

        return new ContentBox(clipped.getX(), clipped.getY(), clipped.getWidth(), clipped.getHeight());
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

    /**
     * Doc kich thuoc anh.
     *
     * <p>Anh KHONG duoc cat khoang trang: khac voi PDF, anh khong co "net ve" de doc, chi
     * co mau pixel. Ma nen trang cua mot file anh rat co the la phan co y de chua - vien
     * trang cua decal chang han. Cat di la hong ban in, nen o day giu nguyen; nguoi dung
     * muon cat thi sua tay o cot kich thuoc trong bang.
     */
    private Metadata readImage(Path path) throws IOException {
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

                double widthMm = Units.round2(widthPx / dpi[0] * MM_PER_INCH);
                double heightMm = Units.round2(heightPx / dpi[1] * MM_PER_INCH);
                return new Metadata(widthMm, heightMm, widthMm, heightMm, null, null, 0, 1,
                        !hasAlpha(reader));
            } finally {
                reader.dispose();
            }
        }
    }

    /**
     * Anh nay co kenh trong suot khong.
     *
     * <p>Quyet dinh mau TRANG trong file duoc hieu la gi. Anh CO kenh trong suot thi cho
     * nao khong in da duoc bao bang do trong suot roi, nen mau trang con lai la net ve co
     * chu y - giong het file PDF. Anh KHONG co kenh trong suot (anh JPG chang han) thi
     * mang trang bao quanh hinh thuong la nen can bo di.
     *
     * <p>Doc qua kieu anh ma bo doc khai bao, khong giai nen ca anh.
     *
     * @return {@code false} neu khong xac dinh duoc - khi do coi nhu co kenh trong suot,
     *         tuc la GIU mau trang. Tha in thua mot mang trang (tho nhin ban nhap la
     *         thay ngay) con hon in thieu chu trang (chi lo ra khi muc da len ao)
     */
    private boolean hasAlpha(ImageReader reader) {
        try {
            Iterator<javax.imageio.ImageTypeSpecifier> types = reader.getImageTypes(0);
            if (types.hasNext()) {
                return types.next().getColorModel().hasAlpha();
            }
        } catch (IOException | RuntimeException ex) {
            log.warn("Khong doc duoc kieu anh, coi nhu co kenh trong suot: {}", ex.toString());
        }
        return true;
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
}
