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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiem thu kenh muc trang (spot channel W1) trong ban TIF.
 *
 * <p>May in DTF phun mot lop muc TRANG LOT xuong truoc roi moi phun mau len tren. Khong co
 * lop lot thi mau in len vai toi se chim het.
 *
 * <p><b>Bai nay doc thang DIEM ANH chu khong chi doc bang tag.</b> Ly do: mot file sai
 * chieu gia tri van du nam kenh, van dung ten kenh, van mo ra binh thuong trong moi phan
 * mem - no chi lo ra khi muc da len ao. Bang tag khong bat duoc loi do, chi doc so lieu
 * diem anh moi bat duoc.
 *
 * <p>Moi con so mong doi o day doi chieu voi file mau {@code samples/IN6 2209.tif} - ban
 * do tho tu dung trong Photoshop.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.storage.path=${java.io.tmpdir}/printnest-test-white",
        "app.tiff.dpi=72",
        "app.tiff.color-mode=cmyk",
        "app.tiff.white.enabled=true",
        "app.tiff.white.channel-name=W1",
        "app.tiff.white.choke-pixels=1",
        "logging.level.vn.printnest=WARN"
})
class TiffWhiteChannelIntegrationTest {

    /** So hieu tag TIFF dung trong bai. */
    private static final int SAMPLES_PER_PIXEL = 277;
    private static final int PHOTOMETRIC = 262;
    private static final int EXTRA_SAMPLES = 338;
    private static final int PHOTOSHOP = 34377;
    private static final int BITS_PER_SAMPLE = 258;
    private static final int IMAGE_SOURCE_DATA = 37724;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("File ra 5 kenh: CMYK cong mot kenh muc trang")
    void tiffHasFiveChannels() throws Exception {
        Tiff tiff = Tiff.parse(downloadTiff());

        assertThat(tiff.shortValue(SAMPLES_PER_PIXEL))
                .as("SamplesPerPixel phai la 5").isEqualTo(5);
        assertThat(tiff.shortValue(PHOTOMETRIC))
                .as("van phai la 5 = CMYK, kenh trang khong duoc lam doi cho nay").isEqualTo(5);
        assertThat(tiff.shorts(BITS_PER_SAMPLE))
                .as("ca nam kenh deu 8 bit").containsExactly(8, 8, 8, 8, 8);
    }

    /**
     * Tag 338 phai la 0, dung nhu file mau.
     *
     * <p>Bo ghi cua Java tu dat 2 (kenh alpha roi) vi anh nam kenh trong bo nho buoc phai
     * khai bao kenh thu nam la alpha. De nguyen 2 thi phan mem doc file co the hieu kenh
     * muc trang la do TRONG SUOT va nhan chim lop mau theo no.
     */
    @Test
    @DisplayName("ExtraSamples = 0, khong phai kenh trong suot")
    void extraSamplesIsUnspecified() throws Exception {
        assertThat(Tiff.parse(downloadTiff()).shortValue(EXTRA_SAMPLES))
                .as("phai la 0 nhu file mau, khong phai 2 = alpha").isEqualTo(0);
    }

    /**
     * Khong co khoi 34377 thi mo file ra chi thay "Alpha 1".
     *
     * <p>Chuan TIFF khong co cho nao ghi TEN kenh, cung khong phan biet duoc kenh phu la
     * lop trong suot hay mot mau muc rieng. Khoi nay moi noi duoc dieu do.
     */
    @Test
    @DisplayName("Co khoi Photoshop khai kenh ten W1 la mau muc rieng")
    void photoshopBlockDeclaresTheSpotChannel() throws Exception {
        byte[] block = Tiff.parse(downloadTiff()).bytes(PHOTOSHOP);
        assertThat(block).as("phai co tag 34377").isNotNull();

        StringBuilder hex = new StringBuilder();
        for (byte b : block) {
            hex.append(String.format("%02x ", b));
        }
        String all = hex.toString();

        assertThat(all).as("khoi 1006: ten kenh dang Pascal \"W1\"").contains("02 57 31");
        assertThat(all).as("khoi 1045: ten kenh dang Unicode \"W1\"")
                .contains("00 00 00 03 00 57 00 31 00 00");
        assertThat(all).as("khoi 1077: kind = 2, tuc la mau muc rieng")
                .contains("00 00 00 01 00 00 ff ff 00 00 00 00 00 00 00 64 02");
    }

