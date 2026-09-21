package vn.printnest.file;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.printnest.common.ApiException;
import vn.printnest.common.AppProperties;
import vn.printnest.common.ErrorCode;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nhan file upload, luu xuong dia va doc kich thuoc vat ly.
 *
 * <p>Sieu du lieu giu trong bo nho ({@link ConcurrentHashMap}), con noi dung file nam tren
 * dia tai {@code app.storage.path}. O MVP chua can database; khi can lich su lau dai chi
 * viec thay map nay bang mot repository.
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    /** Chan duoi va chan tren cua do phan giai render PDF xem truoc. */
    private static final float MIN_PREVIEW_DPI = 24f;
    private static final float MAX_PREVIEW_DPI = 200f;

    /**
     * Canh dai nhat cua anh xem truoc, tinh bang pixel.
     *
     * <p>O xem truoc trong bang chi rong 44 px, nhung bam vao se mo to ra giua man hinh,
     * nen anh phai du net o ca hai co. Lay 360 px la du cho khung phong to ma file van
     * chi vai chuc KB.
     */
    private static final int PREVIEW_MAX_PX = 360;

    /** Muc nen JPEG cho anh chup: 0,82 gan nhu khong thay khac ma nhe hon PNG chuc lan. */
    private static final float JPEG_QUALITY = 0.82f;

    private final Map<String, StoredFile> files = new ConcurrentHashMap<>();

    /**
     * Anh xem truoc da dung, giu lai theo ma file.
     *
     * <p>Trinh duyet co the hoi lai anh nay nhieu lan (cuon bang, ve lai component, mo
     * hai tab). Dung mot lan roi giu lai tranh phai giai nen lai anh goc moi lan hoi.
     */
    private final Map<String, Preview> previewCache = new ConcurrentHashMap<>();

    /**
     * Anh xem truoc kem dinh dang cua no.
     *
     * <p>Dinh dang khong co dinh vi hai loai file can hai kieu nen rat khac nhau: net ve
     * tu PDF chi co vai mau nen PNG ra vai KB va sac canh, con anh chup thi PNG phinh to
     * (mot tam 360 px ra toi 258 KB) trong khi JPEG chi ton mot phan muoi ma mat thuong
     * khong phan biet duoc.
     *
     * @param bytes       noi dung anh
     * @param contentType kieu MIME de tra ve cho trinh duyet
     */
    public record Preview(byte[] bytes, String contentType) {
    }

    private final FileMetadataReader metadataReader;
    private final AppProperties properties;
    private final Path storageRoot;

    public FileService(FileMetadataReader metadataReader, AppProperties properties) throws IOException {
        this.metadataReader = metadataReader;
        this.properties = properties;
        this.storageRoot = Path.of(properties.storage().path()).toAbsolutePath().normalize();
        Files.createDirectories(storageRoot);
        log.info("Thu muc luu file: {}", storageRoot);
    }

    /**
     * Luu nhieu file cung luc va doc kich thuoc tung file.
     *
     * @param uploads danh sach file tu form multipart
     * @return thong tin cac file da luu, dung thu tu dau vao
     */
    public List<FileResponse> store(List<MultipartFile> uploads) {
        if (uploads == null || uploads.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Chua chon file nao de tai len.");
        }
        List<FileResponse> responses = new ArrayList<>(uploads.size());
        for (MultipartFile upload : uploads) {
            responses.add(FileResponse.from(storeOne(upload)));
        }
        return responses;
    }

    private StoredFile storeOne(MultipartFile upload) {
        String originalName = upload.getOriginalFilename() == null
                ? "khong-ten" : Path.of(upload.getOriginalFilename()).getFileName().toString();

        if (upload.getSize() > properties.storage().maxFileSize()) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE,
                    "File \"" + originalName + "\" vuot qua gioi han "
                            + (properties.storage().maxFileSize() / (1024 * 1024)) + " MB.");
        }

        FileType type = detectType(originalName);
        String id = UUID.randomUUID().toString();
        Path target = storageRoot.resolve(id + extensionOf(originalName));

        try {
            upload.transferTo(target);
        } catch (IOException ex) {
            log.error("Khong luu duoc file {}", originalName, ex);
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Khong luu duoc file \"" + originalName + "\" len may chu.");
        }

        FileMetadataReader.Metadata meta = metadataReader.read(target, type, originalName);
        if (meta.widthMm() <= 0 || meta.heightMm() <= 0) {
            throw new ApiException(ErrorCode.SIZE_UNREADABLE,
                    "File \"" + originalName + "\" co kich thuoc bang 0. Hay nhap kich thuoc thu cong.");
        }

        StoredFile stored = new StoredFile(id, originalName, target.toString(), type,
                meta.widthMm(), meta.heightMm(), meta.sourceWidthMm(), meta.sourceHeightMm(),
                meta.contentBox(), meta.occupancy(), meta.pageRotation(), meta.pageCount(),
                upload.getSize(), Instant.now());
        files.put(id, stored);

        if (stored.trimmed()) {
            log.info("Da nhan file {}: kho {} x {} mm, cat con {} x {} mm",
                    originalName, stored.sourceWidthMm(), stored.sourceHeightMm(),
                    stored.widthMm(), stored.heightMm());
        } else {
            log.info("Da nhan file {} ({} x {} mm)", originalName, stored.widthMm(), stored.heightMm());
        }
        return stored;
    }

    /**
     * Xoa cac file nhan truoc moc thoi gian: ca tren dia LAN trong bo nho.
     *
     * <p>Phai xoa ca ba cho, khong duoc quen cho nao:
     * <ul>
     *   <li>{@code files} - kem theo la ban do chiem cho ({@link OccupancyMask}), thu
     *       nang nhat: mot file kho lon co the giu toi nua megabyte trong bo nho;</li>
     *   <li>{@code previewCache} - anh xem truoc da dung san;</li>
     *   <li>chinh file tren dia.</li>
     * </ul>
     *
     * <p>Xoa file tren dia that bai thi KHONG bo qua ban ghi trong bo nho: cu go ra khoi
     * map roi ghi log canh bao. Giu lai ban ghi tro toi mot file co the da hong chi lam
     * lan them, ma bo nho thi van khong duoc giai phong.
     *
     * @param age tuoi toi da duoc giu lai
     * @return so file da xoa va tong dung luong da giai phong tren dia
     */
    public Purge purgeOlderThan(Duration age) {
        Instant cutoff = Instant.now().minus(age);
        int removed = 0;
        long freedBytes = 0;

        for (StoredFile file : List.copyOf(files.values())) {
            if (file.uploadedAt() == null || !file.uploadedAt().isBefore(cutoff)) {
                continue;
            }
            files.remove(file.id());
            previewCache.remove(file.id());
            try {
                if (Files.deleteIfExists(Path.of(file.storedPath()))) {
                    freedBytes += file.sizeBytes();
                }
            } catch (IOException ex) {
                log.warn("Khong xoa duoc file {} tren dia: {}", file.storedPath(), ex.getMessage());
            }
            removed++;
        }
        return new Purge(removed, freedBytes);
    }

    /**
     * Ket qua mot lan don rac.
     *
     * @param files      so file da xoa
     * @param freedBytes dung luong da giai phong tren dia
     */
    public record Purge(int files, long freedBytes) {
    }

    /** Lay thong tin file, bao loi ro neu khong con tren may chu. */
    public StoredFile require(String id) {
        StoredFile file = files.get(id);
        if (file == null) {
            throw new ApiException(ErrorCode.FILE_NOT_FOUND,
                    "Khong tim thay file " + id + ". Co the may chu da khoi dong lai, hay tai file len lai.");
        }
        return file;
    }

    /**
     * Sinh anh PNG xem truoc, da thu nho.
     *
     * <p>Anh nay chi de hien trong o 44 px cua bang, nen phai NHO. Truoc day ham nay tra
     * ve nguyen anh goc doi sang PNG: mot file JPEG 3 MB bien thanh PNG hon 30 MB, mat
     * vai giay chi de ve mot o ti hon - do chinh la cam giac "upload anh cham".
     *
     * <p>Giai phap: giai nen anh o do phan giai THAP ngay tu dau (subsampling), roi thu
     * nho ve toi da {@value #PREVIEW_MAX_PX} px va giu lai trong bo nho.
     */
    public Preview renderPreview(String id) {
        StoredFile file = require(id);
        Preview cached = previewCache.get(id);
        if (cached != null) {
            return cached;
        }

        try {
            BufferedImage image = file.type() == FileType.PDF
                    ? renderPdfPreview(file)
                    : readImageSubsampled(Path.of(file.storedPath()));
            if (image == null) {
                throw new ApiException(ErrorCode.SIZE_UNREADABLE, "Khong doc duoc noi dung file de xem truoc.");
            }

            BufferedImage thumbnail = scaleDown(image, PREVIEW_MAX_PX);
            Preview preview = file.type() == FileType.PDF
                    ? new Preview(encodePng(thumbnail), "image/png")
                    : new Preview(encodeJpeg(thumbnail), "image/jpeg");

            previewCache.put(id, preview);
            return preview;
        } catch (IOException ex) {
            log.error("Khong tao duoc anh xem truoc cho {}", file.originalName(), ex);
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Khong tao duoc anh xem truoc.");
        }
    }

    /**
     * Ve trang PDF dau tien, CAT DUNG BANG vung co net ve.
     *
     * <p>Anh xem truoc phai khop voi kich thuoc ghi trong bang. Bang hien kich thuoc DA
     * CAT khoang trang, nen neu anh van ve ca to giay thi tho nhin vao se tuong app doc
     * sai - dung thu ma nguoi dung bao.
     *
     * <p>Cach cat: dat lai CropBox cua trang bang {@link StoredFile#contentBox()} roi de
     * PDFBox tu ve. Khong tu cat anh bitmap sau khi ve, vi lam vay phai tu xu ly
     * {@code /Rotate} cua trang - dung nguon sai lech giua anh xem truoc va file xuat ra.
     * Tai lieu chi mo trong bo nho va khong luu lai nen sua CropBox o day la vo hai.
     */
    private BufferedImage renderPdfPreview(StoredFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(Path.of(file.storedPath()).toFile())) {
            ContentBox box = file.contentBox();
            float widthPt;
            float heightPt;
            if (box != null && box.widthPt() > 0 && box.heightPt() > 0) {
                PDPage page = document.getPage(0);
                page.setCropBox(new PDRectangle((float) box.xPt(), (float) box.yPt(),
                        (float) box.widthPt(), (float) box.heightPt()));
                widthPt = (float) box.widthPt();
                heightPt = (float) box.heightPt();
            } else {
                PDRectangle crop = document.getPage(0).getCropBox();
                widthPt = crop.getWidth();
                heightPt = crop.getHeight();
            }
            return new PDFRenderer(document).renderImageWithDPI(0, previewDpi(widthPt, heightPt));
        }
    }

    /**
     * Chon DPI sao cho canh dai cua vung duoc ve ra dung khoang {@value #PREVIEW_MAX_PX} px.
     *
     * <p>Truoc day DPI co dinh 36: hop ly voi ca to A4, nhung sau khi cat con mot con tem
     * 4 x 3 cm thi chi ra 57 px - mo tit. Tinh theo kich thuoc thuc te moi ra anh net deu
     * cho ca file to lan file be.
     */
    private float previewDpi(float widthPt, float heightPt) {
        double longestInch = Math.max(widthPt, heightPt) / 72.0;
        if (longestInch <= 0) {
            return MIN_PREVIEW_DPI;
        }
        double dpi = PREVIEW_MAX_PX / longestInch;
        return (float) Math.min(MAX_PREVIEW_DPI, Math.max(MIN_PREVIEW_DPI, dpi));
    }

    /**
     * Giai nen anh o do phan giai thap ngay tu dau.
     *
     * <p>{@link ImageReadParam#setSourceSubsampling} bao cho bo giai nen chi lay mot pixel
     * trong moi N pixel. Voi anh 4200x3000, lay N = 16 thi chi phai dung 262x187 px thay
     * vi 12,6 trieu pixel - nhanh hon hang chuc lan va gan nhu khong ton bo nho.
     */
    private BufferedImage readImageSubsampled(Path path) throws IOException {
        try (ImageInputStream stream = ImageIO.createImageInputStream(path.toFile())) {
            if (stream == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);

                int step = Math.max(1, Math.max(width, height) / PREVIEW_MAX_PX);
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(step, step, 0, 0);
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        }
    }

    /** Thu nho giu nguyen ty le; anh da nho hon nguong thi tra lai nguyen ven. */
    private BufferedImage scaleDown(BufferedImage source, int maxSide) {
        int width = source.getWidth();
        int height = source.getHeight();
        int longest = Math.max(width, height);
        if (longest <= maxSide) {
            return source;
        }

        double ratio = maxSide / (double) longest;
        int targetWidth = Math.max(1, (int) Math.round(width * ratio));
        int targetHeight = Math.max(1, (int) Math.round(height * ratio));

        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            // Nen trang: anh PNG trong suot ma ve thang se ra nen den.
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, targetWidth, targetHeight);
            g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g.dispose();
        }
        return scaled;
    }

    private byte[] encodePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    /**
     * Nen anh chup thanh JPEG chat luong {@value #JPEG_QUALITY}.
     *
     * <p>Phai di duong {@link ImageWriter} chu khong goi thang {@code ImageIO.write}, vi
     * ImageIO khong cho chon muc nen - mac dinh cua no cho ra file lon hon nhieu.
     */
    private byte[] encodeJpeg(BufferedImage image) throws IOException {
        BufferedImage opaque = withoutAlpha(image);
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(opaque, null, null), param);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    /** JPEG khong co kenh trong suot; phan trong suot phai dat len nen trang truoc. */
    private BufferedImage withoutAlpha(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage opaque = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = opaque.createGraphics();
        try {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, source.getWidth(), source.getHeight());
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return opaque;
    }

    private FileType detectType(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return FileType.PDF;
        }
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return FileType.IMAGE;
        }
        throw new ApiException(ErrorCode.UNSUPPORTED_FORMAT,
                "File \"" + name + "\" khong duoc ho tro. Chi nhan PDF, PNG hoac JPG.");
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot).toLowerCase(Locale.ROOT) : "";
    }
}
