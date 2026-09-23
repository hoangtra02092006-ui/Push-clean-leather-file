package vn.printnest.export;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
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

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Dung ban TIFF cua mot tam da dan khuon.
 *
 * <p><b>Day la NGOAI LE duy nhat cua bat bien "khong raster hoa PDF".</b> Ban PDF van la
 * ban chinh va van giu nguyen vector; TIFF la ban THEM, cho nhung RIP chi nhan anh bitmap.
 * Ai sua cho nay nho: dung bao gio thay ban PDF bang ban raster, chi them ben canh.
 *
 * <p>Cach lam: dung lai dung {@link PdfComposer} de co ban PDF vector, roi ve no thanh
 * anh. Nho vay chi co MOT cho quyet dinh hinh nam o dau tren tam - sua bo tri thi ca hai
 * dinh dang doi theo, khong the lech nhau.
 *
 * <p><b>Bo nho la rang buoc that o day.</b> Mot tam 57 x 100 cm o 300 DPI la 6.732 x
 * 11.811 diem = 318 MB trong bo nho, va do la anh CHUA nen. Vi vay co mot tran cung:
 * vuot qua thi bao loi ro rang chu khong de may chu het bo nho roi tu khoi dong lai, mat
 * sach viec cua nguoi khac dang lam do.
 */
@Component
public class TiffComposer {

    private static final Logger log = LoggerFactory.getLogger(TiffComposer.class);

    /** Moi diem anh RGB chiem 4 byte trong bo nho (int packed). */
    private static final int BYTES_PER_PIXEL = 4;

    /**
     * So hang anh moi dai (strip).
     *
     * <p>Mac dinh cua Java la 1 - tuc la mot anh cao 5.000 hang se thanh 5.000 dai. Moi
     * dai them mot muc trong bang tag, va LZW phai khoi dong lai bo tu dien o dau moi
     * dai nen ty le nen kem han han. 64 hang mot dai la muc thong dung.
     */
    private static final int ROWS_PER_STRIP = 64;

    private final PdfComposer pdfComposer;
    private final AppProperties properties;

    public TiffComposer(PdfComposer pdfComposer, AppProperties properties) {
        this.pdfComposer = pdfComposer;
        this.properties = properties;
    }

    /**
     * Ve mot tam thanh TIFF nen LZW.
     *
     * @param sheet        tam can ve
     * @param drawCutLines co ve duong cat quanh moi hinh khong
     */
    public byte[] compose(Sheet sheet, boolean drawCutLines) {
        int dpi = properties.tiff().dpi();
        guardSize(sheet, dpi);

        byte[] pdf = pdfComposer.compose(sheet, drawCutLines);
        long start = System.currentTimeMillis();

        try (PDDocument document = Loader.loadPDF(pdf)) {
            // RGB chu khong ARGB: ban in khong co khai niem trong suot, va bo mot kenh la
            // bot mot phan tu dung luong trong bo nho.
            BufferedImage image = new PDFRenderer(document)
                    .renderImageWithDPI(0, dpi, ImageType.RGB);
            byte[] tiff = encode(image, dpi);
            image.flush();

            log.info("Da dung TIFF tam {}: {} x {} diem o {} DPI, {} MB, mat {} ms",
                    sheet.index() + 1, image.getWidth(), image.getHeight(), dpi,
                    tiff.length / (1024 * 1024), System.currentTimeMillis() - start);
            return tiff;
        } catch (OutOfMemoryError err) {
            // Bat rieng: neu de no bay len tren, Spring tra 500 khong ro rang va may chu
            // co the dang o trang thai lung lay. Bao thang cho nguoi dung cach xu ly.
            throw new ApiException(ErrorCode.TIFF_TOO_LARGE,
                    "May chu khong du bo nho de dung file TIF o " + dpi + " DPI. "
                            + "Hay giam do phan giai TIF hoac dat chieu dai toi da moi file ngan lai.");
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Khong dung duoc file TIF: " + ex.getMessage());
        }
    }

    /**
     * Chan truoc nhung tam qua lon.
     *
     * <p>Tinh truoc so diem anh thay vi cu ve roi cho het bo nho: bao loi ro rang keo theo
     * cach xu ly bao gio cung hon la de JVM chet roi khoi dong lai.
     */
    private void guardSize(Sheet sheet, int dpi) {
        long widthPx = Units.mmToPixels(sheet.widthMm(), dpi);
        long heightPx = Units.mmToPixels(sheet.lengthMm(), dpi);
        double megapixels = (double) widthPx * heightPx / 1_000_000d;
        int limit = properties.tiff().maxMegapixels();

        if (megapixels > limit) {
            throw new ApiException(ErrorCode.TIFF_TOO_LARGE, String.format(
                    "Tam %d (%.0f x %.0f cm) o %d DPI se thanh %.0f trieu diem anh, "
                            + "vuot muc cho phep %d trieu. Hay giam do phan giai TIF, "
                            + "hoac dat chieu dai toi da moi file ngan lai roi ghep lai. "
                            + "Ban PDF khong vuong gioi han nay.",
                    sheet.index() + 1, sheet.widthMm() / 10, sheet.lengthMm() / 10,
                    dpi, megapixels, limit));
        }
    }

    /**
     * Nen TIFF bang LZW.
     *
     * <p>LZW khong mat du lieu - ban in khong duoc phep co nhieu nen quanh net ve, nen
     * khong dung duoc cac kieu nen mat du lieu nhu JPEG.
     */
    private byte[] encode(BufferedImage image, int dpi) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("tiff").next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionType("LZW");

