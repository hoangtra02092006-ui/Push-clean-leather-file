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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiem thu tran cung cua ban TIF.
 *
 * <p>Raster hoa mot tam lon ton bo nho theo BINH PHUONG do phan giai: 57 x 100 cm o
 * 150 DPI la 80 MB, o 300 DPI la 318 MB, o 600 DPI la 1,3 GB. Khong co tran thi mot yeu
 * cau duy nhat du lam JVM het cho va tu khoi dong lai - mat sach viec cua moi nguoi dang
 * lam do.
 *
 * <p>Dat tran xuong 1 trieu diem anh de kich hoat duoc trong bai test. O that la 80 trieu.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.storage.path=${java.io.tmpdir}/printnest-test-tiff-guard",
        // 600 DPI + tran 1 trieu diem: du de kich hoat voi mot tam be trong bai test.
        // O that la 150 DPI va tran 80 trieu.
        "app.tiff.dpi=600",
        "app.tiff.max-megapixels=1",
        "logging.level.vn.printnest=WARN"
})
class TiffGuardIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Tam vuot tran diem anh: bao loi ro rang, khong de may chu het bo nho")
    void oversizedSheetIsRefusedClearly() throws Exception {
        String jobId = runJob();

        mockMvc.perform(get("/api/v1/nesting/jobs/{id}/sheets/0/tif", jobId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("TIFF_TOO_LARGE"))
                // Thong bao phai noi ro CACH XU LY chu khong chi bao la hong.
                .andExpect(jsonPath("$.error.message").value(
                        org.hamcrest.Matchers.containsString("giam do phan giai")));
    }

    @Test
    @DisplayName("Tran chi ap cho TIF, ban PDF van tai duoc binh thuong")
    void pdfIsUnaffectedByTheTiffLimit() throws Exception {
        String jobId = runJob();

        mockMvc.perform(get("/api/v1/nesting/jobs/{id}/sheets/0/pdf", jobId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/nesting/jobs/{id}/export.zip", jobId))
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
                  "sheetWidthMm": 570, "marginMm": 5, "gapMm": 3,
                  "allowRotateGlobal": true, "drawCutLines": false,
                  "items": [{"fileId": "%s", "quantity": 6, "allowRotate": true}]
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