    /**
     * Cho quan trong nhat ca bai: chieu gia tri cua kenh W1.
     *
     * <p>Trong file mau, cho co hinh thi W1 = 0 con cho trong thi W1 = 255 - tuc la 0 moi
     * la CO muc trang. Dat nguoc lai thi lop trang ra am ban: may phun trang day vao
     * khoang trong quanh hinh va bo trang chinh cho co hinh.
     *
     * <p><b>Luu y ve pham vi:</b> bai nay dung mot hinh MOT MAU DAC, nen o day "co lot
     * trang" va "co muc mau" trung nhau. Voi thiet ke that co chi tiet TRANG ben trong -
     * chu trang, vien trang - thi nhung diem do khong co muc CMYK nao nhung VAN phai duoc
     * lot trang, vi tren ao mau toi thi chinh lop trang lam chu hien ra. Do la hanh vi
     * DUNG, dung "sua" no de thoa man con so o day. Truong hop do duoc phu boi
     * {@link #jpegWithoutAlphaStillGetsAUsableMask()}.
     */
    @Test
    @DisplayName("Chi lot trang o cho co hinh, va 0 moi la co muc")
    void whiteInkOnlyGoesWhereTheArtworkIs() throws Exception {
        Tiff tiff = Tiff.parse(downloadTiff());
        byte[] pixels = tiff.pixels();
        int bands = tiff.shortValue(SAMPLES_PER_PIXEL);

        int inked = 0;
        int whiteInk = 0;
        int whiteOnEmptyPaper = 0;

        for (int p = 0; p + bands <= pixels.length; p += bands) {
            int colour = (pixels[p] & 0xFF) + (pixels[p + 1] & 0xFF)
                    + (pixels[p + 2] & 0xFF) + (pixels[p + 3] & 0xFF);
            boolean hasWhite = (pixels[p + 4] & 0xFF) < 128;

            if (colour > 0) {
                inked++;
            }
            if (hasWhite) {
                whiteInk++;
                if (colour == 0) {
                    whiteOnEmptyPaper++;
                }
            }
        }

        String counts = "tong %d diem, co mau %d, co trang %d, trang-tren-cho-trong %d"
                .formatted(pixels.length / bands, inked, whiteInk, whiteOnEmptyPaper);

        assertThat(inked).as("tam phai co hinh, khong thi bai test khong noi len dieu gi (%s)", counts)
                .isGreaterThan(0);
        assertThat(whiteInk).as("phai co cho duoc lot trang (%s)", counts).isGreaterThan(0);
        assertThat(whiteOnEmptyPaper)
                .as("hinh mot mau dac thi khong cho nao duoc lot trang ma thieu mau -"
                        + " sai chieu gia tri la ra day (%s)", counts)
                .isEqualTo(0);
        assertThat(whiteInk)
                .as("lop trang phai NHO HON lop mau vi da co vao trong (%s)", counts)
                .isLessThan(inked);
    }

    /**
     * Goc tam la phim trong: khong mau, khong trang.
     *
     * <p>Kiem rieng mot diem cu the de neu bai tren hong thi biet ngay la hong theo chieu
     * nao.
     */
    @Test
    @DisplayName("Goc tam khong co muc gi ca")
    void theCornerOfTheSheetIsBare() throws Exception {
        Tiff tiff = Tiff.parse(downloadTiff());
        byte[] pixels = tiff.pixels();

        assertThat(pixels[0] & 0xFF).as("C").isEqualTo(0);
        assertThat(pixels[1] & 0xFF).as("M").isEqualTo(0);
        assertThat(pixels[2] & 0xFF).as("Y").isEqualTo(0);
        assertThat(pixels[3] & 0xFF).as("K").isEqualTo(0);
        assertThat(pixels[4] & 0xFF).as("W1: 255 = khong lot trang").isEqualTo(255);
    }

