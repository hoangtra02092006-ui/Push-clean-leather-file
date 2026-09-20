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
import vn.printnest.file.PdfContentBoxFinder;

import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiem thu viec cat bo khoang trang quanh hinh, tron luong tu upload toi PDF xuat ra.
 *
 * <p>Diem mau chot khong phai la "co cat duoc khong" ma la <b>cat xong hinh co con dung
 * cho khong</b>. Vi vay moi bai test deu doc nguoc lai vung net ve cua file PDF THANH PHAM
 * va doi chieu voi o ma thuat toan da dinh - neu phep bu toa do sai, hinh se lech vao
 * trong dung bang phan le da cat va bai test bat duoc ngay.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.storage.path=${java.io.tmpdir}/printnest-test-trim",
        "logging.level.vn.printnest=WARN"
})
class TrimWhitespaceIntegrationTest {

    /** Sai so cho phep khi doi chieu hinh hoc, tinh bang milimet. */
    private static final double EPS = 0.8;

    private static final long POLL_TIMEOUT_MS = 60_000;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Hinh nho giua trang A4: bao ra kich thuoc hinh chu khong phai kho trang")
    void reportsInkSizeNotPageSize() throws Exception {
        // Trang A4 210x297 mm, hinh that chi 30x30 mm dat tai (90,140).
        byte[] pdf = buildPdf(210, 297, content -> {
            content.addRect(pt(90), pt(140), pt(30), pt(30));
            content.fill();
        });

        JsonNode file = upload("nhan-le-loi.pdf", pdf);

        assertThat(file.get("widthMm").asDouble()).isCloseTo(30, within(EPS));
        assertThat(file.get("heightMm").asDouble()).isCloseTo(30, within(EPS));
        assertThat(file.get("sourceWidthMm").asDouble()).isCloseTo(210, within(EPS));
        assertThat(file.get("sourceHeightMm").asDouble()).isCloseTo(297, within(EPS));
        assertThat(file.get("trimmed").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Nhieu hinh nho trong mot file: gop lam mot, giu nguyen vi tri tuong doi")
    void manyShapesKeepRelativeLayout() throws Exception {
        // Ba hinh trong vung X 80..200, Y 150..260 mm tren trang A3.
        byte[] pdf = buildPdf(297, 420, content -> {
            content.addRect(pt(80), pt(150), pt(50), pt(40));
            content.fill();
            content.addRect(pt(140), pt(150), pt(60), pt(40));
            content.fill();
            content.addRect(pt(80), pt(200), pt(120), pt(60));
            content.fill();
        });

        JsonNode file = upload("ba-hinh.pdf", pdf);
        assertThat(file.get("widthMm").asDouble()).isCloseTo(120, within(EPS));
        assertThat(file.get("heightMm").asDouble()).isCloseTo(110, within(EPS));

        // Xuat ra PDF roi doc nguoc lai: ca cum ba hinh phai nam khit o da dinh.
        byte[] output = nestAndDownload(file.get("id").asText(), 1);
        Rectangle2D ink = inkBoxOf(output);

        assertThat(mm(ink.getWidth())).as("cum ba hinh giu nguyen be ngang").isCloseTo(120, within(EPS));
        assertThat(mm(ink.getHeight())).as("cum ba hinh giu nguyen chieu cao").isCloseTo(110, within(EPS));
    }

    @Test
    @DisplayName("Sau khi cat, hinh nam DUNG o da dinh trong PDF thanh pham")
    void trimmedArtworkLandsExactlyOnItsSlot() throws Exception {
        byte[] pdf = buildPdf(210, 297, content -> {
            content.addRect(pt(90), pt(140), pt(30), pt(30));
            content.fill();
        });

        String fileId = upload("mot-hinh.pdf", pdf).get("id").asText();
        byte[] output = nestAndDownload(fileId, 1);

        try (PDDocument document = Loader.loadPDF(output)) {
            PDRectangle sheet = document.getPage(0).getMediaBox();
            // Mot hinh 30x30 voi le 5 mm -> tam dai 40 mm.
            assertThat(mm(sheet.getHeight())).as("chieu dai tam").isCloseTo(40, within(EPS));
        }

        Rectangle2D ink = inkBoxOf(output);
        // Hinh phai bat dau dung tai le 5 mm, khong lech vao trong theo phan da cat.
        assertThat(mm(ink.getX())).as("canh trai cua hinh").isCloseTo(5, within(EPS));
        assertThat(mm(ink.getY())).as("canh duoi cua hinh").isCloseTo(5, within(EPS));
        assertThat(mm(ink.getWidth())).as("be ngang giu nguyen, khong bi co").isCloseTo(30, within(EPS));
        assertThat(mm(ink.getHeight())).as("chieu cao giu nguyen, khong bi co").isCloseTo(30, within(EPS));
    }

    @Test
    @DisplayName("Hinh khong co khoang trang thua thi khong bi cat gi ca")
    void tightArtworkIsLeftAlone() throws Exception {
        // Hinh phu kin ca trang: khong con gi de cat.
        byte[] pdf = buildPdf(100, 60, content -> {
            content.addRect(0, 0, pt(100), pt(60));
            content.fill();
        });

        JsonNode file = upload("kin-trang.pdf", pdf);

        assertThat(file.get("widthMm").asDouble()).isCloseTo(100, within(EPS));
        assertThat(file.get("heightMm").asDouble()).isCloseTo(60, within(EPS));
        assertThat(file.get("trimmed").asBoolean()).isFalse();
    }

    // ------------------------------------------------------------------
    // Tien ich
    // ------------------------------------------------------------------

    private interface Drawing {
        void draw(PDPageContentStream content) throws IOException;
    }

    private static float pt(double mm) {
        return Units.mmToPt(mm);
    }

    private static double mm(double points) {
        return Units.ptToMm(points);
    }

    /** Doc vung co net ve cua trang dau tien trong file PDF thanh pham. */
    private static Rectangle2D inkBoxOf(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            Rectangle2D box = new PdfContentBoxFinder(document.getPage(0)).find();
            assertThat(box).as("PDF thanh pham phai co net ve").isNotNull();
            return box;
        }
    }

    private JsonNode upload(String name, byte[] pdf) throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", name,
                MediaType.APPLICATION_PDF_VALUE, pdf);

