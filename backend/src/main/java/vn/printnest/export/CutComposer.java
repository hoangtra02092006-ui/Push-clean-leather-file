package vn.printnest.export;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceCMYK;
import org.apache.pdfbox.pdmodel.graphics.color.PDSeparation;
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentGroup;
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentProperties;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.printnest.common.ApiException;
import vn.printnest.common.AppProperties;
import vn.printnest.common.ErrorCode;
import vn.printnest.common.Units;
import vn.printnest.nesting.model.Sheet;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Dung file CAT cua mot tam da dan khuon.
 *
 * <p>File cat la ban PDF <b>vector</b> rieng, chi chua duong cat quanh moi hinh va 4 dau
 * dinh vi o goc. No <b>khong phai ban in va khong thay the ban in</b>: tho mo no bang
 * Illustrator hoac CorelDRAW roi day sang may cat Graphtec qua plugin Cutting Master.
 *
 * <p><b>Vi sao dung lai ban PDF thay vi doc nguoc file TIF.</b> Yeu cau ban dau noi dau
 * vao la file TIF da xuat. Nhung ca hai duong deu bat nguon tu {@link PdfComposer}, ma di
 * qua ban PDF thi:
 *
 * <ul>
 *   <li>Duong cat va ban in <b>khong the lech nhau</b> - cung mot cho quyet dinh hinh nam
 *       o dau, dung bat bien "chi co MOT cho" ma {@link TiffComposer} cung theo.</li>
 *   <li>Khong phai dung ban TIF truoc roi moi cat duoc: dung mot ban TIF day o 300 DPI
 *       mat gan ba chuc giay, ma file cat khong can den mot byte nao cua no.</li>
 * </ul>
 *
 * <p><b>Ve KHONG kem duong cat quanh hinh.</b> Ban in co the duoc bat tuy chon ve khung
 * xam quanh moi hinh. Ban cat thi luon ve tat: neu khong, khung xam do cung thanh net ve
 * va duong cat se di vong quanh cai khung chu khong quanh hinh.
 */
@Component
public class CutComposer {

    private static final Logger log = LoggerFactory.getLogger(CutComposer.class);

    /**
     * Mau thay the cua muc cat khi xem tren man hinh.
     *
     * <p>Thong le nganh in la hong canh sen cho duong cat, nhung xuong doi net DEN cho
     * giong cai ho nhin trong Cutting Master. Mau nay chi de NHIN: may cat nhan dang
     * duong cat bang TEN mau muc rieng chu khong bang mau hien thi.
     */
    private static final float[] SPOT_PREVIEW_CMYK = {0f, 0f, 0f, 1f};

    /** Ten hai lop trong file PDF. */
    private static final String REG_MARK_LAYER = "RegMarks";

    private final PdfComposer pdfComposer;
    private final AppProperties properties;

    public CutComposer(PdfComposer pdfComposer, AppProperties properties) {
        this.pdfComposer = pdfComposer;
        this.properties = properties;
    }

    /**
     * Xuat mot tam thanh file cat.
     *
     * @param sheet tam can xuat
     * @return noi dung file PDF cat
     */
    public byte[] compose(Sheet sheet) {
        AppProperties.Cut settings = properties.cut();
        AppProperties.Tiff.White white = properties.tiff().white();
        long start = System.currentTimeMillis();

        // Luon ve tat duong cat quanh hinh - xem chu thich dau lop.
        byte[] pdf = pdfComposer.compose(sheet, false);

        try (PDDocument source = Loader.loadPDF(pdf)) {
            BufferedImage image = new PDFRenderer(source)
                    .renderImageWithDPI(0, settings.dpi(), ImageType.ARGB);
            int width = image.getWidth();
            int height = image.getHeight();

            // DUNG vung phu cua kenh muc trang W1, khong dung mot phep do rieng. Nho vay
            // duong cat chay dung tren vien cua lop W1 - dung cai tho nhin thay khi bat
            // kenh do len trong Photoshop.
            //
            // Nhung KHONG lay ban da co vao. Phep co do co de lop trang nam LOT trong lop
            // mau; dem no di cat thi net manh bi xoa sach: mot net day 2 diem anh ma co
            // moi ben 1 diem la dut thanh tung cham roi rac. Do that tren mot ban in cua
            // xuong, dong chu nho "NGUYEN BAN TRA VIET" vo thanh 59 cham co 0,007 mm2 -
            // dung bang mot diem anh - roi bi buoc loc bui don sach.
            byte[] cutMask = WhiteChannel.coverage(image,
                    white.alphaThreshold(), white.whiteTolerance());
            image.flush();

            int radius = (int) Math.round(settings.offsetMm() * settings.dpi() / 25.4);
            if (radius > 0) {
                cutMask = CutContours.dilate(cutMask, width, height, radius);
            }

            guardMarkZones(cutMask, width, height, sheet, settings);

            List<List<double[]>> paths = outlines(cutMask, width, height, settings);
            byte[] result = writePdf(sheet, paths, width, height, settings);

            log.info("Da dung file cat tam {}: {} duong cat, no ra {} mm, mat {} ms",
                    sheet.index() + 1, paths.size(), settings.offsetMm(),
                    System.currentTimeMillis() - start);
            return result;
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Khong dung duoc file cat: " + ex.getMessage());
        }
    }