    /**
     * Lop mang theo do trong suot, de mo ra thay o caro chu khong phai nen trang.
     *
     * <p>Anh gop cua file TIFF khong co khai niem trong suot: cho khong co muc thi CMYK
     * bang 0 0 0 0, ma Photoshop ve CMYK rong thanh mau TRANG. Nhin vao khong phan biet
     * duoc "nen trang" voi "khong co vung in". Tho can nhin thay o caro de biet chac may
     * chi in phan chi tiet.
     *
     * <p>Moi con so mong doi doi chieu voi file mau {@code samples/IN6 2209.tif}: mot lop
     * phu kin tam, 5 kenh -1/0/1/2/3, che do hoa "norm", nen kieu RLE.
     */
    @Test
    @DisplayName("Co lop trong suot phu kin tam, dung cau truc file mau")
    void carriesATransparentLayer() throws Exception {
        Tiff tiff = Tiff.parse(downloadTiff());
        byte[] block = tiff.bytes(IMAGE_SOURCE_DATA);
        assertThat(block).as("phai co tag 37724").isNotNull();

        ByteBuffer p = ByteBuffer.wrap(block).order(ByteOrder.BIG_ENDIAN);
        StringBuilder signature = new StringBuilder();
        for (byte b = p.get(); b != 0; b = p.get()) {
            signature.append((char) b);
        }
        assertThat(signature.toString()).isEqualTo("Adobe Photoshop Document Data Block");

        byte[] four = new byte[4];
        p.get(four);
        assertThat(new String(four)).as("chu ky khoi").isEqualTo("8BIM");
        p.get(four);
        assertThat(new String(four)).as("khoa khoi").isEqualTo("Layr");

        int declared = p.getInt();
        assertThat(declared).as("do dai khai bao phai khop phan con lai cua khoi")
                .isEqualTo(block.length - p.position());

        assertThat(p.getShort()).as("dung mot lop").isEqualTo((short) 1);

        int top = p.getInt();
        int left = p.getInt();
        int bottom = p.getInt();
        int right = p.getInt();
        assertThat(new int[]{top, left})
                .as("lop phai bat dau tu goc tam").containsExactly(0, 0);
        assertThat(new int[]{right, bottom})
                .as("lop phai phu kin tam")
                .containsExactly(tiff.shortValue(256), tiff.shortValue(257));

        int channels = p.getShort() & 0xFFFF;
        assertThat(channels).as("mot kenh trong suot cong bon kenh mau").isEqualTo(5);

        int[] ids = new int[channels];
        int[] lengths = new int[channels];
        for (int c = 0; c < channels; c++) {
            ids[c] = p.getShort();
            lengths[c] = p.getInt();
        }
        assertThat(ids).as("kenh -1 la do trong suot, roi den C M Y K")
                .containsExactly(-1, 0, 1, 2, 3);
        for (int c = 0; c < channels; c++) {
            assertThat(lengths[c]).as("kenh %d khong duoc rong", ids[c]).isGreaterThan(2);
        }

        p.get(four);
        assertThat(new String(four)).as("chu ky che do hoa").isEqualTo("8BIM");
        p.get(four);
        assertThat(new String(four)).as("che do hoa binh thuong").isEqualTo("norm");
        assertThat(p.get() & 0xFF).as("do dac phai day").isEqualTo(255);

        // Bo qua clipping, co, byte don, roi den phan phu.
        p.get();
        p.get();
        p.get();
        int extra = p.getInt();
        int extraStart = p.position();
        assertThat(p.getInt()).as("khong co mat na lop").isEqualTo(0);
        assertThat(p.getInt()).as("khong gioi han dai hoa").isEqualTo(0);
        int nameLength = p.get() & 0xFF;
        byte[] name = new byte[nameLength];
        p.get(name);
        assertThat(new String(name)).as("ten lop").isEqualTo("Layer 1");

        // File mau dung RLE (kieu 1) vi do la mac dinh cua Photoshop. Mac dinh cua he
        // thong nay la ZIP (kieu 2) vi no nho hon 46% ma lai nen nhanh hon - doi kieu nen
        // KHONG doi cau truc lop, nhung van chot lai o day de khong ai doi sang mot kieu
        // Photoshop khong doc duoc.
        p.position(extraStart + extra);
        assertThat(p.getShort()).as("nen kieu 2 = ZIP, hoac 1 = RLE nhu file mau")
                .isIn((short) 1, (short) 2);
    }

