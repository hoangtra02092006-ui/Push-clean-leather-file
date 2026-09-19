package vn.printnest.file;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.printnest.common.ApiException;
import vn.printnest.common.AppProperties;
import vn.printnest.common.ErrorCode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

    /** Do phan giai anh xem truoc cua file PDF. */
    private static final float PREVIEW_DPI = 36f;

    private final Map<String, StoredFile> files = new ConcurrentHashMap<>();
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

        FileMetadataReader.Dimensions dimensions = metadataReader.read(target, type, originalName);
        if (dimensions.widthMm() <= 0 || dimensions.heightMm() <= 0) {
            throw new ApiException(ErrorCode.SIZE_UNREADABLE,
                    "File \"" + originalName + "\" co kich thuoc bang 0. Hay nhap kich thuoc thu cong.");
        }

        StoredFile stored = new StoredFile(id, originalName, target.toString(), type,
                dimensions.widthMm(), dimensions.heightMm(), dimensions.pageCount(), upload.getSize());
        files.put(id, stored);
        log.info("Da nhan file {} ({} x {} mm)", originalName, stored.widthMm(), stored.heightMm());
        return stored;
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
     * Sinh anh PNG xem truoc.
     *
     * <p>Voi PDF thi render trang dau o do phan giai thap; voi anh thi tra lai chinh anh
     * do. Anh xem truoc chi phuc vu hien thi - viec xuat PDF van dung file goc nen khong
     * anh huong chat luong in.
     */
    public byte[] renderPreview(String id) {
        StoredFile file = require(id);
        try {
            BufferedImage image = file.type() == FileType.PDF
                    ? renderPdfPreview(file)
                    : ImageIO.read(Path.of(file.storedPath()).toFile());
            if (image == null) {
                throw new ApiException(ErrorCode.SIZE_UNREADABLE, "Khong doc duoc noi dung file de xem truoc.");
            }
            var output = new java.io.ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException ex) {
            log.error("Khong tao duoc anh xem truoc cho {}", file.originalName(), ex);
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Khong tao duoc anh xem truoc.");
        }
    }

    private BufferedImage renderPdfPreview(StoredFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(Path.of(file.storedPath()).toFile())) {
            return new PDFRenderer(document).renderImageWithDPI(0, PREVIEW_DPI);
        }
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
