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

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiem thu xuat ban TIF.
 *
 * <p>TIF la anh bitmap, nen no la NGOAI LE cua bat bien "khong raster hoa PDF". Ban PDF
 * van la ban chinh; TIF chi la ban them cho RIP chi nhan anh.
 *
 * <p>Dieu phai chung minh khong phai "co tra ve byte nao khong" ma la <b>file do mo ra co
 * dung kich thuoc that khong</b>. Mot file TIF thieu thong tin do phan giai se bi phan mem
 * doan la 72 DPI - tam 57 cm in ra thanh 2,4 met. Do la kieu loi chi phat hien khi giay
 * da chay tren may.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.storage.path=${java.io.tmpdir}/printnest-test-tiff",
        "app.tiff.dpi=72",
        // Bai nay kiem duong RGB. Mac dinh cua he thong la CMYK nen phai noi ro.
        "app.tiff.color-mode=rgb",
        "logging.level.vn.printnest=WARN"
})
class TiffExportIntegrationTest {

    /** Phai khop voi app.tiff.dpi o tren. */
    private static final int DPI = 72;

    private static final double MM_PER_INCH = 25.4;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Tai TIF mot tam: mo ra dung so diem anh ung voi kich thuoc that")
    void singleSheetTiffHasCorrectPixelSize() throws Exception {
        String jobId = runJob();
        double[] size = sheetSizeMm(jobId, 0);

        byte[] tiff = mockMvc.perform(get("/api/v1/nesting/jobs/{id}/sheets/0/tif", jobId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(tiff));
        assertThat(image).as("file tra ve phai doc duoc nhu mot anh").isNotNull();

        long expectedWidth = Math.round(size[0] / MM_PER_INCH * DPI);
        long expectedHeight = Math.round(size[1] / MM_PER_INCH * DPI);

        // Sai lech 1 diem la do lam tron, chap nhan duoc.
        assertThat((long) image.getWidth())
                .as("chieu rong tinh tu %.1f mm o %d DPI", size[0], DPI)
                .isBetween(expectedWidth - 1, expectedWidth + 1);
        assertThat((long) image.getHeight())
                .as("chieu cao tinh tu %.1f mm o %d DPI", size[1], DPI)
                .isBetween(expectedHeight - 1, expectedHeight + 1);
    }

    /**
     * Doc NGUOC cac tag trong file de chac do phan giai that su duoc ghi vao.
     *
     * <p>Bai test kiem so diem anh o tren KHONG bat duoc loi nay: anh van dung so diem,
     * chi la file khong noi cho phan mem biet mot diem ung voi bao nhieu milimet. Phan mem
     * se doan 72 DPI va tam 57 cm in ra thanh 2,4 met - chi phat hien khi giay da chay.
     *
     * <p>Da tung xay ra that: ham ghi metadata nem NullPointerException roi bi bat va ghi
     * canh bao, file van phat hanh voi XResolution = 1 va ResolutionUnit = None.
     */
    @Test
    @DisplayName("File TIF co ghi do phan giai that, khong de phan mem doan")
    void tiffCarriesResolutionTags() throws Exception {
        String jobId = runJob();
        byte[] tiff = mockMvc.perform(get("/api/v1/nesting/jobs/{id}/sheets/0/tif", jobId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        Map<Integer, String> tags = readTags(tiff);

        assertThat(tags).containsKey(296);
        assertThat(tags.get(296)).as("ResolutionUnit phai la 2 = inch").isEqualTo("2");
        assertThat(tags.get(282)).as("XResolution phai bang DPI dang dat").isEqualTo(DPI + "/1");
        assertThat(tags.get(283)).as("YResolution phai bang DPI dang dat").isEqualTo(DPI + "/1");
    }

    /**
     * Ho so mau phai nhung duoc va doc lai duoc nhu mot ho so ICC hop le.
     *
     * <p>Khong co no thi file chi noi "day la RGB" ma khong noi RGB NAO, RIP phai tu doan.
     * Doan sai thi mau in ra lech ma khong co cho nao bao loi.
     *
     * <p>Kiem bang cach dung lai ho so tu byte trong file roi nho Java phan tich - neu
     * byte bi ghi lech mot don vi hay bi cat cut thi buoc nay nem loi ngay.
     */
    @Test
    @DisplayName("File TIF co nhung ho so mau sRGB doc lai duoc")
    void tiffCarriesIccProfile() throws Exception {
        String jobId = runJob();
        byte[] tiff = mockMvc.perform(get("/api/v1/nesting/jobs/{id}/sheets/0/tif", jobId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        String raw = readTags(tiff).get(34675);
        assertThat(raw).as("file phai co tag 34675 = ICC Profile").isNotNull();

        String[] parts = raw.split(",");
        byte[] profileBytes = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            profileBytes[i] = (byte) Integer.parseInt(parts[i].trim());
        }

        java.awt.color.ICC_Profile profile = java.awt.color.ICC_Profile.getInstance(profileBytes);
        assertThat(profile.getColorSpaceType())
                .as("ho so nhung vao phai la khong gian mau RGB")
                .isEqualTo(java.awt.color.ColorSpace.TYPE_RGB);
        assertThat(profile.getData())
                .as("so byte phai khop ho so sRGB cua he thong")
                .hasSameSizeAs(java.awt.color.ICC_Profile
                        .getInstance(java.awt.color.ColorSpace.CS_sRGB).getData());
    }

    /**
     * Anh khong duoc chia thanh moi hang mot dai.
     *
     * <p>Mac dinh cua Java la RowsPerStrip = 1: mot anh cao 5.000 hang thanh 5.000 dai,
     * vua phinh bang tag vua lam LZW khoi dong lai bo tu dien moi hang nen nen kem han.
     */
    @Test
    @DisplayName("Anh chia dai theo khoi chu khong phai moi hang mot dai")
    void tiffUsesSensibleStrips() throws Exception {
        String jobId = runJob();
        byte[] tiff = mockMvc.perform(get("/api/v1/nesting/jobs/{id}/sheets/0/tif", jobId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(readTags(tiff).get(278))
                .as("RowsPerStrip")
                .isNotEqualTo("1");
    }

    @Test
    @DisplayName("Tai TIF tra ve dung kieu MIME va ten file duoi .tif")
    void tiffResponseIsLabelledCorrectly() throws Exception {
        String jobId = runJob();

        MvcResult result = mockMvc.perform(get("/api/v1/nesting/jobs/{id}/sheets/0/tif", jobId))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentType()).isEqualTo("image/tiff");
        assertThat(result.getResponse().getHeader("Content-Disposition"))
                .as("ten file phai co duoi .tif")
                .contains(".tif");
    }

    @Test
    @DisplayName("Tai tat ca dang TIF: moi tam mot file .tif trong zip")
    void zipWithTiffFormat() throws Exception {
        String jobId = runJob();

        byte[] zip = mockMvc.perform(
                        get("/api/v1/nesting/jobs/{id}/export.zip", jobId).param("format", "tif"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        List<String> names = entryNames(zip);
        assertThat(names).isNotEmpty();
        assertThat(names).allMatch(name -> name.endsWith(".tif"));
    }

    @Test
    @DisplayName("Khong truyen dinh dang thi van ra PDF nhu cu")
    void zipDefaultsToPdf() throws Exception {
        String jobId = runJob();

        byte[] zip = mockMvc.perform(get("/api/v1/nesting/jobs/{id}/export.zip", jobId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(entryNames(zip)).isNotEmpty().allMatch(name -> name.endsWith(".pdf"));
    }

    @Test
    @DisplayName("Dinh dang la khong bao loi ngay chu khong lang le tra ve PDF")
    void unknownFormatIsRejected() throws Exception {
        String jobId = runJob();

        mockMvc.perform(get("/api/v1/nesting/jobs/{id}/export.zip", jobId).param("format", "png"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
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
                  "maxSheetLengthMm": 1000,
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
            if ("FAILED".equals(status)) {
                throw new IllegalStateException("Job chay hong");
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("Job khong chay xong");
    }

    private double[] sheetSizeMm(String jobId, int index) throws Exception {
        var sheet = objectMapper.readTree(
                        mockMvc.perform(get("/api/v1/nesting/jobs/{id}", jobId))
                                .andReturn().getResponse().getContentAsString())
                .get("result").get("sheets").get(index);
        return new double[]{sheet.get("widthMm").asDouble(), sheet.get("lengthMm").asDouble()};
    }

    /** Doc bang tag cua trang dau tien: so tag -> gia tri dau tien duoi dang chuoi. */
    private static Map<Integer, String> readTags(byte[] tiff) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(tiff))) {
            ImageReader reader = ImageIO.getImageReaders(in).next();
            reader.setInput(in, false, false);
            IIOMetadata metadata = reader.getImageMetadata(0);
            Node root = metadata.getAsTree(metadata.getNativeMetadataFormatName());

            Map<Integer, String> tags = new LinkedHashMap<>();
            for (Node field = root.getFirstChild().getFirstChild();
                 field != null; field = field.getNextSibling()) {
                NamedNodeMap attrs = field.getAttributes();
                if (attrs == null || attrs.getNamedItem("number") == null) {
                    continue;
                }
                // Phan lon kieu du lieu boc gia tri trong mot nut con (TIFFShorts >
                // TIFFShort). Rieng TIFFUndefined dat gia tri ngay tren chinh no, nen
                // phai thu ca hai cho.
                Node values = field.getFirstChild();
                if (values == null) {
                    continue;
                }
                Node value = values.getAttributes() == null
                        ? null : values.getAttributes().getNamedItem("value");
                if (value == null) {
                    Node first = values.getFirstChild();
                    value = first == null ? null : first.getAttributes().getNamedItem("value");
                }
                if (value != null) {
                    tags.put(Integer.parseInt(attrs.getNamedItem("number").getNodeValue()),
                            value.getNodeValue());
                }
            }
            reader.dispose();
            return tags;
        }
    }

    private static List<String> entryNames(byte[] zip) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
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