    /**
     * Do trong suot phai khop vung co hinh, va KHONG bi co vao.
     *
     * <p>Chi rieng lop muc trang moi phai thut vao de khoi lo vien; lop mau trai het ra
     * den mep hinh. Lay nham mat na da co cho ca hai thi anh bi hut mat mot vien mong
     * quanh moi hinh - nhin man hinh khong ra, in moi thay.
     */
    @Test
    @DisplayName("Vung trong suot rong hon vung lot trang dung bang phan da co")
    void transparencyIsWiderThanTheWhiteUnderbase() throws Exception {
        Tiff tiff = Tiff.parse(downloadTiff());
        int width = tiff.shortValue(256);
        int height = tiff.shortValue(257);

        byte[] alpha = tiff.layerChannel(0, width, height);
        byte[] pixels = tiff.pixels();
        int bands = tiff.shortValue(SAMPLES_PER_PIXEL);

        int opaque = 0;
        int whiteInk = 0;
        int whiteOutsideArtwork = 0;
        for (int i = 0; i < width * height; i++) {
            boolean solid = (alpha[i] & 0xFF) > 127;
            boolean white = (pixels[i * bands + 4] & 0xFF) < 128;
            if (solid) {
                opaque++;
            }
            if (white) {
                whiteInk++;
                if (!solid) {
                    whiteOutsideArtwork++;
                }
            }
        }

        assertThat(opaque).as("phai co vung dac").isGreaterThan(0);
        assertThat(whiteOutsideArtwork)
                .as("khong duoc lot trang ra ngoai vung co hinh").isEqualTo(0);
        assertThat(opaque)
                .as("vung dac phai rong hon vung lot trang, vi lop trang bi co vao")
                .isGreaterThan(whiteInk);
    }

    /**
     * Kenh CMYK cua LOP phai nam NGUOC voi anh gop.
     *
     * <p>PSD luu CMYK lat lai: 0 la muc DAY, 255 la khong muc - con anh gop TIFF thi 0 la
     * khong muc. Do tren file mau {@code samples/IN6 2209.tif}, kenh Cyan: cho anh gop
     * ghi 21 thi lop ghi 234, cho anh gop ghi 58 thi lop ghi 197. Luc nao cung tron 255.
     *
     * <p>Ghi thang gia tri anh gop vao lop thi mot hinh mau KEM (rat it muc) bi doc thanh
     * DEN DAC. Anh gop van dung nen so lieu kiem anh gop khong bat duoc - chi mo file ra
     * nhin moi thay, va rat de tuong la loi chuyen mau.
     */
    @Test
    @DisplayName("Kenh mau cua lop lat nguoc so voi anh gop, dung nhu file mau")
    void layerColourChannelsAreStoredInverted() throws Exception {
        Tiff tiff = Tiff.parse(downloadTiff());
        int width = tiff.shortValue(256);
        int height = tiff.shortValue(257);
        byte[] pixels = tiff.pixels();
        int bands = tiff.shortValue(SAMPLES_PER_PIXEL);

        // Kenh 0 la do trong suot, nen bon kenh mau la 1..4.
        for (int band = 0; band < 4; band++) {
            byte[] layer = tiff.layerChannel(band + 1, width, height);

            int checked = 0;
            for (int i = 0; i < width * height && checked < 500; i += 977) {
                int flat = pixels[i * bands + band] & 0xFF;
                int inLayer = layer[i] & 0xFF;
                assertThat(flat + inLayer)
                        .as("kenh mau %d tai diem %d: anh gop %d + lop %d phai bang 255",
                                band, i, flat, inLayer)
                        .isEqualTo(255);
                checked++;
            }
            assertThat(checked).as("phai kiem duoc it nhat vai tram diem").isGreaterThan(100);
        }
    }

