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
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiem thu xuat TIF o che do CMYK.
 *
 * <p><b>Ve chuyen doi mau.</b> Chuyen RGB sang CMYK bang {@link java.awt.image.ColorConvertOp}
 * voi mot ho so ICC that, KHONG dung cong thuc tay kieu {@code K = 1 - max(R,G,B)}. Cong
 * thuc tay chay nhanh va nhin qua thi "ra CMYK", nhung no bo qua toan bo dac tinh muc va
 * giay ma ho so mo ta, nen mau in ra lech thay ro va lech khong deu - khong bu lai duoc
 * bang cach chinh tay.
 *
 * <p>Ho so dung de chuyen cung duoc NHUNG vao file, de RIP biet chinh xac nhung con so
 * CMYK trong do co nghia gi. Thieu no thi RIP phai doan, va doan sai la lech mau.
 *
 * <p>Dung dung ho so kem san trong ung dung, nen bai test chay o may nao cung duoc -
 * khong phu thuoc vao ho so cua he dieu hanh.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.storage.path=${java.io.tmpdir}/printnest-test-cmyk",
        "app.tiff.dpi=72",
        "app.tiff.color-mode=cmyk",
        // Tat kenh muc trang: bai nay giu duong xuat CMYK THUAN. Kenh trang la phan THEM
        // vao sau, va no khong duoc phep lam doi duong cu - de nguyen bai nay chay bang
        // 4 kenh chinh la cai chot giu dieu do.
        "app.tiff.white.enabled=false",
        "logging.level.vn.printnest=WARN"
})
class TiffCmykIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Che do CMYK: file ra 4 kenh, danh dau la CMYK")
    void tiffIsFourChannelCmyk() throws Exception {
        Map<Integer, String> tags = readTags(downloadTiff());

        // 262 = PhotometricInterpretation, 5 = Separated (CMYK).
        assertThat(tags.get(262)).as("PhotometricInterpretation phai la 5 = CMYK").isEqualTo("5");
        assertThat(tags.get(277)).as("SamplesPerPixel phai la 4 kenh").isEqualTo("4");
    }

    /**
     * Ho so dung de chuyen phai duoc nhung vao file.
     *
     * <p>Mot file CMYK khong kem ho so la "CMYK tran": RIP khong biet nhung con so do do
     * theo chuan nao nen phai doan. Doan sai thi mau lech ma khong cho nao bao loi.
     */
    @Test
    @DisplayName("Che do CMYK: ho so dung de chuyen duoc nhung vao file")
    void cmykProfileIsEmbedded() throws Exception {
        String rawTag = readTags(downloadTiff()).get(34675);
        assertThat(rawTag).as("file phai co tag 34675 = ICC Profile").isNotNull();

        String[] parts = rawTag.split(",");
        byte[] bytes = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            bytes[i] = (byte) Integer.parseInt(parts[i].trim());
        }

        ICC_Profile embedded = ICC_Profile.getInstance(bytes);
        assertThat(embedded.getColorSpaceType())
                .as("ho so nhung vao phai la CMYK chu khong phai sRGB cu")
                .isEqualTo(ColorSpace.TYPE_CMYK);
        // KHONG so tung byte voi ban goc. Java dong dau bo quan ly mau cua no vao ho so
        // khi dung den (byte 4 doi tu 0 sang 'l' cua "lcms"), va trong luong nay ho so
        // duoc dung de CHUYEN MAU truoc roi moi ghi vao file. Doi byte-khop-byte la doi
        // mot dieu khong dung, va bai test se do vi mot ly do khong lien quan gi toi
        // chat luong file.
        //
        // Cung KHONG doi bang dung kich thuoc file goc. Java ghi LAI ho so khi dung no de
        // chuyen mau - do that tren ho so SWOP kem san: 557.168 byte tren dia thanh
        // 702.712 byte sau mot lan chuyen. Doi bang nhau la doi mot dieu khong dung.
        //
        // Nhung dieu SAU day moi la dieu thuc su phai dung:
        assertThat(bytes.length)
                .as("khong bi cat cut: it nhat phai bang ho so kem san")
                .isGreaterThanOrEqualTo(bundledProfile().length);

        assertThat(describe(embedded))
                .as("phai dung ho so kem san chu khong phai mot ho so khac")
                .contains("SWOP");

        // Va quan trong nhat: ho so nhung vao con DUNG DUOC de chuyen mau.
        float[] cmyk = new java.awt.color.ICC_ColorSpace(embedded)
                .fromRGB(new float[]{0.85f, 0.12f, 0.16f});
        assertThat(cmyk).as("ho so phai con chuyen duoc mau").hasSize(4);
        assertThat(cmyk[1]).as("mau do tach ra phai nhieu muc M").isGreaterThan(0.5f);
    }

    // ------------------------------------------------------------------

    private byte[] downloadTiff() throws Exception {
        String jobId = runJob();
        return mockMvc.perform(get("/api/v1/nesting/jobs/{id}/sheets/0/tif", jobId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

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

    /** Ten mo ta ghi trong ho so, dung de biet co dung ho so mong doi khong. */
    private static String describe(ICC_Profile profile) {
        byte[] tag = profile.getData(ICC_Profile.icSigProfileDescriptionTag);
        if (tag == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (byte b : tag) {
            if (b >= 32 && b < 127) {
                text.append((char) b);
            }
        }
        return text.toString();
    }

    /** Ho so CMYK kem san trong ung dung. */
    private static byte[] bundledProfile() throws IOException {
        try (var in = TiffCmykIntegrationTest.class.getClassLoader()
                .getResourceAsStream("color/USWebCoatedSWOP.icc")) {
            assertThat(in).as("ho so CMYK phai co trong resources").isNotNull();
            return ICC_Profile.getInstance(in).getData();
        }
    }

    private static byte[] buildPdf() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(283.46f, 141.73f));
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.setNonStrokingColor(0.85f, 0.12f, 0.16f);
                content.addRect(20, 20, 200, 100);
                content.fill();
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            document.save(bytes);
            return bytes.toByteArray();
        }
    }
}