    /**
     * Do vien va bo nhung mang qua nho.
     *
     * <p>Lay CA vien ngoai lan vien lo ben trong - dung nhu khi stroke mot selection quanh
     * layer trong Photoshop. Nho vien lo ma ruot chu "o", "a", "e" duoc khoet ra, khong thi
     * boc len thanh cuc dac, doc khong ra.
     *
     * <p>Toa do tra ve da doi sang MILIMET, goc o trai-duoi giong quy uoc PDF - anh thi
     * dem hang tu tren xuong nen truc doc phai lat mot lan o day.
     */
    private List<List<double[]>> outlines(byte[] cutMask, int width, int height,
                                          AppProperties.Cut settings) {
        double mmPerPixel = 25.4 / settings.dpi();
        double minAreaPixels = settings.minAreaMm2() / (mmPerPixel * mmPerPixel);
        double tolerance = settings.simplifyMm() / mmPerPixel;

        List<List<double[]>> outlines = new ArrayList<>();
        int dropped = 0;
        int holes = 0;

        for (CutContours.Path path : CutContours.trace(cutMask, width, height)) {
            // Dien tich AM la vien mot lo ben trong hinh. Giu lai - xem chu thich dau ham.
            double area = path.signedArea();
            if (Math.abs(area) < minAreaPixels) {
                dropped++;
                continue;
            }
            if (area < 0) {
                holes++;
            }

            List<double[]> simplified = CutContours.simplify(path.points(), tolerance);
            List<double[]> inMillimetres = new ArrayList<>(simplified.size());
            for (double[] point : simplified) {
                inMillimetres.add(new double[]{
                        point[0] * mmPerPixel,
                        (height - point[1]) * mmPerPixel});
            }
            outlines.add(inMillimetres);
        }

        log.info("Duong cat: {} vien ngoai, {} vien lo ben trong, bo {} mang nho hon {} mm2",
                outlines.size() - holes, holes, dropped, settings.minAreaMm2());
        return outlines;
    }

    /**
     * Tu choi xuat file neu co hinh lan vao cho phai de trong cho dau dinh vi.
     *
     * <p>May cat dung camera do 4 dau nay de biet phim nam lech bao nhieu so voi luc in.
     * Co net ve lan vao vung do thi camera do nham, va may chay lech - hong ca tam phim.
     *
     * <p>Bao loi o day chu khong xuat ra roi de tho phat hien khi dao da ha xuong. Ban IN
     * van tai duoc binh thuong; chi rieng ban CAT la tu choi.
     */
    private void guardMarkZones(byte[] cutMask, int width, int height, Sheet sheet,
                                AppProperties.Cut settings) {
        double zoneMm = settings.mark().zoneMm();
        int zone = (int) Math.ceil(zoneMm * settings.dpi() / 25.4);
        int side = Math.min(zone, Math.min(width, height));

        String[] corners = {"tren-trai", "tren-phai", "duoi-trai", "duoi-phai"};
        int[][] origins = {
                {0, 0}, {width - side, 0}, {0, height - side}, {width - side, height - side}};

        for (int corner = 0; corner < origins.length; corner++) {
            int fromX = origins[corner][0];
            int fromY = origins[corner][1];

            for (int y = fromY; y < fromY + side; y++) {
                for (int x = fromX; x < fromX + side; x++) {
                    if (cutMask[y * width + x] == 0) {
                        continue;
                    }
                    throw new ApiException(ErrorCode.CUT_MARK_AREA_BUSY, String.format(
                            "Tam %d co hinh lan vao goc %s, cho do phai de trong %.0f x %.0f mm "
                                    + "cho dau dinh vi cua may cat. Hay noi rong le tam len "
                                    + "it nhat %.0f mm roi ghep lai, hoac bot so hinh tren tam. "
                                    + "Ban in PDF va TIF khong vuong gioi han nay.",
                            sheet.index() + 1, corners[corner], zoneMm, zoneMm, zoneMm));
                }
            }
        }
    }

