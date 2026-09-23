package vn.printnest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiem thu file CAT cho may cat Graphtec CE7000-60.
 *
 * <p>File cat la ban PDF vector rieng, chi chua duong cat quanh moi hinh va 4 dau dinh vi
 * o goc. No KHONG phai ban in.
 *
 * <p><b>Khong co file PDF mau de doi chieu</b> - moi thong so doc tu anh chup man hinh
 * Cutting Master cua xuong. Nen bai nay doc thang LENH VE trong file thay vi so voi mot
 * file co san: kich thuoc trang, vi tri va kich thuoc tung net dau, ten mau muc rieng.
 * Sai mot con so o day thi camera cua may cat do nham dau va chay lech ca tam phim.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.storage.path=${java.io.tmpdir}/printnest-test-cut",
        "logging.level.vn.printnest=WARN"
})
class CutExportIntegrationTest {

    /** Doi point sang milimet de doi chieu voi thong so cua Cutting Master. */
    private static final double PT_TO_MM = 25.4 / 72;

    /** Sai so cho phep khi so mot con so doc tu file voi thong so mong doi. */
    private static final double TOLERANCE_MM = 0.01;

    /** Canh cua o vuong dau dinh vi, theo mac dinh cua he thong. */
    private static final double MARK_MM = 15;

    /** Dau cong vung trong bat buoc quanh no: cho phai de trong o moi goc. */
    private static final double ZONE_MM = MARK_MM + 5;

    /**
     * Le tam du rong de bon goc con trong cho dau dinh vi.
     *
     * <p>Dat rong hon {@link #ZONE_MM} de cac bai khac do duoc truong hop KHONG phai noi
     * trang. Le MAC DINH 5 mm cua he thong thi khong du - khi do ban cat tu noi rong ra,
     * va do la viec cua bai {@code widensThePageWhenArtworkSitsWhereAMarkMustGo}.
     */
    private static final double CLEAR_MARGIN_MM = 25;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Trang cat dung bang trang in, khong xe xich mot ly")
    void pageMatchesTheSheetExactly() throws Exception {
        byte[] cut = downloadCut();

        try (PDDocument document = Loader.loadPDF(cut)) {
            PDRectangle box = document.getPage(0).getMediaBox();

            // Tam rong 570 mm theo yeu cau ghep. Trang cat lech di thi may cat bu lech
            // sai, va moi hinh deu bi cat tru hao dan tu dau tam den cuoi tam.
            assertThat(box.getWidth() * PT_TO_MM)
                    .as("chieu rong trang cat").isCloseTo(570.0, org.assertj.core.data.Offset.offset(TOLERANCE_MM));
        }
    }

    @Test
    @DisplayName("Co dung hai lop: duong cat va dau dinh vi")
    void hasBothLayers() throws Exception {
        try (PDDocument document = Loader.loadPDF(downloadCut())) {
            var properties = document.getDocumentCatalog().getOCProperties();
            assertThat(properties).as("file phai khai bao lop").isNotNull();
            assertThat(properties.getGroupNames())
                    .as("mot lop duong cat, mot lop dau dinh vi")
                    .containsExactlyInAnyOrder("CutContour", "RegMarks");
        }
    }

    /**
     * Duong cat phai dung mau muc RIENG mang dung ten.
     *
     * <p>Illustrator va Cutting Master nhan dang duong cat bang TEN mau nay. To duong cat
     * bang mau thuong thi may cat khong thay duong nao, va file mo ra trong nhin van y het.
     */
    @Test
    @DisplayName("Duong cat to bang mau muc rieng ten CutContour")
    void cutLinesUseTheNamedSpotColour() throws Exception {
        try (PDDocument document = Loader.loadPDF(downloadCut())) {
            COSDictionary resources = document.getPage(0).getResources().getCOSObject();
            COSDictionary spaces =
                    (COSDictionary) resources.getDictionaryObject(COSName.COLORSPACE);
            assertThat(spaces).as("trang phai khai bao khong gian mau").isNotNull();

            List<String> colorants = new ArrayList<>();
            for (COSName key : spaces.keySet()) {
                COSBase value = spaces.getDictionaryObject(key);
                if (value instanceof COSArray array && array.size() >= 2
                        && COSName.SEPARATION.equals(array.getObject(0))
                        && array.getObject(1) instanceof COSName name) {
                    colorants.add(name.getName());
                }
            }

            assertThat(colorants)
                    .as("phai co dung mot mau muc rieng, ten CutContour")
                    .containsExactly("CutContour");
        }
    }

