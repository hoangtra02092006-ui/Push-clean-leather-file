package vn.printnest.export;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import vn.printnest.common.ApiException;
import vn.printnest.common.ErrorCode;
import vn.printnest.job.Job;
import vn.printnest.nesting.NestingService;
import vn.printnest.nesting.model.Sheet;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Tai file thanh pham cua mot lan ghep.
 *
 * <p>Hai dinh dang, cung mot bo tri: PDF giu nguyen vector, TIF la anh bitmap cho nhung
 * RIP chi nhan anh, va CUT la duong cat vector cho may cat Graphtec. Ca ba deu dung lai
 * {@link PdfComposer} nen khong the lech nhau.
 */
@RestController
@RequestMapping("/api/v1/nesting/jobs/{jobId}")
public class ExportController {

    private static final Logger log = LoggerFactory.getLogger(ExportController.class);

    /**
     * Tien to ten file tai ve.
     *
     * <p>Khong dau va khong khoang trang: ten file di qua header HTTP roi xuong may in,
     * dau tieng Viet o do rat de bi bo doc sai.
     */
    private static final String BRAND = "minh-tri";

    /** Kieu MIME cua TIFF theo dang ky IANA. */
    private static final MediaType IMAGE_TIFF = MediaType.parseMediaType("image/tiff");

    private final NestingService nestingService;
    private final PdfComposer composer;
    private final TiffComposer tiffComposer;
    private final CutComposer cutComposer;
    private final ExportCache cache;

    public ExportController(NestingService nestingService, PdfComposer composer,
                            TiffComposer tiffComposer, CutComposer cutComposer,
                            ExportCache cache) {
        this.nestingService = nestingService;
        this.composer = composer;
        this.tiffComposer = tiffComposer;
        this.cutComposer = cutComposer;
        this.cache = cache;
    }

    /**
     * Dung mot tam, dung lai ban da dung neu co.
     *
     * <p>Tho hay tai mot tam de xem roi moi tai ca bo .zip. Khong co buoc nay thi moi tam
     * deu bi dung lai lan thu hai, ma mot ban TIF mat vai giay.
     */
    private byte[] build(Job job, Sheet sheet, String ext, boolean cutLines) {
        return build(job, sheet, ext, cutLines, true);
    }

    /**
     * Dung mot tam.
     *
     * @param store co giu lai ban vua dung khong. Luc tai ca bo .zip thi KHONG - xem
     *              {@link ExportCache#get(String, int, String, java.util.function.Supplier, boolean)}
     */
    private byte[] build(Job job, Sheet sheet, String ext, boolean cutLines, boolean store) {
        return cache.get(job.jobId(), sheet.index(), ext, () -> switch (ext) {
            case "tif" -> tiffComposer.compose(sheet, cutLines);
            // File cat khong nhan cutLines: khung xam quanh hinh ma bat len thi duong cat
            // se di vong quanh cai khung chu khong quanh hinh.
            case "cut" -> cutComposer.compose(sheet);
            default -> composer.compose(sheet, cutLines);
        }, store);
    }

