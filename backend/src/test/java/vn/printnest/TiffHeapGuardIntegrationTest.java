package vn.printnest;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tran bo nho cua ban TIF phai do theo HEAP THAT, khong phai mot so diem anh co dinh.
 *
 * <p>Vi sao can bai rieng nay ben canh {@link TiffGuardIntegrationTest}: tran diem anh la
 * so CO DINH nen no khong biet may chu that co bao nhieu RAM. Muc mac dinh 100 trieu diem
 * o che do CMYK kem kenh trang la 1 GB - qua tren mot may 512 MB, va tran do khong bao gio
 * chan lai.
 *
 * <p>Het bo nho o muc container KHONG nem ra {@code OutOfMemoryError} de ma bat: he dieu
 * hanh giet thang tien trinh. Nen tang tra 502, may chu khoi dong lai, va moi lan ghep
 * dang giu trong bo nho mat sach - tho bam tai TIF thi nhan {@code JOB_NOT_FOUND} cho
 * chinh lan ghep vua chay xong. Do dung chuoi su kien tho gap tren ban Render.
 *
 * <p>Dat phan heap cho phep xuong cuc nho de kich hoat duoc trong bai test, va nang tran
 * diem anh len cao de chac chan cai chan lai la phep do HEAP chu khong phai phep dem diem.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.storage.path=${java.io.tmpdir}/printnest-test-tiff-heap",
        "app.tiff.color-mode=rgb",
        "app.tiff.max-megapixels=100000",
        "app.tiff.max-heap-fraction=0.0000001",
        "logging.level.vn.printnest=WARN"
})
class TiffHeapGuardIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Khong du heap: bao loi kem do phan giai nen dat, khong de he dieu hanh giet")
    void refusesWhenTheSheetCannotFitInHeap() throws Exception {
        String jobId = runJob();

        mockMvc.perform(get("/api/v1/nesting/jobs/{id}/sheets/0/tif", jobId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("TIFF_TOO_LARGE"))
                // Thong bao phai noi ro DAT BAO NHIEU, khong chi bao la hong.
                .andExpect(jsonPath("$.error.message").value(
                        org.hamcrest.Matchers.containsString("APP_TIFF_DPI")))
                .andExpect(jsonPath("$.error.message").value(
                        org.hamcrest.Matchers.containsString("MB bo nho")));
    }

    @Test
    @DisplayName("Tran bo nho chi ap cho TIF: ban PDF va ban cat van tai duoc")
    void printAndCutAreUnaffected() throws Exception {
        String jobId = runJob();

        mockMvc.perform(get("/api/v1/nesting/jobs/{id}/sheets/0/pdf", jobId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/nesting/jobs/{id}/sheets/0/cut", jobId))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------

    private String runJob() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "nhan.pdf",
                MediaType.APPLICATION_PDF_VALUE, buildPdf());
        MvcResult uploaded = mockMvc.perform(multipart("/api/v1/files").file(file))
                .andExpect(status().isOk())
                .andReturn();
        String fileId = objectMapper.readTree(uploaded.getResponse().getContentAsString())
                .get(0).get("id").asText();

        String body = """
                {
                  "sheetWidthMm": 570, "marginMm": 25, "gapMm": 3,
                  "allowRotateGlobal": true, "drawCutLines": false,
                  "items": [{"fileId": "%s", "quantity": 4, "allowRotate": true}]
                }
                """.formatted(fileId);
        MvcResult created = mockMvc.perform(post("/api/v1/nesting/jobs")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        String jobId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("jobId").asText();

        for (int i = 0; i < 200; i++) {
            String status = objectMapper.readTree(
                            mockMvc.perform(get("/api/v1/nesting/jobs/{id}", jobId))
                                    .andReturn().getResponse().getContentAsString())
                    .get("status").asText();
            if ("DONE".equals(status)) {
                return jobId;
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("Job khong chay xong");
    }

    private static byte[] buildPdf() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(283.46f, 141.73f));
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.addRect(20, 20, 200, 100);
                content.fill();
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            document.save(bytes);
            return bytes.toByteArray();
        }
    }
}