        MvcResult result = mockMvc.perform(multipart("/api/v1/files").file(file))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get(0);
    }

    /** Ghep mot so ban roi tai ve PDF cua tam dau. Tat duong cat de khong lam nhieu phep do. */
    private byte[] nestAndDownload(String fileId, int quantity) throws Exception {
        String body = """
                {
                  "sheetWidthMm": 570,
                  "marginMm": 5,
                  "gapMm": 3,
                  "maxSheetLengthMm": null,
                  "allowRotateGlobal": false,
                  "drawCutLines": false,
                  "items": [ { "fileId": "%s", "quantity": %d, "allowRotate": false } ]
                }
                """.formatted(fileId, quantity);

        MvcResult created = mockMvc.perform(post("/api/v1/nesting/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andReturn();

        String jobId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("jobId").asText();

        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            MvcResult status = mockMvc.perform(get("/api/v1/nesting/jobs/{id}", jobId))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode job = objectMapper.readTree(status.getResponse().getContentAsString());
            String state = job.get("status").asText();
            if ("DONE".equals(state)) {
                break;
            }
            if ("FAILED".equals(state)) {
                throw new AssertionError("Job that bai: " + job.get("error"));
            }
            Thread.sleep(100);
        }

        return mockMvc.perform(get("/api/v1/nesting/jobs/{id}/sheets/0/pdf", jobId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
    }

    private static byte[] buildPdf(double widthMm, double heightMm, Drawing drawing) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(pt(widthMm), pt(heightMm)));
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                drawing.draw(content);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            document.save(bytes);
            return bytes.toByteArray();
        }
    }

    @Test
    @DisplayName("Anh xem truoc phai nho, khong tra ve nguyen anh goc")
    void previewIsThumbnailSized() throws Exception {
        // Anh 2000x1500 px; neu tra ve nguyen anh, PNG se nang hang MB.
        byte[] png = buildLargePng(2000, 1500);
        MockMultipartFile file = new MockMultipartFile("files", "anh-to.png",
                MediaType.IMAGE_PNG_VALUE, png);

        MvcResult uploaded = mockMvc.perform(multipart("/api/v1/files").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("IMAGE"))
                .andReturn();
        String id = objectMapper.readTree(uploaded.getResponse().getContentAsString())
                .get(0).get("id").asText();

        byte[] preview = mockMvc.perform(get("/api/v1/files/{id}/preview", id))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        try (var in = new java.io.ByteArrayInputStream(preview)) {
            var image = javax.imageio.ImageIO.read(in);
            // Nguong 360 px chu khong phai kich thuoc anh goc: o trong bang chi 44 px nhung
            // bam vao se mo to ra giua man hinh nen anh phai du net cho ca hai co.
            assertThat(Math.max(image.getWidth(), image.getHeight()))
                    .as("canh dai nhat cua anh xem truoc")
                    .isLessThanOrEqualTo(360);
        }
        assertThat(preview.length)
                .as("anh xem truoc phai duoi 200 KB")
                .isLessThan(200_000);
    }

    /**
     * Anh xem truoc cua PDF phai la anh DA CAT khoang trang.
     *
     * <p>Bai test dung mot trang A4 doc (210 x 297 mm) chi co mot o vuong 40 x 20 mm nam
     * lech han sang goc. Neu anh xem truoc ve ca to giay thi ty le canh se la 210/297,
     * tuc la CAO hon rong; con neu cat dung thi phai la 40/20, tuc rong gap doi cao.
     * Hai con so nay khac nhau mot troi mot vuc nen khong the lot luoi.
     */
    @Test
    @DisplayName("Anh xem truoc cua PDF la anh da cat khoang trang, khong phai ca to giay")
    void previewIsCroppedToContent() throws Exception {
        double boxWidthMm = 40;
        double boxHeightMm = 20;
        byte[] pdf = buildPdf(210, 297, content -> {
            content.addRect(pt(20), pt(240), pt(boxWidthMm), pt(boxHeightMm));
            content.fill();
        });
        MockMultipartFile file = new MockMultipartFile("files", "mot-o-nho.pdf",
                MediaType.APPLICATION_PDF_VALUE, pdf);

        MvcResult uploaded = mockMvc.perform(multipart("/api/v1/files").file(file))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode info = objectMapper.readTree(uploaded.getResponse().getContentAsString()).get(0);
        String id = info.get("id").asText();

        assertThat(info.get("widthMm").asDouble())
                .as("kich thuoc bao ra ngoai phai la kich thuoc DA CAT")
                .isCloseTo(boxWidthMm, within(EPS));

        byte[] preview = mockMvc.perform(get("/api/v1/files/{id}/preview", id))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        try (var in = new java.io.ByteArrayInputStream(preview)) {
            var image = javax.imageio.ImageIO.read(in);
            double previewRatio = image.getWidth() / (double) image.getHeight();
            assertThat(previewRatio)
                    .as("ty le canh cua anh xem truoc phai khop o da cat (%d x %d px)",
                            image.getWidth(), image.getHeight())
                    .isCloseTo(boxWidthMm / boxHeightMm, within(0.08));
        }
    }

    private static byte[] buildLargePng(int width, int height) throws IOException {
        java.awt.image.BufferedImage image =
                new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, ((x * 7) % 256) << 16 | ((y * 5) % 256) << 8 | 120);
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }
}