    /**
     * Anh JPG nen trang dac - truong hop KHONG co kenh trong suot.
     *
     * <p>Day la cho toi tung dinh tu choi: khong co alpha thi khong biet dau la hinh dau
     * la nen. Nhung co cach, va no chinh la cai tho van lam: bam magic wand vao nen. Nen
     * duoc loang tu MEP tam vao qua cac diem gan trang; cho nao loang toi thi la nen.
     *
     * <p>Cai duoc nhat cua cach nay la o vuong TRANG nam giua hinh: no bi mau bao quanh
     * nen loang khong toi, va van duoc lot trang. Neu chi lay quy tac "diem trang la nen"
     * thi chi tiet do mat lop lot, in len ao mau toi la bien mat.
     */
    @Test
    @DisplayName("Anh JPG nen trang: nen bi loai, chi tiet trang giua hinh van duoc lot")
    void jpegWithoutAlphaStillGetsAUsableMask() throws Exception {
        Tiff tiff = Tiff.parse(downloadTiff(buildJpeg(), "anh.jpg", MediaType.IMAGE_JPEG_VALUE));
        byte[] pixels = tiff.pixels();
        int bands = tiff.shortValue(SAMPLES_PER_PIXEL);
        int width = tiff.shortValue(256);
        int height = pixels.length / bands / width;

        int whiteInk = 0;
        for (int p = 0; p + bands <= pixels.length; p += bands) {
            if ((pixels[p + 4] & 0xFF) < 128) {
                whiteInk++;
            }
        }

        assertThat(whiteInk).as("phai co cho duoc lot trang").isGreaterThan(0);
        assertThat(whiteInk)
                .as("nen trang phai bi loai, khong duoc lot ca tam")
                .isLessThan(width * height / 2);

        // Goc tam: nen, khong duoc lot.
        assertThat(pixels[4] & 0xFF).as("goc tam khong duoc lot trang").isEqualTo(255);

        // Tam hinh: o vuong trang nam giua, phai duoc lot trang.
        int centre = ((height / 2) * width + findArtworkColumn(pixels, bands, width, height)) * bands;
        assertThat(pixels[centre + 4] & 0xFF)
                .as("chi tiet trang nam giua hinh phai duoc lot trang")
                .isEqualTo(0);
    }

    /** Tim mot cot di qua giua mot hinh, de biet cho ma soi. */
    private static int findArtworkColumn(byte[] pixels, int bands, int width, int height) {
        int row = height / 2;
        for (int x = 0; x < width; x++) {
            int p = (row * width + x) * bands;
            if ((pixels[p + 4] & 0xFF) < 128) {
                // Di tiep den giua vung duoc lot trang.
                int end = x;
                while (end < width && (pixels[(row * width + end) * bands + 4] & 0xFF) < 128) {
                    end++;
                }
                return (x + end) / 2;
            }
        }
        throw new IllegalStateException("Khong tim thay vung duoc lot trang nao");
    }