    /**
     * Bon dau dinh vi: dung kich thuoc, dung cho.
     *
     * <p>Moi dau ve bang hai hinh chu nhat to day, nam o hai canh PHIA TRONG cach 15 mm tu
     * mep trang - hai canh con lai cua o vuong chinh la hai mep giay, nen may cat nhin ra
     * mot o vuong khep kin.
     */
    @Test
    @DisplayName("Bon dau dung 15 x 1 mm, khep o vuong cung mep trang")
    void registrationMarksSitInEveryCorner() throws Exception {
        byte[] cut = downloadCut();
        List<double[]> rectangles;
        double pageWidth;
        double pageHeight;

        try (PDDocument document = Loader.loadPDF(cut)) {
            PDPage page = document.getPage(0);
            pageWidth = page.getMediaBox().getWidth() * PT_TO_MM;
            pageHeight = page.getMediaBox().getHeight() * PT_TO_MM;
            rectangles = rectangles(page);
        }

        assertThat(rectangles).as("bon dau, moi dau hai net").hasSize(8);

        for (double[] rectangle : rectangles) {
            double longSide = Math.max(rectangle[2], rectangle[3]);
            double shortSide = Math.min(rectangle[2], rectangle[3]);
            assertThat(longSide).as("chieu dai net dau")
                    .isCloseTo(MARK_MM, org.assertj.core.data.Offset.offset(TOLERANCE_MM));
            assertThat(shortSide).as("do day net dau")
                    .isCloseTo(1.0, org.assertj.core.data.Offset.offset(TOLERANCE_MM));
        }

        // Moi goc phai co dung hai net, va chung phai KHEP O VUONG cung hai mep trang:
        // net doc cach mep doc dung 15 mm, net ngang cach mep ngang dung 15 mm.
        double[][] corners = {{0, 0}, {pageWidth, 0}, {0, pageHeight}, {pageWidth, pageHeight}};
        for (double[] corner : corners) {
            List<double[]> inCorner = rectangles.stream()
                    .filter(bar -> inCornerBox(bar, corner, MARK_MM))
                    .toList();

            assertThat(inCorner)
                    .as("goc (%.0f, %.0f) mm phai co dung hai net", corner[0], corner[1])
                    .hasSize(2);

            double farthestX = inCorner.stream()
                    .mapToDouble(bar -> Math.max(Math.abs(bar[0] - corner[0]),
                            Math.abs(bar[0] + bar[2] - corner[0])))
                    .max().orElseThrow();
            double farthestY = inCorner.stream()
                    .mapToDouble(bar -> Math.max(Math.abs(bar[1] - corner[1]),
                            Math.abs(bar[1] + bar[3] - corner[1])))
                    .max().orElseThrow();

            assertThat(farthestX)
                    .as("goc (%.0f, %.0f): hai net phai khep o vuong 15 mm theo chieu ngang",
                            corner[0], corner[1])
                    .isCloseTo(MARK_MM, org.assertj.core.data.Offset.offset(TOLERANCE_MM));
            assertThat(farthestY)
                    .as("goc (%.0f, %.0f): va 15 mm theo chieu doc", corner[0], corner[1])
                    .isCloseTo(MARK_MM, org.assertj.core.data.Offset.offset(TOLERANCE_MM));
        }
    }

    /** Hinh chu nhat nay co nam tron trong o vuong canh {@code side} o goc do khong. */
    private static boolean inCornerBox(double[] bar, double[] corner, double side) {
        double nearX = Math.min(Math.abs(bar[0] - corner[0]), Math.abs(bar[0] + bar[2] - corner[0]));
        double nearY = Math.min(Math.abs(bar[1] - corner[1]), Math.abs(bar[1] + bar[3] - corner[1]));
        return nearX <= side + TOLERANCE_MM && nearY <= side + TOLERANCE_MM;
    }

