package vn.printnest.export;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.printnest.common.ApiException;
import vn.printnest.common.ErrorCode;
import vn.printnest.job.Job;
import vn.printnest.nesting.NestingService;
import vn.printnest.nesting.model.Sheet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Tai file PDF thanh pham cua mot lan ghep. */
@RestController
@RequestMapping("/api/v1/nesting/jobs/{jobId}")
public class ExportController {

    /**
     * Tien to ten file tai ve.
     *
     * <p>Khong dau va khong khoang trang: ten file di qua header HTTP roi xuong may in,
     * dau tieng Viet o do rat de bi bo doc sai.
     */
    private static final String BRAND = "minh-tri";

    private final NestingService nestingService;
    private final PdfComposer composer;

    public ExportController(NestingService nestingService, PdfComposer composer) {
        this.nestingService = nestingService;
        this.composer = composer;
    }

    /** Tai PDF cua mot tam. */
    @GetMapping("/sheets/{index}/pdf")
    public ResponseEntity<byte[]> sheetPdf(@PathVariable String jobId, @PathVariable int index) {
        Job job = nestingService.requireDone(jobId);
        Sheet sheet = sheetAt(job, index);
        byte[] pdf = composer.compose(sheet, job.request().drawCutLinesOrDefault());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName(job, sheet) + "\"")
                .body(pdf);
    }

    /** Tai tat ca cac tam trong mot file .zip. */
    @GetMapping("/export.zip")
    public ResponseEntity<byte[]> exportAll(@PathVariable String jobId) {
        Job job = nestingService.requireDone(jobId);
        boolean cutLines = job.request().drawCutLinesOrDefault();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            for (Sheet sheet : job.result().sheets()) {
                zip.putNextEntry(new ZipEntry(fileName(job, sheet)));
                zip.write(composer.compose(sheet, cutLines));
                zip.closeEntry();
            }
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Khong nen duoc file zip: " + ex.getMessage());
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + BRAND + "-" + shortId(jobId) + ".zip\"")
                .body(buffer.toByteArray());
    }

    private Sheet sheetAt(Job job, int index) {
        var sheets = job.result().sheets();
        if (index < 0 || index >= sheets.size()) {
            throw new ApiException(ErrorCode.JOB_NOT_FOUND,
                    "Lan ghep nay chi co " + sheets.size() + " tam, khong co tam so " + (index + 1) + ".");
        }
        return sheets.get(index);
    }

    /** Ten file mang san kich thuoc de tho khong phai mo ra kiem tra. */
    private String fileName(Job job, Sheet sheet) {
        long widthCm = Math.round(sheet.widthMm() / 10d);
        long lengthCm = Math.round(sheet.lengthMm() / 10d);
        return String.format("%s-%s-tam%02d-%dx%dcm.pdf",
                BRAND, shortId(job.jobId()), sheet.index() + 1, widthCm, lengthCm);
    }

    private String shortId(String jobId) {
        return jobId.length() > 8 ? jobId.substring(0, 8) : jobId;
    }
}
