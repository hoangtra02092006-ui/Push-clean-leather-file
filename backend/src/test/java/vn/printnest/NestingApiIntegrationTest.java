package vn.printnest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import vn.printnest.common.Units;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiem thu tich hop tron luong: upload &rarr; tao job &rarr; cho DONE &rarr; tai PDF.
 *
 * <p>Diem quan trong nhat la kiem tra kich thuoc trang PDF xuat ra dung toi 0,1 mm - do
 * la cho de sai nhat vi phai di qua ba he don vi (milimet &rarr; 1/100 mm &rarr; point).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.storage.path=${java.io.tmpdir}/printnest-test-storage",
        "logging.level.vn.printnest=WARN"
})
class NestingApiIntegrationTest {

    /** Sai so cho phep khi kiem tra kich thuoc trang PDF, tinh bang milimet. */
    private static final double PDF_TOLERANCE_MM = 0.1;

    private static final long POLL_TIMEOUT_MS = 60_000;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Tron luong: upload PDF, ghep, tai ve va kiem tra kich thuoc trang")
    void fullRoundTrip() throws Exception {
        // 1. Upload hai file PDF co kich thuoc biet truoc.
        String smallId = uploadPdf("nhan-nho.pdf", 100, 60);
        String largeId = uploadPdf("nhan-to.pdf", 200, 180);

        // 2. Tao job ghep: kho 57 cm, le 0.5 cm, gap 0.3 cm, khong gioi han chieu dai.
        String body = """
                {
                  "sheetWidthMm": 570,
                  "marginMm": 5,
                  "gapMm": 3,
                  "maxSheetLengthMm": null,
                  "allowRotateGlobal": true,
                  "drawCutLines": true,
                  "items": [
                    { "fileId": "%s", "quantity": 8, "allowRotate": true },
                    { "fileId": "%s", "quantity": 4, "allowRotate": true }
                  ]
                }
                """.formatted(smallId, largeId);

        MvcResult created = mockMvc.perform(post("/api/v1/nesting/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").exists())
                .andReturn();

        String jobId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("jobId").asText();

        // 3. Poll toi khi job xong.
        JsonNode done = pollUntilDone(jobId);
        assertThat(done.get("status").asText()).isEqualTo("DONE");

        JsonNode stats = done.get("result").get("stats");
        assertThat(stats.get("totalPieces").asInt())
                .as("tong so hinh phai dung bang tong so luong yeu cau")
                .isEqualTo(12);
        assertThat(stats.get("totalSheets").asInt()).isEqualTo(1);

        JsonNode sheet = done.get("result").get("sheets").get(0);
        double expectedWidthMm = sheet.get("widthMm").asDouble();
        double expectedLengthMm = sheet.get("lengthMm").asDouble();
        assertThat(sheet.get("placements")).hasSize(12);

        // 4. Tai PDF cua tam dau va doi chieu kich thuoc trang.
        MvcResult pdfResult = mockMvc.perform(get("/api/v1/nesting/jobs/{id}/sheets/0/pdf", jobId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andReturn();

        byte[] pdfBytes = pdfResult.getResponse().getContentAsByteArray();
        assertThat(pdfBytes).isNotEmpty();

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            PDRectangle box = document.getPage(0).getMediaBox();
            assertThat(Units.ptToMm(box.getWidth()))
                    .as("chieu rong trang PDF")
                    .isCloseTo(expectedWidthMm, org.assertj.core.data.Offset.offset(PDF_TOLERANCE_MM));
            assertThat(Units.ptToMm(box.getHeight()))
                    .as("chieu dai trang PDF")
                    .isCloseTo(expectedLengthMm, org.assertj.core.data.Offset.offset(PDF_TOLERANCE_MM));
        }

        // 5. Tai ca goi .zip. Endpoint nay ghi thang ra dap ung nen phai qua mot nhip
        // asyncDispatch - xem chu thich o ExportController.exportAll.
        MvcResult zipStarted = mockMvc.perform(get("/api/v1/nesting/jobs/{id}/export.zip", jobId))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .request().asyncStarted())
                .andReturn();
        MvcResult zipResult = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .asyncDispatch(zipStarted))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(zipResult.getResponse().getContentAsByteArray()).isNotEmpty();
    }

    @Test
    @DisplayName("Hinh rong hon kho bi tu choi voi ma ITEM_WIDER_THAN_SHEET")
    void rejectsOversizedItem() throws Exception {
        String bigId = uploadPdf("qua-kho.pdf", 700, 900);

        String body = """
                {
                  "sheetWidthMm": 570,
                  "marginMm": 5,
                  "gapMm": 3,
                  "allowRotateGlobal": true,
                  "items": [ { "fileId": "%s", "quantity": 1, "allowRotate": true } ]
                }
                """.formatted(bigId);

        mockMvc.perform(post("/api/v1/nesting/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("ITEM_WIDER_THAN_SHEET"));
    }

    @Test
    @DisplayName("Job khong ton tai tra ve JOB_NOT_FOUND")
    void unknownJob() throws Exception {
        mockMvc.perform(get("/api/v1/nesting/jobs/{id}", "khong-co-that"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_NOT_FOUND"));
    }

    @Test
    @DisplayName("Dinh dang khong ho tro bi tu choi")
    void rejectsUnsupportedFormat() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "ban-ve.dxf",
                "application/octet-stream", "khong phai pdf".getBytes());

        mockMvc.perform(multipart("/api/v1/files").file(file))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_FORMAT"));
    }

    @Test
    @DisplayName("Doc dung kich thuoc vat ly cua PDF tai len")
    void readsPdfDimensions() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "the-kiem-tra.pdf",
                MediaType.APPLICATION_PDF_VALUE, buildPdf(210, 297));

        mockMvc.perform(multipart("/api/v1/files").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].originalName").value("the-kiem-tra.pdf"))
                .andExpect(jsonPath("$[0].type").value("PDF"))
                .andExpect(jsonPath("$[0].widthMm").value(org.hamcrest.Matchers.closeTo(210, PDF_TOLERANCE_MM)))
                .andExpect(jsonPath("$[0].heightMm").value(org.hamcrest.Matchers.closeTo(297, PDF_TOLERANCE_MM)));
    }

    // ------------------------------------------------------------------
    // Tien ich
    // ------------------------------------------------------------------

    /** Upload mot PDF sinh tai cho va tra ve ma file. */
    private String uploadPdf(String name, double widthMm, double heightMm) throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", name,
                MediaType.APPLICATION_PDF_VALUE, buildPdf(widthMm, heightMm));

        MvcResult result = mockMvc.perform(multipart("/api/v1/files").file(file))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get(0).get("id").asText();
    }

    /** Sinh mot file PDF mot trang dung kich thuoc yeu cau, co ve chut noi dung vector. */
    private byte[] buildPdf(double widthMm, double heightMm) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(Units.mmToPt(widthMm), Units.mmToPt(heightMm)));
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.setNonStrokingColor(0.9f, 0.9f, 0.95f);
                content.addRect(0, 0, Units.mmToPt(widthMm), Units.mmToPt(heightMm));
                content.fill();
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            document.save(bytes);
            return bytes.toByteArray();
        }
    }

    /** Hoi trang thai job cho toi khi DONE hoac FAILED. */
    private JsonNode pollUntilDone(String jobId) throws Exception {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        JsonNode last = null;

        while (System.currentTimeMillis() < deadline) {
            MvcResult result = mockMvc.perform(get("/api/v1/nesting/jobs/{id}", jobId))
                    .andExpect(status().isOk())
                    .andReturn();
            last = objectMapper.readTree(result.getResponse().getContentAsString());
            String status = last.get("status").asText();

            if ("DONE".equals(status)) {
                return last;
            }
            if ("FAILED".equals(status)) {
                throw new AssertionError("Job that bai: " + last.get("error"));
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Job khong xong trong " + POLL_TIMEOUT_MS + " ms, lan cuoi: " + last);
    }

    private static org.springframework.test.web.servlet.result.HeaderResultMatchers header() {
        return org.springframework.test.web.servlet.result.MockMvcResultMatchers.header();
    }
}