    /** Tai file CAT cua mot tam, dinh dang PDF vector cho may cat Graphtec. */
    @GetMapping("/sheets/{index}/cut")
    public ResponseEntity<byte[]> sheetCut(@PathVariable String jobId, @PathVariable int index) {
        Job job = nestingService.requireDone(jobId);
        Sheet sheet = sheetAt(job, index);
        byte[] cut = build(job, sheet, "cut", false);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName(job, sheet, "cut") + "\"")
                .body(cut);
    }

    /** Tai PDF cua mot tam. */
    @GetMapping("/sheets/{index}/pdf")
    public ResponseEntity<byte[]> sheetPdf(@PathVariable String jobId, @PathVariable int index) {
        Job job = nestingService.requireDone(jobId);
        Sheet sheet = sheetAt(job, index);
        byte[] pdf = build(job, sheet, "pdf", job.request().drawCutLinesOrDefault());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName(job, sheet, "pdf") + "\"")
                .body(pdf);
    }

    /** Tai TIF cua mot tam. */
    @GetMapping("/sheets/{index}/tif")
    public ResponseEntity<byte[]> sheetTif(@PathVariable String jobId, @PathVariable int index) {
        Job job = nestingService.requireDone(jobId);
        Sheet sheet = sheetAt(job, index);
        byte[] tiff = build(job, sheet, "tif", job.request().drawCutLinesOrDefault());

        return ResponseEntity.ok()
                .contentType(IMAGE_TIFF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName(job, sheet, "tif") + "\"")
                .body(tiff);
    }

    /**
     * Tai tat ca cac tam trong mot file .zip.
     *
     * <p><b>Ghi THANG ra dap ung, khong dung cai zip trong bo nho.</b> Ban cu gom ca zip
     * vao mot {@code ByteArrayOutputStream} roi goi {@code toByteArray()} - tuc la giu HAI
     * ban cua toan bo file. Do that tren mot don 11 tam 57 x 99 cm: moi tam 17-42 MB, cong
     * lai khoang 300 MB, nhan doi thanh 600 MB, va may chu het bo nho giua chung sau khi
     * tho da doi gan nam phut. Ghi thang thi luc nao cung chi co MOT tam trong bo nho.
     *
     * <p>Doi lai: da gui byte dau tien di thi khong the doi ma trang thai nua. Tam nao
     * dung hong giua chung thi zip dut o do - trinh duyet bao file hong, va log ghi ro tam
     * nao. Danh doi nay chap nhan duoc vi ban cu KHONG chay noi ngay tu dau.
     *
     * @param format {@code pdf} (mac dinh), {@code tif} hoac {@code cut}
     */
    @GetMapping("/export.zip")
    public ResponseEntity<StreamingResponseBody> exportAll(
            @PathVariable String jobId,
            @RequestParam(defaultValue = "pdf") String format) {

        String ext = normaliseFormat(format);
        Job job = nestingService.requireDone(jobId);
        boolean cutLines = job.request().drawCutLinesOrDefault();

        StreamingResponseBody body = output -> {
            long start = System.currentTimeMillis();
            int failed = 0;

            try (ZipOutputStream zip = new ZipOutputStream(output)) {
                for (Sheet sheet : job.result().sheets()) {
                    byte[] file;
                    try {
                        file = build(job, sheet, ext, cutLines, false);
                    } catch (RuntimeException ex) {
                        // Da gui byte dau tien di roi thi khong doi duoc ma trang thai nua.
                        // Nen thay vi de goi zip DUT giua chung - van tai ve duoc, van mo
                        // duoc vai file dau, rat de tuong la xong - ghi han mot file bao
                        // loi vao goi. Goi van hop le, va tho thay ngay tam nao hong.
                        failed++;
                        log.error("Tam {} hong nen khong vao duoc goi zip: {}",
                                sheet.index() + 1, ex.getMessage());
                        zip.putNextEntry(new ZipEntry(
                                String.format("LOI-tam%02d.txt", sheet.index() + 1)));
                        zip.write(("Khong dung duoc tam " + (sheet.index() + 1) + ".\r\n\r\n"
                                + ex.getMessage() + "\r\n").getBytes(StandardCharsets.UTF_8));
                        zip.closeEntry();
                        continue;
                    }

                    zip.putNextEntry(new ZipEntry(fileName(job, sheet, ext)));
                    zip.write(file);
                    zip.closeEntry();
                    // Day tam vua xong di ngay. Khong flush thi ca bo van don lai trong
                    // vung dem cua Tomcat, dung cai loi vua sua.
                    zip.flush();
                }
            }

            log.info("Da gui zip {} gom {} tam ({} tam hong) trong {} ms",
                    ext, job.result().sheets().size(), failed,
                    System.currentTimeMillis() - start);
        };

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + BRAND + "-" + shortId(jobId) + "-" + ext + ".zip\"")
                .body(body);
    }

    /** Chi nhan dung ba dinh dang; go nham thi bao ngay chu khong lang le tra ve PDF. */
    private String normaliseFormat(String format) {
        String ext = format == null ? "pdf" : format.trim().toLowerCase(java.util.Locale.ROOT);
        if (!"pdf".equals(ext) && !"tif".equals(ext) && !"cut".equals(ext)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Dinh dang \"" + format + "\" khong ho tro. Chi nhan pdf, tif hoac cut.");
        }
        return ext;
    }

    private Sheet sheetAt(Job job, int index) {
        var sheets = job.result().sheets();
        if (index < 0 || index >= sheets.size()) {
            throw new ApiException(ErrorCode.JOB_NOT_FOUND,
                    "Lan ghep nay chi co " + sheets.size() + " tam, khong co tam so " + (index + 1) + ".");
        }
        return sheets.get(index);
    }

    /**
     * Ten file mang san kich thuoc de tho khong phai mo ra kiem tra.
     *
     * <p>File cat cung la PDF nhung phai deo them chu {@code -cat}: de trung ten voi ban in
     * thi hai file nam canh nhau trong thu muc Tai ve, tho bam nham la day ban cat xuong
     * may in hoac day ban in xuong may cat.
     */
    private String fileName(Job job, Sheet sheet, String ext) {
        long widthCm = Math.round(sheet.widthMm() / 10d);
        long lengthCm = Math.round(sheet.lengthMm() / 10d);
        String suffix = "cut".equals(ext) ? "-cat.pdf" : "." + ext;
        return String.format("%s-%s-tam%02d-%dx%dcm%s",
                BRAND, shortId(job.jobId()), sheet.index() + 1, widthCm, lengthCm, suffix);
    }

    private String shortId(String jobId) {
        return jobId.length() > 8 ? jobId.substring(0, 8) : jobId;
    }
}