    /**
     * Ghi file PDF: mot lop duong cat, mot lop dau dinh vi.
     *
     * <p>Kich thuoc trang lay tu {@link Sheet} chu khong tu so diem anh: so diem anh da qua
     * mot lan lam tron khi ve, nen quy nguoc lai co the lech vai phan tram milimet so voi
     * ban in. Trang cat phai trung khit ban in thi may cat moi bu lech dung.
     */
    private byte[] writePdf(Sheet sheet, List<List<double[]>> paths, int width, int height,
                            AppProperties.Cut settings) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(
                    Units.mmToPt(sheet.widthMm()), Units.mmToPt(sheet.lengthMm())));
            document.addPage(page);

            PDOptionalContentGroup cutLayer = new PDOptionalContentGroup(settings.spotName());
            PDOptionalContentGroup markLayer = new PDOptionalContentGroup(REG_MARK_LAYER);
            PDOptionalContentProperties layers = new PDOptionalContentProperties();
            layers.addGroup(cutLayer);
            layers.addGroup(markLayer);
            document.getDocumentCatalog().setOCProperties(layers);

            PDSeparation spot = spotColour(settings.spotName());

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginMarkedContent(COSName.OC, cutLayer);
                content.setStrokingColor(new PDColor(new float[]{1f}, spot));
                content.setLineWidth((float) settings.strokePt());
                for (List<double[]> path : paths) {
                    drawClosedPath(content, path);
                }
                content.endMarkedContent();

                content.beginMarkedContent(COSName.OC, markLayer);
                content.setNonStrokingColor(new PDColor(new float[]{0f, 0f, 0f, 1f},
                        PDDeviceCMYK.INSTANCE));
                drawRegistrationMarks(content, sheet, settings.mark());
                content.endMarkedContent();
            }

            logGeometry(sheet, paths, width, height, settings);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static void drawClosedPath(PDPageContentStream content, List<double[]> path)
            throws IOException {
        double[] first = path.get(0);
        content.moveTo(Units.mmToPt(first[0]), Units.mmToPt(first[1]));
        for (int i = 1; i < path.size(); i++) {
            double[] point = path.get(i);
            content.lineTo(Units.mmToPt(point[0]), Units.mmToPt(point[1]));
        }
        content.closeAndStroke();
    }

    /**
     * Ve 4 dau dinh vi, moi goc mot hinh theo cau hinh.
     *
     * <p>Goc NGOAI cua dau dat dung goc trang, khong chua le. Chon vay vi kich thuoc trang
     * cat phai giu dung bang ban in - khong duoc noi trang ra de nhet dau vao.
     *
     * <p>Moi dau ve bang cac hinh chu nhat TO DAY chu khong phai net ke. To day thi do day
     * la kich thuoc that cua hinh, khong phu thuoc phan mem nao dien giai do day net ra sao.
     */
    private static void drawRegistrationMarks(PDPageContentStream content, Sheet sheet,
                                              AppProperties.Cut.Mark mark) throws IOException {
        float length = Units.mmToPt(mark.lengthMm());
        float thickness = Units.mmToPt(mark.thicknessMm());
        float right = Units.mmToPt(sheet.widthMm());
        float top = Units.mmToPt(sheet.lengthMm());

        // Moi goc: toa do goc, va huong di VAO TRONG tam theo hai truc.
        drawMark(content, mark.bottomLeft(), 0, 0, 1, 1, length, thickness);
        drawMark(content, mark.bottomRight(), right, 0, -1, 1, length, thickness);
        drawMark(content, mark.topLeft(), 0, top, 1, -1, length, thickness);
        drawMark(content, mark.topRight(), right, top, -1, -1, length, thickness);
    }

    private static void drawMark(PDPageContentStream content, AppProperties.Cut.Mark.Shape shape,
                                 float cornerX, float cornerY, int towardsX, int towardsY,
                                 float length, float thickness) throws IOException {
        // Goc trai-duoi cua o vuong bao quanh dau, quy ve he toa do PDF.
        float left = towardsX > 0 ? cornerX : cornerX - length;
        float bottom = towardsY > 0 ? cornerY : cornerY - length;

        switch (shape) {
            case SQUARE_WITH_EDGE -> {
                // Hai net nam o hai canh PHIA TRONG cua o vuong. Hai canh con lai la hai
                // mep giay o goc do, nen may cat nhin ra mot o vuong khep kin.
                float innerX = towardsX > 0 ? left + length - thickness : left;
                float innerY = towardsY > 0 ? bottom + length - thickness : bottom;
                content.addRect(innerX, bottom, thickness, length);
                content.addRect(left, innerY, length, thickness);
            }
            case SQUARE_FILLED -> content.addRect(left, bottom, length, length);
            case SQUARE_OUTLINE -> {
                // Bon thanh vien, khong dung net ke de do day la kich thuoc that.
                content.addRect(left, bottom, length, thickness);
                content.addRect(left, bottom + length - thickness, length, thickness);
                content.addRect(left, bottom, thickness, length);
                content.addRect(left + length - thickness, bottom, thickness, length);
            }
            case L -> {
                float armY = towardsY > 0 ? cornerY : cornerY - thickness;
                float armX = towardsX > 0 ? cornerX : cornerX - thickness;
                content.addRect(left, armY, length, thickness);
                content.addRect(armX, bottom, thickness, length);
            }
        }
        content.fill();
    }

    /**
     * Mau muc rieng cho duong cat.
     *
     * <p>Illustrator va Cutting Master nhan dang duong cat bang <b>ten</b> mau nay, khong
     * phai bang mau hien thi. Mau thay the chi de nhin tren man hinh cho de phan biet.
     */
    private static PDSeparation spotColour(String name) throws IOException {
        COSDictionary tint = new COSDictionary();
        tint.setInt(COSName.FUNCTION_TYPE, 2);
        tint.setItem(COSName.DOMAIN, floats(0f, 1f));
        tint.setItem(COSName.getPDFName("C0"), floats(0f, 0f, 0f, 0f));
        tint.setItem(COSName.getPDFName("C1"), floats(SPOT_PREVIEW_CMYK));
        tint.setItem(COSName.N, COSInteger.ONE);

        COSArray separation = new COSArray();
        separation.add(COSName.SEPARATION);
        separation.add(COSName.getPDFName(name));
        separation.add(COSName.DEVICECMYK);
        separation.add(tint);
        return new PDSeparation(separation);
    }

    private static COSArray floats(float... values) {
        COSArray array = new COSArray();
        for (float value : values) {
            array.add(new COSFloat(value));
        }
        return array;
    }

    /**
     * Ghi ra toa do de doi chieu voi so hien trong Cutting Master.
     *
     * <p>Khong co file PDF mau de so tung milimet, nen day la cach kiem duy nhat truoc khi
     * chay may that: mo file bang Illustrator, bat Cutting Master len va so bon con so nay
     * voi phan Job size va vi tri dau ma no hien.
     */
    private void logGeometry(Sheet sheet, List<List<double[]>> paths, int width, int height,
                             AppProperties.Cut settings) {
        double length = settings.mark().lengthMm();
        log.info("File cat tam {}: trang {} x {} mm ({} x {} diem o {} DPI), {} duong cat",
                sheet.index() + 1,
                Units.round2(sheet.widthMm()), Units.round2(sheet.lengthMm()),
                width, height, settings.dpi(), paths.size());
        log.info("  4 dau dinh vi, canh {} mm, day {} mm, goc trang dat tai: "
                        + "(0, 0) - ({}, 0) - (0, {}) - ({}, {}) mm",
                length, settings.mark().thicknessMm(),
                Units.round2(sheet.widthMm()), Units.round2(sheet.lengthMm()),
                Units.round2(sheet.widthMm()), Units.round2(sheet.lengthMm()));
    }
}