    /**
     * Hinh dau khai rieng tung goc, khong dung chung mot hinh.
     *
     * <p>Khong co file mau de doi chieu tung milimet nen hinh dau cua ca bon goc deu de o
     * cau hinh - bai nay chot lai rang moi goc doc RIENG mot gia tri, de sau nay sua mot
     * goc khong keo theo ba goc kia.
     */
    @Test
    @DisplayName("Net dau nam o hai canh PHIA TRONG, khong op vao mep trang")
    void marksSitOnTheInnerSidesOfTheCornerBox() throws Exception {
        List<double[]> bars;
        try (PDDocument document = Loader.loadPDF(downloadCut())) {
            bars = rectangles(document.getPage(0));
        }

        // Goc duoi-trai: net doc phai nam o x = 14..15 mm, net ngang o y = 14..15 mm.
        // Op vao mep (x = 0 hoac y = 0) la SAI - khi do hai net khong khep duoc o vuong
        // voi mep giay, ma nam de len chinh mep do.
        long verticalBar = bars.stream()
                .filter(bar -> Math.abs(bar[0] - (MARK_MM - 1)) < TOLERANCE_MM)
                .filter(bar -> Math.abs(bar[1]) < TOLERANCE_MM)
                .count();
        long horizontalBar = bars.stream()
                .filter(bar -> Math.abs(bar[0]) < TOLERANCE_MM)
                .filter(bar -> Math.abs(bar[1] - (MARK_MM - 1)) < TOLERANCE_MM)
                .count();

        assertThat(verticalBar).as("net doc cach mep trai 14 mm").isEqualTo(1);
        assertThat(horizontalBar).as("net ngang cach mep duoi 14 mm").isEqualTo(1);

        long huggingTheEdge = bars.stream()
                .filter(bar -> Math.abs(bar[0]) < TOLERANCE_MM && Math.abs(bar[1]) < TOLERANCE_MM)
                .count();
        assertThat(huggingTheEdge)
                .as("khong net nao duoc op vao dung goc trang")
                .isZero();
    }