    /**
     * Anh JPG nen trang, co mot o vuong TRANG nam giua hinh mau.
     *
     * <p>Dung JPG that chu khong PNG: JPG bi nen mat du lieu nen nen trang cua no ra
     * 250-255 chu khong phang tuyet doi - chinh la ly do phai co dung sai khi do nen.
     */
    private static byte[] buildJpeg() throws IOException {
        java.awt.image.BufferedImage image =
                new java.awt.image.BufferedImage(400, 300, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, 400, 300);
        g.setColor(new java.awt.Color(20, 80, 200));
        g.fillRect(60, 50, 280, 200);
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(160, 120, 80, 60);
        g.dispose();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "jpg", bytes);
        return bytes.toByteArray();
    }

    /**
     * Ve hai kenh ra anh PNG de nhin bang mat.
     *
     * <p>So lieu noi duoc "bao nhieu diem sai" nhung khong noi duoc "sai o dau". Hai anh
     * nay de mo ra doi chieu: vung trang trong anh W1 phai khop hinh va nho hon mep dung
     * bang so diem da co vao.
     *
     * <p>Ghi vao {@code target/} nen {@code mvn clean} la don sach, khong lan vao kho.
     */
    @Test
    @DisplayName("Ve kenh mau va kenh W1 ra PNG de doi chieu bang mat")
    void writesPngsForEyeballing() throws Exception {
        byte[] raw = downloadTiff();
        Tiff tiff = Tiff.parse(raw);
        byte[] pixels = tiff.pixels();
        int bands = tiff.shortValue(SAMPLES_PER_PIXEL);
        int width = tiff.shortValue(256);
        int height = pixels.length / bands / width;

        java.awt.image.BufferedImage white =
                new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_BYTE_GRAY);
        java.awt.image.BufferedImage colour =
                new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int p = (y * width + x) * bands;
                // Ve lai theo chieu de nhin: TRANG = co lot muc trang.
                white.getRaster().setSample(x, y, 0, 255 - (pixels[p + 4] & 0xFF));

                int k = pixels[p + 3] & 0xFF;
                colour.setRGB(x, y,
                        (channel(pixels[p] & 0xFF, k) << 16)
                                | (channel(pixels[p + 1] & 0xFF, k) << 8)
                                | channel(pixels[p + 2] & 0xFF, k));
            }
        }

        java.nio.file.Path folder = java.nio.file.Path.of("target", "w1-check");
        java.nio.file.Files.createDirectories(folder);
        java.nio.file.Files.write(folder.resolve("tam.tif"), raw);
        javax.imageio.ImageIO.write(white, "png", folder.resolve("kenh-w1.png").toFile());
        javax.imageio.ImageIO.write(colour, "png", folder.resolve("kenh-mau.png").toFile());
        System.out.println("Da ghi anh doi chieu vao " + folder.toAbsolutePath());
    }

    /** Doi mot kenh CMYK ve RGB de NHIN - uoc luong tho, khong dung de in. */
    private static int channel(int ink, int black) {
        return Math.max(0, 255 - ink - black);
    }

    // ------------------------------------------------------------------

    private byte[] downloadTiff() throws Exception {
        return downloadTiff(buildPdf(), "nhan.pdf", MediaType.APPLICATION_PDF_VALUE);
    }

    private byte[] downloadTiff(byte[] content, String name, String type) throws Exception {
        String jobId = runJob(content, name, type);
        return mockMvc.perform(get("/api/v1/nesting/jobs/{id}/sheets/0/tif", jobId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    private String runJob(byte[] content, String name, String type) throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", name, type, content);
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

    /**
     * Bo doc TIFF toi gian, doc thang tu byte.
     *
     * <p><b>Vi sao khong dung ImageIO.</b> {@code TIFFImageReader} cua Java nem
     * {@code UnsupportedOperationException} khi doc raster cua anh nam kenh, con
     * {@code read()} thi khong dung noi mo hinh mau. Ma diem anh lai dung la thu PHAI doc
     * trong bai nay. Doc tay khoang ba chuc dong, va no con co cai hay la khong di qua
     * chinh thu vien dang duoc kiem.
     */
    private record Tiff(Map<Integer, Field> fields, byte[] raw, ByteOrder order) {

        private record Field(int type, int count, long offset, boolean inline) {
        }

        static Tiff parse(byte[] raw) {
            ByteOrder order = raw[0] == 'I' ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
            ByteBuffer buffer = ByteBuffer.wrap(raw).order(order);
            int ifd = buffer.getInt(4);
            int count = buffer.getShort(ifd) & 0xFFFF;

            Map<Integer, Field> fields = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                int entry = ifd + 2 + i * 12;
                int tag = buffer.getShort(entry) & 0xFFFF;
                int type = buffer.getShort(entry + 2) & 0xFFFF;
                int items = buffer.getInt(entry + 4);
                int unit = switch (type) {
                    case 3 -> 2;
                    case 4, 9, 11 -> 4;
                    case 5, 10, 12 -> 8;
                    default -> 1;
                };
                boolean inline = unit * items <= 4;
                fields.put(tag, new Field(type, items,
                        inline ? entry + 8 : buffer.getInt(entry + 8) & 0xFFFFFFFFL, inline));
            }
            return new Tiff(fields, raw, order);
        }

        int shortValue(int tag) {
            Integer[] values = shorts(tag);
            return values == null ? -1 : values[0];
        }

        Integer[] shorts(int tag) {
            Field field = fields.get(tag);
            if (field == null) {
                return null;
            }
            ByteBuffer buffer = ByteBuffer.wrap(raw).order(order);
            Integer[] values = new Integer[field.count()];
            for (int i = 0; i < field.count(); i++) {
                values[i] = buffer.getShort((int) (field.offset() + i * 2L)) & 0xFFFF;
            }
            return values;
        }

        byte[] bytes(int tag) {
            Field field = fields.get(tag);
            if (field == null) {
                return null;
            }
            byte[] data = new byte[field.count()];
            System.arraycopy(raw, (int) field.offset(), data, 0, field.count());
            return data;
        }

        /**
         * Giai nen mot kenh cua lop Photoshop trong tag 37724.
         *
         * <p>Cach xep khac han anh gop: moi kenh la mot mang RIENG, nen bang PackBits, va
         * truoc du lieu la bang do dai tung hang.
         */
        byte[] layerChannel(int index, int width, int height) {
            byte[] block = bytes(IMAGE_SOURCE_DATA);
            ByteBuffer p = ByteBuffer.wrap(block).order(ByteOrder.BIG_ENDIAN);
            while (p.get() != 0) {
                // bo qua chuoi chu ky
            }
            p.position(p.position() + 12);
            p.getShort();
            p.position(p.position() + 16);

            int channels = p.getShort() & 0xFFFF;
            int[] lengths = new int[channels];
            for (int c = 0; c < channels; c++) {
                p.getShort();
                lengths[c] = p.getInt();
            }
            p.position(p.position() + 12);
            // Doc do dai TRUOC roi moi cong: getInt da day con tro di 4 byte, viet gop mot
            // dong thi Java lay vi tri cu va nhay hut dung 4 byte do.
            int extra = p.getInt();
            p.position(p.position() + extra);

            for (int c = 0; c < index; c++) {
                p.position(p.position() + lengths[c]);
            }
            int compression = p.getShort() & 0xFFFF;
            byte[] out = new byte[width * height];

            if (compression == 2) {
                // ZIP: mot luong nen lien cho ca kenh, khong co bang do dai hang.
                Inflater inflater = new Inflater();
                inflater.setInput(block, p.position(), lengths[index] - 2);
                try {
                    int at = 0;
                    while (at < out.length && !inflater.finished()) {
                        int made = inflater.inflate(out, at, out.length - at);
                        if (made == 0) {
                            break;
                        }
                        at += made;
                    }
                } catch (DataFormatException ex) {
                    throw new IllegalStateException("Khong giai nen duoc kenh " + index, ex);
                } finally {
                    inflater.end();
                }
                return out;
            }

            // RLE: bo qua bang do dai hang vi o day giai tuan tu tu dau.
            p.position(p.position() + height * 2);
            int at = 0;
            while (at < out.length && p.hasRemaining()) {
                int control = p.get();
                if (control >= 0) {
                    int count = control + 1;
                    p.get(out, at, count);
                    at += count;
                } else if (control != -128) {
                    int count = 1 - control;
                    byte value = p.get();
                    java.util.Arrays.fill(out, at, at + count, value);
                    at += count;
                }
            }
            return out;
        }

        /** Giai nen toan bo dai anh thanh mang diem anh xen ke. */
        byte[] pixels() throws DataFormatException {
            ByteBuffer buffer = ByteBuffer.wrap(raw).order(order);
            Field offsets = fields.get(273);
            Field counts = fields.get(279);
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            for (int i = 0; i < offsets.count(); i++) {
                int at = buffer.getInt((int) (offsets.offset() + i * 4L));
                int length = buffer.getInt((int) (counts.offset() + i * 4L));

                Inflater inflater = new Inflater();
                inflater.setInput(raw, at, length);
                byte[] chunk = new byte[1 << 16];
                while (!inflater.finished()) {
                    int made = inflater.inflate(chunk);
                    if (made == 0) {
                        break;
                    }
                    out.write(chunk, 0, made);
                }
                inflater.end();
            }
            return out.toByteArray();
        }
    }
}