            IIOMetadata metadata = writer.getDefaultImageMetadata(
                    javax.imageio.ImageTypeSpecifier.createFromRenderedImage(image), param);
            writeMetadata(metadata, dpi);

            writer.write(null, new IIOImage(image, null, metadata), param);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    /**
     * Ghi do phan giai va cach chia dai vao sieu du lieu cua file TIFF.
     *
     * <p><b>Thieu do phan giai la file nguy hiem.</b> Phan mem mo ra khong biet mot diem
     * anh ung voi bao nhieu milimet nen se doan - thuong la 72 DPI, tuc la tam 57 cm in
     * ra thanh 2,4 met. Loi nay chi lo ra khi giay da chay tren may, nen o day KHONG bat
     * roi ghi canh bao: ghi hong thi tu choi phat hanh file luon.
     *
     * <p>Cung nhung luon ho so mau sRGB vao day - xem {@link #iccField()}.
     *
     * <p>Phai dung {@link IIOMetadataNode} chu khong goi {@code Document.createElement}:
     * cay tra ve tu {@code getAsTree} la cay doc lap, khong gan voi Document nao, nen
     * {@code getOwnerDocument()} tra ve null.
     */
    private void writeMetadata(IIOMetadata metadata, int dpi) {
        try {
            String format = metadata.getNativeMetadataFormatName();
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
            IIOMetadataNode ifd = (IIOMetadataNode) root.getFirstChild();

            // 296 = ResolutionUnit, gia tri 2 = inch.
            ifd.appendChild(shortField(296, "ResolutionUnit", 2));
            // 282/283 = XResolution/YResolution, kieu RATIONAL "tu so/mau so".
            ifd.appendChild(rationalField(282, "XResolution", dpi));
            ifd.appendChild(rationalField(283, "YResolution", dpi));
            // 278 = RowsPerStrip. Mac dinh cua Java la 1 hang moi dai: mot anh 4.836 hang
            // thanh 4.836 dai, vua phinh bang tag vua lam LZW phai khoi dong lai moi hang
            // nen nen kem han.
            ifd.appendChild(longField(278, "RowsPerStrip", ROWS_PER_STRIP));
            // 34675 = ICCProfile.
            ifd.appendChild(iccField());

            metadata.mergeTree(format, root);
        } catch (Exception ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Khong ghi duoc do phan giai vao file TIF nen khong phat hanh file: "
                            + ex.getMessage() + ". File thieu do phan giai se in ra sai kich thuoc.");
        }
    }

    /**
     * Nhung ho so mau sRGB vao file.
     *
     * <p>Khong co ho so nay thi file chi noi duoc "day la RGB" ma khong noi la RGB NAO.
     * RIP se tu doan - thuong la sRGB, va thuong la doan dung. Nhung khi file goc cua
     * khach dung mot khong gian mau khac (AdobeRGB chang han) thi mau in ra lech di ma
     * khong ai biet tai sao, vi khong co cho nao bao loi.
     *
     * <p>Dung sRGB vi do dung la khong gian mau PDFBox ve ra. Nhung ho so nay chi la
     * GHI LAI dieu do cho ro rang, khong doi mot diem anh nao.
     */
    private static IIOMetadataNode iccField() {
        byte[] profile = ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData();

        StringBuilder bytes = new StringBuilder(profile.length * 4);
        for (int i = 0; i < profile.length; i++) {
            if (i > 0) {
                bytes.append(',');
            }
            bytes.append(profile[i] & 0xFF);
        }

        IIOMetadataNode field = field(34675, "ICC Profile");
        IIOMetadataNode data = new IIOMetadataNode("TIFFUndefined");
        data.setAttribute("value", bytes.toString());
        field.appendChild(data);
        return field;
    }

    private static IIOMetadataNode shortField(int number, String name, int value) {
        IIOMetadataNode field = field(number, name);
        IIOMetadataNode list = new IIOMetadataNode("TIFFShorts");
        IIOMetadataNode item = new IIOMetadataNode("TIFFShort");
        item.setAttribute("value", String.valueOf(value));
        list.appendChild(item);
        field.appendChild(list);
        return field;
    }

    private static IIOMetadataNode longField(int number, String name, int value) {
        IIOMetadataNode field = field(number, name);
        IIOMetadataNode list = new IIOMetadataNode("TIFFLongs");
        IIOMetadataNode item = new IIOMetadataNode("TIFFLong");
        item.setAttribute("value", String.valueOf(value));
        list.appendChild(item);
        field.appendChild(list);
        return field;
    }

    private static IIOMetadataNode rationalField(int number, String name, int value) {
        IIOMetadataNode field = field(number, name);
        IIOMetadataNode list = new IIOMetadataNode("TIFFRationals");
        IIOMetadataNode item = new IIOMetadataNode("TIFFRational");
        item.setAttribute("value", value + "/1");
        list.appendChild(item);
        field.appendChild(list);
        return field;
    }

    private static IIOMetadataNode field(int number, String name) {
        IIOMetadataNode field = new IIOMetadataNode("TIFFField");
        field.setAttribute("number", String.valueOf(number));
        field.setAttribute("name", name);
        return field;
    }

    /** Uoc luong bo nho mot tam se chiem, dung cho log va bai test. */
    public static long estimatedBytes(Sheet sheet, int dpi) {
        return Units.mmToPixels(sheet.widthMm(), dpi)
                * Units.mmToPixels(sheet.lengthMm(), dpi)
                * BYTES_PER_PIXEL;
    }
}