    /**
     * Hinh lan vao cho de dau thi TU NOI RONG trang cat ra, khong tu choi nua.
     *
     * <p>Tam da ghep xong roi moi den luot ban cat, nen bat tho xep lai tam cho thoang goc
     * la bat lam lai tu dau. Ban cat la file RIENG, khong phai ban in - no duoc phep rong
     * hon de lay cho dat dau, mien la noi deu bon phia thi hai ban van dong tam.
     *
     * <p>Chi noi theo CHIEU DAI. Cuon chay lien tuc nen dai them khong ton gi, con be ngang
     * thi vuong kho cuon 603 mm va muc job toi da 576 mm cua Cutting Master - noi ngang la
     * may cat khong nhan job. Ma noi ngang cung khong can: day hinh len doc la no ra khoi
     * o vuong o goc roi.
     *
     * <p>Noi bao nhieu la du: mot vung dau la {@link #ZONE_MM}, nen noi toi da bang do la
     * o vuong phai de trong o goc khong con chua mot diem anh nao cua hinh.
     *
     * <p>Ban IN khong bi dong vao, van dung kich thuoc cu.
     */
    @Test
    @DisplayName("Hinh lan vao cho de dau: tu noi DAI trang cat, be ngang giu nguyen")
    void widensThePageWhenArtworkSitsWhereAMarkMustGo() throws Exception {
        // Le 0 mm va hinh to gan bang kho: hinh cham thang vao goc tam.
        String jobId = runJob(buildPdf(540, 200), 0, 2);
        double printLengthMm = sheetLengthMm(jobId);

        byte[] cut = mockMvc.perform(get("/api/v1/nesting/jobs/{id}/sheets/0/cut", jobId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        double pageWidth;
        double pageHeight;
        List<double[]> bars;
        List<double[]> cutPoints;
        try (PDDocument document = Loader.loadPDF(cut)) {
            PDPage page = document.getPage(0);
            pageWidth = page.getMediaBox().getWidth() * PT_TO_MM;
            pageHeight = page.getMediaBox().getHeight() * PT_TO_MM;
            bars = rectangles(page);
            cutPoints = cutPoints(page);
        }

        assertThat(pageWidth)
                .as("be ngang KHONG duoc dong vao: kho cuon 603 mm va muc job 576 mm")
                .isCloseTo(570.0, org.assertj.core.data.Offset.offset(TOLERANCE_MM));

        double pad = (pageHeight - printLengthMm) / 2;
        assertThat(pad).as("noi deu hai dau, va khong bao gio qua mot vung dau")
                .isBetween(0.5, Math.ceil(ZONE_MM) + TOLERANCE_MM);

        double[][] corners = {
                {0, 0}, {pageWidth, 0}, {0, pageHeight}, {pageWidth, pageHeight}};
        for (double[] corner : corners) {
            assertThat(bars.stream().filter(bar -> inCornerBox(bar, corner, MARK_MM)).count())
                    .as("goc (%.0f, %.0f) van phai co dung hai net dau", corner[0], corner[1])
                    .isEqualTo(2);
        }

        // Cho de dau phai SACH: con mot net cat di qua day la camera do nham dau.
        for (double[] corner : corners) {
            assertThat(cutPoints).as("khong duong cat nao duoc di vao vung dau o goc "
                            + "(%.0f, %.0f)", corner[0], corner[1])
                    .noneMatch(point -> Math.abs(point[0] - corner[0]) < ZONE_MM - TOLERANCE_MM
                            && Math.abs(point[1] - corner[1]) < ZONE_MM - TOLERANCE_MM);
        }

        mockMvc.perform(get("/api/v1/nesting/jobs/{id}/sheets/0/pdf", jobId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/nesting/jobs/{id}/sheets/0/tif", jobId))
                .andExpect(status().isOk());
    }

    /**
     * Hinh CACH goc mot doan - le mac dinh 5 mm - thi van phai noi cho du.
     *
     * <p>Bai tren dung le 0 mm nen hinh cham thang vao goc, va truong hop do phep do dung
     * ngay o hang dau tien. Bai nay moi do den phan giua: phai tim hang DAU TIEN co hinh.
     *
     * <p>Co that: ban dau toi viet vong lap thoat moi vong trong, vong ngoai chay tiep va
     * ghi de tri so bang hang CUOI co hinh. Bai tren van xanh - vi hang dau tien da co hinh
     * nen no dung ngay - con chay that thi noi 1 mm thay vi 15, dau de len hinh.
     */
    @Test
    @DisplayName("Hinh cach goc mot le van duoc noi du, khong noi hut")
    void widensEnoughWhenArtworkIsSetBackFromTheCorner() throws Exception {
        String jobId = runJob(buildPdf(200, 180), 5, 12);
        double printLengthMm = sheetLengthMm(jobId);

        byte[] cut = mockMvc.perform(get("/api/v1/nesting/jobs/{id}/sheets/0/cut", jobId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        double pageHeight;
        List<double[]> cutPoints;
        try (PDDocument document = Loader.loadPDF(cut)) {
            PDPage page = document.getPage(0);
            pageHeight = page.getMediaBox().getHeight() * PT_TO_MM;
            cutPoints = cutPoints(page);
        }

        // Le 5 mm: cho de dau con thieu 20 - 5 = 15 mm.
        double pad = (pageHeight - printLengthMm) / 2;
        assertThat(pad).as("phai noi khoang 15 mm, khong duoc noi hut")
                .isBetween(14.0, Math.ceil(ZONE_MM) + TOLERANCE_MM);

        double[][] corners = {
                {0, 0}, {570.0, 0}, {0, pageHeight}, {570.0, pageHeight}};
        for (double[] corner : corners) {
            assertThat(cutPoints).as("vung dau o goc (%.0f, %.0f) phai sach",
                            corner[0], corner[1])
                    .noneMatch(point -> Math.abs(point[0] - corner[0]) < ZONE_MM - TOLERANCE_MM
                            && Math.abs(point[1] - corner[1]) < ZONE_MM - TOLERANCE_MM);
        }
    }

    @Test
    @DisplayName("Tai ca bo file cat duoi dang zip")
    void downloadsEveryCutFileAsZip() throws Exception {
        String jobId = runJob(buildPdf(100, 60), CLEAR_MARGIN_MM, 6);

        byte[] zip = mockMvc.perform(
                        get("/api/v1/nesting/jobs/{id}/export.zip", jobId).param("format", "cut"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        List<String> names = new ArrayList<>();
        try (var in = new java.util.zip.ZipInputStream(new ByteArrayInputStream(zip))) {
            for (var entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                names.add(entry.getName());
            }
        }

        assertThat(names).as("moi tam mot file").isNotEmpty();
        assertThat(names).as("file cat phai deo chu -cat de khong lan voi ban in")
                .allMatch(name -> name.endsWith("-cat.pdf"));
    }

    @Test
    @DisplayName("Go sai dinh dang thi bao ngay chu khong lang le tra ve PDF")
    void rejectsAnUnknownFormat() throws Exception {
        String jobId = runJob(buildPdf(100, 60), CLEAR_MARGIN_MM, 2);

        mockMvc.perform(get("/api/v1/nesting/jobs/{id}/export.zip", jobId).param("format", "dxf"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Ve ban in va ban cat chong len nhau ra PNG de nhin bang mat.
     *
     * <p>So lieu noi duoc "dau nam dung cho" nhung khong noi duoc "duong cat co bao ngoai
     * hinh khong, co cat vao net nao khong". Chi co nhin moi biet.
     *
     * <p>Ghi vao {@code target/} nen {@code mvn clean} la don sach, khong lan vao kho.
     */
    @Test
    @DisplayName("Ve ban in va duong cat chong len nhau ra PNG de doi chieu")
    void writesPreviewForEyeballing() throws Exception {
        String jobId = runJob(buildRingPdf(), CLEAR_MARGIN_MM, 4);

        byte[] print = mockMvc.perform(get("/api/v1/nesting/jobs/{id}/sheets/0/pdf", jobId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        byte[] cut = mockMvc.perform(get("/api/v1/nesting/jobs/{id}/sheets/0/cut", jobId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        java.awt.image.BufferedImage printed = render(print);
        java.awt.image.BufferedImage cutLines = render(cut);

        java.awt.image.BufferedImage overlay = new java.awt.image.BufferedImage(
                printed.getWidth(), printed.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = overlay.createGraphics();
        g.setColor(new java.awt.Color(238, 238, 238));
        g.fillRect(0, 0, overlay.getWidth(), overlay.getHeight());
        g.drawImage(printed, 0, 0, null);
        g.dispose();

        // To duong cat thanh do va dau dinh vi thanh xanh, de phan biet voi ban in.
        for (int y = 0; y < Math.min(cutLines.getHeight(), overlay.getHeight()); y++) {
            for (int x = 0; x < Math.min(cutLines.getWidth(), overlay.getWidth()); x++) {
                int pixel = cutLines.getRGB(x, y);
                if ((pixel >>> 24) < 40) {
                    continue;
                }
                int red = (pixel >> 16) & 0xFF;
                int green = (pixel >> 8) & 0xFF;
                int blue = pixel & 0xFF;
                if (red > 240 && green > 240 && blue > 240) {
                    continue;
                }
                boolean dark = red < 100 && green < 100 && blue < 100;
                overlay.setRGB(x, y, dark ? 0x0000FF : 0xFF0000);
            }
        }

        java.nio.file.Path folder = java.nio.file.Path.of("target", "cut-check");
        java.nio.file.Files.createDirectories(folder);
        java.nio.file.Files.write(folder.resolve("ban-cat.pdf"), cut);
        javax.imageio.ImageIO.write(overlay, "png", folder.resolve("chong-len-nhau.png").toFile());
        System.out.println("Da ghi anh doi chieu vao " + folder.toAbsolutePath()
                + " (do = duong cat, xanh = dau dinh vi)");
    }

    private static java.awt.image.BufferedImage render(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new org.apache.pdfbox.rendering.PDFRenderer(document)
                    .renderImageWithDPI(0, 50, org.apache.pdfbox.rendering.ImageType.ARGB);
        }
    }

    /**
     * Mot hinh vanh khuyen: vien ngoai vuong, giua thung mot o vuong.
     *
     * <p>Phai THUNG that - de trong suot - chu khong phai to trang len giua. Mot o trang
     * nam kin ben trong hinh van duoc coi la CO HINH (xem {@code WhiteChannel}: nen chi
     * duoc loai khi no noi ra duoc mep tam), nen to trang thi khong sinh ra lo nao ca va
     * bai test khong kiem duoc gi.
     *
     * <p>Hinh chu nhat dac cung khong dung duoc: no khong co lo nao de ma kiem.
     */
    private static byte[] buildRingPdf() throws IOException {
        float size = (float) (60 / PT_TO_MM);
        float hole = size * 0.4f;
        float inset = (size - hole) / 2;

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(size, size));
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.setNonStrokingColor(0.1f, 0.2f, 0.8f);
                // Vien ngoai di mot chieu, vien lo di chieu NGUOC lai; to theo quy tac
                // chan-le thi phan giua thanh lo thung that su.
                content.moveTo(0, 0);
                content.lineTo(size, 0);
                content.lineTo(size, size);
                content.lineTo(0, size);
                content.closePath();
                content.moveTo(inset, inset);
                content.lineTo(inset, inset + hole);
                content.lineTo(inset + hole, inset + hole);
                content.lineTo(inset + hole, inset);
                content.closePath();
                content.fillEvenOdd();
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            document.save(bytes);
            return bytes.toByteArray();
        }
    }

    // ------------------------------------------------------------------

    /** Doc cac lenh {@code re} trong luong noi dung, doi sang milimet. */
    private static List<double[]> rectangles(PDPage page) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (InputStream in = page.getContents()) {
            in.transferTo(raw);
        }
        Matcher matcher = Pattern
                .compile("([-0-9.]+) ([-0-9.]+) ([-0-9.]+) ([-0-9.]+) re")
                .matcher(raw.toString("ISO-8859-1"));

        List<double[]> rectangles = new ArrayList<>();
        while (matcher.find()) {
            rectangles.add(new double[]{
                    Double.parseDouble(matcher.group(1)) * PT_TO_MM,
                    Double.parseDouble(matcher.group(2)) * PT_TO_MM,
                    Double.parseDouble(matcher.group(3)) * PT_TO_MM,
                    Double.parseDouble(matcher.group(4)) * PT_TO_MM});
        }
        return rectangles;
    }

    /** Doc cac dinh cua duong cat - lenh {@code m} va {@code l} - doi sang milimet. */
    private static List<double[]> cutPoints(PDPage page) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (InputStream in = page.getContents()) {
            in.transferTo(raw);
        }
        Matcher matcher = Pattern.compile("([-0-9.]+) ([-0-9.]+) [ml]\\b")
                .matcher(raw.toString("ISO-8859-1"));

        List<double[]> points = new ArrayList<>();
        while (matcher.find()) {
            points.add(new double[]{
                    Double.parseDouble(matcher.group(1)) * PT_TO_MM,
                    Double.parseDouble(matcher.group(2)) * PT_TO_MM});
        }
        return points;
    }

    /** Hinh chu nhat nay co mot goc dat dung vao diem do khong. */
    private static boolean touches(double[] rectangle, double[] corner) {
        double left = rectangle[0];
        double bottom = rectangle[1];
        double right = left + rectangle[2];
        double top = bottom + rectangle[3];

        boolean onX = Math.abs(left - corner[0]) < TOLERANCE_MM
                || Math.abs(right - corner[0]) < TOLERANCE_MM;
        boolean onY = Math.abs(bottom - corner[1]) < TOLERANCE_MM
                || Math.abs(top - corner[1]) < TOLERANCE_MM;
        return onX && onY;
    }

    private byte[] downloadCut() throws Exception {
        String jobId = runJob(buildPdf(100, 60), CLEAR_MARGIN_MM, 4);
        return mockMvc.perform(get("/api/v1/nesting/jobs/{id}/sheets/0/cut", jobId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    /** Chieu dai tam da ghep, doc tu ket qua job - de so voi chieu dai trang cat. */
    private double sheetLengthMm(String jobId) throws Exception {
        String body = mockMvc.perform(get("/api/v1/nesting/jobs/{id}", jobId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body)
                .get("result").get("sheets").get(0).get("lengthMm").asDouble();
    }

    private String runJob(byte[] pdf, double marginMm, int quantity) throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "nhan.pdf",
                MediaType.APPLICATION_PDF_VALUE, pdf);
        MvcResult uploaded = mockMvc.perform(multipart("/api/v1/files").file(file))
                .andExpect(status().isOk())
                .andReturn();
        String fileId = objectMapper.readTree(uploaded.getResponse().getContentAsString())
                .get(0).get("id").asText();

        String body = """
                {
                  "sheetWidthMm": 570, "marginMm": %s, "gapMm": 3,
                  "maxSheetLengthMm": 1000,
                  "allowRotateGlobal": true, "drawCutLines": false,
                  "items": [{"fileId": "%s", "quantity": %d, "allowRotate": true}]
                }
                """.formatted(marginMm, fileId, quantity);
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

    /** Mot hinh chu nhat dac, kich thuoc tinh bang milimet. */
    private static byte[] buildPdf(double widthMm, double heightMm) throws IOException {
        float width = (float) (widthMm / PT_TO_MM);
        float height = (float) (heightMm / PT_TO_MM);

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(width, height));
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.setNonStrokingColor(0.1f, 0.2f, 0.8f);
                content.addRect(0, 0, width, height);
                content.fill();
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            document.save(bytes);
            return bytes.toByteArray();
        }
    }
}
