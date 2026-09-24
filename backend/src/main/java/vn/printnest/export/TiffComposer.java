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
import javax.imageio.plugins.tiff.BaselineTIFFTagSet;
import javax.imageio.plugins.tiff.TIFFDirectory;
import javax.imageio.plugins.tiff.TIFFField;
import javax.imageio.plugins.tiff.TIFFTag;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;


import java.awt.image.WritableRaster;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.Deflater;
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
 * 11.811 diem = 318 MB rieng anh da ve, va do la anh CHUA nen. Nen duong CMYK kem kenh
 * muc trang ve theo TUNG DAI - xem {@link #encodeWithWhiteBanded}. Ngoai ra con mot tran
 * do theo heap that: vuot qua thi bao loi ro rang chu khong de he dieu hanh giet tien
 * trinh, mat sach viec cua nguoi khac dang lam do.
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

    /** Tien to bao ho so nam trong resources chu khong phai tren dia. */
    private static final String CLASSPATH_PREFIX = "classpath:";

    /** So kenh cua anh CMYK, cua anh CMYK co them kenh muc trang, va thu tu kenh do. */
    private static final int CMYK_BANDS = 4;
    private static final int CMYK_WHITE_BANDS = 5;
    private static final int SPOT_BAND = 4;

    /** So hieu cac tag TIFF duoc ghi tay. */
    private static final int TAG_ROWS_PER_STRIP = 278;
    private static final int TAG_X_RESOLUTION = 282;
    private static final int TAG_Y_RESOLUTION = 283;
    private static final int TAG_RESOLUTION_UNIT = 296;
    private static final int TAG_EXTRA_SAMPLES = 338;
    private static final int TAG_PHOTOSHOP = 34377;
    private static final int TAG_ICC_PROFILE = 34675;
    private static final int TAG_IMAGE_SOURCE_DATA = 37724;

    /** Ten lop hien trong bang Layers, dat giong file mau. */
    private static final String LAYER_NAME = "Layer 1";

    /**
     * Gia tri tag 338 noi "kenh phu nay khong phai lop trong suot".
     *
     * <p>File mau cua tho ghi 0 o day. Bo ghi cua Java tu dat 2 (kenh alpha roi) vi anh
     * nam kenh trong bo nho buoc phai khai bao kenh thu nam la alpha; de nguyen thi phan
     * mem doc file co the hieu kenh muc trang la do trong suot va nhan chim lop mau.
     */
    private static final int EXTRA_SAMPLES_UNSPECIFIED = 0;

    private final PdfComposer pdfComposer;
    private final AppProperties properties;

    /** Ho so CMYK da nap, giu lai de khong phai doc file moi lan tai. */
    private final AtomicReference<ICC_Profile> cmykCache = new AtomicReference<>();

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

        boolean white = wantsWhiteChannel();

        try (PDDocument document = Loader.loadPDF(pdf)) {
            byte[] tiff;
            int width;
            int height;

            if (white) {
                // Duong DAI: khong bao gio ve ca tam ra bo nho - xem encodeWithWhiteBanded.
                Banded banded = encodeWithWhiteBanded(document, sheet, dpi, cmykProfile());
                tiff = banded.tiff();
                width = banded.width();
                height = banded.height();
            } else {
                // Khong co kenh muc trang thi ban in khong co khai niem trong suot, nen ve
                // RGB - bot mot phan tu dung luong trong bo nho.
                BufferedImage image = new PDFRenderer(document)
                        .renderImageWithDPI(0, dpi, ImageType.RGB);
                width = image.getWidth();
                height = image.getHeight();

                if (!properties.tiff().cmyk()) {
                    tiff = encode(image, dpi, srgbProfile(), null, null);
                } else {
                    ICC_Profile cmyk = cmykProfile();
                    BufferedImage converted = toCmyk(image, cmyk);
                    // Tra lai anh RGB NGAY: o 300 DPI no chiem 318 MB, giu them mot nhip
                    // la dinh bo nho gap doi trong luc nen file.
                    image.flush();
                    tiff = encode(converted, dpi, cmyk, null, null);
                    converted.flush();
                }
                image.flush();
            }

            log.info("Da dung TIFF tam {}: {} x {} diem o {} DPI{}, {} MB, mat {} ms",
                    sheet.index() + 1, width, height, dpi,
                    white ? " kem kenh " + properties.tiff().white().channelName() : "",
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

        // Dinh bo nho tinh theo so byte moi diem anh. Dem tung mang con song cung luc:
        //
        //   RGB           4        anh da ve
        //   CMYK          4 + 4    anh da ve va anh CMYK cung ton tai luc chuyen doi
        //   CMYK + trang  3        vung phu 1 + mat na da co 1, cong 1 cho phan da nen
        //                          giu lai va cho file dau ra
        //
        // Duong CMYK + trang ve theo DAI nen ba mang co ca tam do la tat ca - anh da ve,
        // anh CMYK va mang nam kenh deu chi ton mot dai. Truoc khi ve theo dai, con so
        // nay la 11 byte moi diem: tam 57 x 100 cm o 300 DPI can 875 MB va may chu 2 GB
        // bi he dieu hanh giet.
        //
        // Day la mang CON SONG. Mang da flush nhung GC chua kip don con nam them mot
        // nhip nua, va bo nho NGOAI heap cua ImageIO/ICC/PDFBox thi khong tinh o day -
        // nen he so nay la can duoi, khong phai con so an toan.
        boolean banded = properties.tiff().cmyk() && wantsWhiteChannel();
        long perPixel = properties.tiff().cmyk() ? (banded ? 3 : 8) : 4;
        long needBytes = widthPx * heightPx * perPixel;
        if (banded) {
            // Cong phan cua MOT dai: anh da ve 4 + anh CMYK 4 + mang nam kenh 5.
            needBytes += (long) bandRows((int) heightPx) * widthPx * 13;
        }
        long allowedBytes = properties.tiff().maxBytesPerSheet();

        if (needBytes > allowedBytes) {
            // Do phan giai lon nhat con vua: bo nho tang theo BINH PHUONG DPI nen rut can
            // bac hai. Lam tron XUONG boi 10 - lam tron len thi con so goi y vuot chinh
            // muc cho phep, tho lam theo van bi tu choi lan nua.
            long fits = (long) Math.floor(dpi * Math.sqrt((double) allowedBytes / needBytes) / 10) * 10;
            throw new ApiException(ErrorCode.TIFF_TOO_LARGE, String.format(
                    "Tam %d (%.0f x %.0f cm) o %d DPI can khoang %d MB bo nho, ma may chu "
                            + "nay chi danh duoc %d MB cho moi tam (heap %d MB). "
                            + "Hay dat APP_TIFF_DPI xuong khoang %d, hoac dat chieu dai toi da "
                            + "moi file ngan lai roi ghep lai, hoac nang RAM may chu len. "
                            + "Ban PDF va ban cat khong vuong gioi han nay.",
                    sheet.index() + 1, sheet.widthMm() / 10, sheet.lengthMm() / 10, dpi,
                    needBytes / (1024 * 1024), allowedBytes / (1024 * 1024),
                    Runtime.getRuntime().maxMemory() / (1024 * 1024),
                    Math.max(fits, 10)));
        }

        log.debug("Tam {} o {} DPI: {} trieu diem, dinh bo nho khoang {} MB tren muc cho "
                        + "phep {} MB",
                sheet.index() + 1, dpi, Math.round(megapixels),
                needBytes / (1024 * 1024), allowedBytes / (1024 * 1024));
    }

    /**
     * Co them kenh muc trang vao file khong.
     *
     * <p>Chi them khi dang xuat CMYK. Kenh muc rieng la khai niem cua tai lieu CMYK -
     * nhet no vao file RGB thi phan mem doc ra khong hieu, ma che do RGB cung khong phai
     * che do xuong dang dung.
     */
    private boolean wantsWhiteChannel() {
        AppProperties.Tiff.White white = properties.tiff().white();
        return white != null && white.enabled() && properties.tiff().cmyk();
    }

    /**
     * Xuat TIFF CMYK kem mot kenh muc trang cho in DTF.
     *
     * <p>Bon kenh CMYK dau van y het duong cu - phan xuat mau khong bi dong den mot dong
     * nao. Kenh thu nam la THEM vao.
     *
     * <p><b>Bo nho la cho phai can nhac nhat.</b> Mot tam 57 x 100 cm o 300 DPI la 79
     * trieu diem. Neu dung anh CMYK rieng roi chep sang anh nam kenh thi trong luc chep
     * ca hai cung ton tai: 318 MB + 397 MB.
     *
     * <p><b>Da thu cach gon hon va no HONG.</b> Y tuong la chi cap phat MOT vung nho nam
     * kenh roi muon lai chinh vung do duoi dang anh bon kenh, de {@link ColorConvertOp}
     * ghi thang vao khoi phai chep. Nhung {@code ColorConvertOp} KHONG ton trong buoc nhay
     * giua hai diem anh: dat buoc nhay 5 thi no van ghi don 4 byte lien nhau, nen chi lap
     * duoc dung 4/5 vung nho con lai la rac. Do tren anh mot mau do dac: 165.376 tren
     * 206.720 diem co muc - dung 80%, va anh ra soc ngang do trang xen ke. Vi vay o day
     * chep that. Ai dinh toi uu lai cho nay xin doc {@code TiffWhiteChannelIntegrationTest}
     * truoc.
     */
    /**
     * Dung ban CMYK kem kenh muc trang, ve theo TUNG DAI.
     *
     * <p>Ban cu giu bon mang co ca tam cung mot luc - anh da ve, anh CMYK, mang nam kenh
     * va hai mat na - tong 11 byte moi diem anh. Voi tam 57 x 100 cm o 300 DPI la 875 MB,
     * qua suc may chu 2 GB: tien trinh bi he dieu hanh giet, khong phai JVM nem
     * {@code OutOfMemoryError} nen khong cho nao bat duoc.
     *
     * <p>Ban nay chi giu hai mat na co ca tam (2 byte moi diem), con lai deu theo dai:
     *
     * <ol>
     *   <li><b>Luot 1</b> ve tung dai, phan loai diem anh vao mat na. Phep loang tu mep
     *       nhin ca tam nen phai doi het cac dai roi moi chay - xem
     *       {@link WhiteChannel#finish}.</li>
     *   <li><b>Luot 2</b> ve lai tung dai, chuyen CMYK, ghep nam kenh, nap vao lop
     *       Photoshop, roi NEN dai do va giu lai. Ban in phan lon la giay trang nen phan
     *       da nen rat gon.</li>
     *   <li><b>Ghi</b> dua cho bo ghi TIFF mot anh luoi: no hoi tung dai, ta giai nen dung
     *       dai do ra - xem {@link BandedSamples}.</li>
     * </ol>
     *
     * <p>Ve hai luot nghe phi nhung ve chi chiem mot phan nho thoi gian; phan ton nhat la
     * NEN file. Doi lai bo nho khong con phu thuoc chieu dai tam.
     */
    private Banded encodeWithWhiteBanded(PDDocument document, Sheet sheet, int dpi,
                                         ICC_Profile profile) throws IOException {
        AppProperties.Tiff.White settings = properties.tiff().white();
        SheetBands bands = SheetBands.of(document, dpi);
        int width = bands.width();
        int height = bands.height();
        int bandRows = bandRows(height);

        // MOT luot ve duy nhat. Moi dai di mot mach: phan loai diem anh -> lam phang len
        // nen trang -> chuyen CMYK -> nen lai giu day.
        //
        // Ban truoc ve HAI luot, mot de dung mat na va mot de lay mau. Do tren don that
        // cua xuong: luot dung mat na ngon 8-12 giay moi tam, khoang 35% tong thoi gian.
        // Gop lai duoc vi mau khong phu thuoc mat na - chi kenh muc trang moi phu thuoc,
        // ma kenh do duoc ghep vao luc GHI file, khong phai luc nay.
        long start = System.currentTimeMillis();
        byte[] coverage = new byte[width * height];
        List<byte[]> packed = new ArrayList<>((height + bandRows - 1) / bandRows);

        for (int row = 0; row < height; row += bandRows) {
            int rows = Math.min(bandRows, height - row);
            BufferedImage band = bands.render(row, rows);

            WhiteChannel.classify(band, coverage, width, row,
                    settings.alphaThreshold(), settings.whiteTolerance());
            // Phai lam phang SAU khi phan loai (buoc tren can kenh alpha) va TRUOC khi
            // chuyen mau.
            flattenOntoWhite(band);

            BufferedImage cmyk = toCmykQuiet(band, profile);
            band.flush();
            byte[] colour = ((DataBufferByte) cmyk.getRaster().getDataBuffer()).getData();
            packed.add(deflate(colour, rows * width * CMYK_BANDS));
            cmyk.flush();
        }

        int packedBytes = packed.stream().mapToInt(part -> part.length).sum();
        log.info("Da ve va chuyen mau {} dai {} hang trong {} ms, giu lai {} MB da nen",
                packed.size(), bandRows, System.currentTimeMillis() - start,
                packedBytes / (1024 * 1024));

        // Loang nen tu mep tam. PHAI lam sau khi MOI dai da phan loai xong: duong loang
        // di xuyen qua ranh gioi cac dai, lam som mot dai nao do la cat cut no.
        long maskStart = System.currentTimeMillis();
        WhiteChannel.finish(coverage, width, height,
                pdfComposer.imageZones(sheet, dpi, height));

        // Vung phu dung hai viec, va hai viec do can hai ban KHAC nhau: kenh do trong suot
        // lay ban chua co (lop mau trai het ra mep), kenh muc trang lay ban da co vao.
        byte[] mask = WhiteChannel.choke(coverage, width, height, settings.chokePixels());
        log.info("Da dung mat na kenh {} trong {} ms (co vao {} diem)",
                settings.channelName(), System.currentTimeMillis() - maskStart,
                settings.chokePixels());

        byte[] layers = null;
        if (properties.tiff().transparentLayer()) {
            long layerStart = System.currentTimeMillis();
            PhotoshopLayers.Builder builder =
                    new PhotoshopLayers.Builder(width, height, properties.tiff().layerZip());
            // Doc lai tu phan da nen chu khong ve lai: giai nen nhanh hon ve nhieu.
            for (int band = 0; band < packed.size(); band++) {
                int first = band * bandRows;
                int rows = Math.min(bandRows, height - first);
                byte[] colour = BandedSamples.inflate(packed.get(band),
                        rows * width * CMYK_BANDS);
                for (int y = 0; y < rows; y++) {
                    builder.addRow(colour, y * width * CMYK_BANDS, CMYK_BANDS,
                            coverage, (first + y) * width);
                }
            }
            layers = builder.finish(LAYER_NAME);
            log.info("Da dung lop trong suot: {} MB, mat {} ms",
                    layers.length / (1024 * 1024), System.currentTimeMillis() - layerStart);
        }

        BandedSamples image = new BandedSamples(width, height, bandRows, ROWS_PER_STRIP,
                CMYK_BANDS, packed, mask, spotColorModel(profile));
        byte[] tiff = encode(image, dpi, profile,
                PhotoshopResources.spotChannel(settings.channelName()), layers);
        forceExtraSamplesUnspecified(tiff);
        return new Banded(tiff, width, height);
    }

    /** Ket qua duong dai: file, va kich thuoc THAT ma PDFBox ve ra. */
    private record Banded(byte[] tiff, int width, int height) {
    }

    /**
     * So hang moi dai, lam tron LEN thanh boi so cua {@code ROWS_PER_STRIP}.
     *
     * <p>Phai la boi so thi moi dai TIFF ma bo ghi hoi moi nam gon trong mot dai cua ta -
     * khong thi lan nao cung phai ghep tu hai dai, cham han.
     */
    private int bandRows(int height) {
        int rows = Math.max(properties.tiff().bandRows(), ROWS_PER_STRIP);
        rows = (rows + ROWS_PER_STRIP - 1) / ROWS_PER_STRIP * ROWS_PER_STRIP;
        return Math.min(rows, height);
    }

    /** Nen mot dai bang Deflate de giu lai cho luc ghi file. */
    private static byte[] deflate(byte[] source, int length) {
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try {
            deflater.setInput(source, 0, length);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(length / 8);
            byte[] chunk = new byte[1 << 16];
            while (!deflater.finished()) {
                out.write(chunk, 0, deflater.deflate(chunk));
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    /** Ghep bon kenh mau va phan mat na cua MOT DAI vao mang nam kenh dung chung. */
    private static void interleaveBand(BufferedImage cmyk, byte[] mask, int maskFrom,
                                       int pixels, byte[] samples) {
        byte[] colour = ((DataBufferByte) cmyk.getRaster().getDataBuffer()).getData();
        for (int i = 0; i < pixels; i++) {
            System.arraycopy(colour, i * CMYK_BANDS, samples, i * CMYK_WHITE_BANDS, CMYK_BANDS);
        }
        WhiteChannel.writeSpotBand(mask, maskFrom, pixels, samples, SPOT_BAND, CMYK_WHITE_BANDS);
    }

    /** Nhu {@link #toCmyk} nhung khong ghi log: duong dai goi no hang chuc lan. */
    private BufferedImage toCmykQuiet(BufferedImage source, ICC_Profile profile) {
        ICC_ColorSpace space = new ICC_ColorSpace(profile);
        ComponentColorModel model = new ComponentColorModel(
                space, false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
        WritableRaster raster = model.createCompatibleWritableRaster(
                source.getWidth(), source.getHeight());
        BufferedImage target = new BufferedImage(model, raster, false, null);
        new ColorConvertOp(null).filter(source, target);
        return target;
    }

    /** Mo hinh mau cua anh nam kenh - tach rieng de {@link BandedSamples} dung chung. */
    private static ComponentColorModel spotColorModel(ICC_Profile profile) {
        // Phai khai bao kenh thu nam la alpha thi Java moi chiu ghi anh nam kenh. Tag 338
        // duoc sua lai ngay sau khi ghi - xem forceExtraSamplesUnspecified.
        return new ComponentColorModel(
                new ICC_ColorSpace(profile), true, false,
                Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE);
    }

    /**
     * Dat anh len nen trang, bo han kenh trong suot.
     *
     * <p><b>Khong duoc giao viec nay cho {@link ColorConvertOp}.</b> Do that tren chinh
     * may nay: cung mot diem {@code 0x00000000} (den, trong suot hoan toan), doi vao anh
     * 1x1 thi ra CMYK {@code 215 192 153 255} - DEN DAC - con doi vao anh 8x4 thi ra
     * {@code 0 0 0 0}. No chay hai duong khac nhau tuy kich thuoc anh. Duong ra den dac
     * nghia la toan bo khoang trong quanh hinh bi do day muc den.
     *
     * <p>Lam phang truoc thi moi diem deu dac, {@code ColorConvertOp} khong con cho nao
     * de xu ly khac nhau nua. Nen trang la dung: cho khong co hinh thi may khong phun gi,
     * ma khong phun gi tren phim trong chinh la ra CMYK {@code 0 0 0 0}.
     *
     * <p>Vien rang cua o mep hinh cung duoc hoa dung ty le sang nen trang, nen net ve
     * khong bi gai.
     */
    private static void flattenOntoWhite(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] row = new int[width];

        for (int y = 0; y < height; y++) {
            image.getRGB(0, y, width, 1, row, 0, width);
            boolean touched = false;

            for (int x = 0; x < width; x++) {
                int argb = row[x];
                int alpha = argb >>> 24;
                if (alpha == 255) {
                    continue;
                }
                touched = true;
                int rest = 255 - alpha;
                row[x] = 0xFF000000
                        | (((((argb >> 16) & 0xFF) * alpha + 255 * rest) / 255) << 16)
                        | (((((argb >> 8) & 0xFF) * alpha + 255 * rest) / 255) << 8)
                        | ((argb & 0xFF) * alpha + 255 * rest) / 255;
            }

            if (touched) {
                image.setRGB(0, y, width, 1, row, 0, width);
            }
        }
    }


    /**
     * Sua tag 338 trong file da ghi ve 0.
     *
     * <p><b>Vi sao phai sua sau khi ghi.</b> Java chi chiu ghi anh nam kenh neu mo hinh
     * mau khai bao kenh thu nam la alpha, va bo ghi TIFF tu suy tag 338 ra tu do - dat lai
     * qua sieu du lieu khong an thua, no van ghi de. File mau cua tho ghi 0, nen o day
     * vao sua thang hai byte trong bang tag.
     *
     * <p>Khong tim thay tag thi bao loi chu khong bo qua: dinh dang file da khac voi cai
     * ham nay tung doc duoc, im lang di la phat hanh mot file khong biet no dang the nao.
     */
    private static void forceExtraSamplesUnspecified(byte[] tiff) {
        ByteBuffer buffer = ByteBuffer.wrap(tiff)
                .order(tiff[0] == 'I' ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        int ifd = buffer.getInt(4);
        int count = buffer.getShort(ifd) & 0xFFFF;

        for (int i = 0; i < count; i++) {
            int entry = ifd + 2 + i * 12;
            if ((buffer.getShort(entry) & 0xFFFF) != TAG_EXTRA_SAMPLES) {
                continue;
            }
            // Kieu SHORT va chi mot gia tri, nen no nam ngay trong bang tag chu khong
            // phai o mot cho khac trong file.
            buffer.putShort(entry + 8, (short) EXTRA_SAMPLES_UNSPECIFIED);
            return;
        }

        throw new ApiException(ErrorCode.INTERNAL_ERROR,
                "Khong tim thay tag ExtraSamples trong file TIF vua ghi nen khong phat hanh file: "
                        + "kenh muc trang co the bi hieu nham la lop trong suot.");
    }

    /**
     * Nen TIFF, kieu nen lay tu cau hinh.
     *
     * <p>Chi dung cac kieu KHONG MAT DU LIEU - ban in khong duoc phep co nhieu quanh net
     * ve, nen tuyet doi khong dung JPEG trong TIFF.
     *
     * <p>Day la chang ton thoi gian nhat cua ca quy trinh: do tren mot tam 57 x 100 cm o
     * 300 DPI thi ve het 210 ms, chuyen mau 1 giay, con nen het 3,9 giay - tuc 76%.
     */
    private byte[] encode(java.awt.image.RenderedImage image, int dpi, ICC_Profile profile,
                          byte[] photoshop, byte[] layers) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("tiff").next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionType(properties.tiff().compression());
            // Muc nen: so nho la nhanh va file to hon. Nen khong mat du lieu nen day
            // thuan tuy la danh doi thoi gian lay dung luong, khong dung toi chat luong.
            param.setCompressionQuality((float) properties.tiff().compressionQuality());

            IIOMetadata metadata = writer.getDefaultImageMetadata(
                    javax.imageio.ImageTypeSpecifier.createFromRenderedImage(image), param);
            metadata = writeMetadata(metadata, dpi, profile, photoshop, layers);

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
     * <p>Cung nhung luon ho so mau vao day. Khong co no thi file chi noi duoc "day la
     * RGB" hay "day la CMYK" ma khong noi la RGB NAO, CMYK NAO - RIP phai tu doan, va
     * doan sai la lech mau ma khong co cho nao bao loi. O che do RGB, ho so nhung vao la
     * sRGB (dung khong gian PDFBox ve ra, nen chi GHI LAI dieu von da dung chu khong doi
     * mot diem anh nao); o che do CMYK la chinh ho so da dung de chuyen doi.
     *
     * <p>Dung {@link TIFFDirectory} chu khong di duong cay sieu du lieu. Duong cay bat
     * moi gia tri phai qua CHUOI, nen mot khoi byte 20 MB thanh chuoi 71 trieu ky tu
     * chiem 136 MB bo nho - khoi du lieu lop thua suc dat nguong do. No con hay o cho
     * {@code TIFFDirectory} tra ve sieu du lieu MOI thay vi vua sua vua tron cay cu.
     */
    private IIOMetadata writeMetadata(IIOMetadata metadata, int dpi, ICC_Profile profile,
                                      byte[] photoshop, byte[] layers) {
        try {
            TIFFDirectory directory = TIFFDirectory.createFromMetadata(metadata);

            // ResolutionUnit, gia tri 2 = inch.
            directory.addTIFFField(shortField(TAG_RESOLUTION_UNIT, 2));
            // XResolution/YResolution, kieu RATIONAL "tu so/mau so".
            directory.addTIFFField(rationalField(TAG_X_RESOLUTION, dpi));
            directory.addTIFFField(rationalField(TAG_Y_RESOLUTION, dpi));
            // RowsPerStrip. Mac dinh cua Java la 1 hang moi dai: mot anh 4.836 hang thanh
            // 4.836 dai, vua phinh bang tag vua lam nen kem han.
            directory.addTIFFField(longField(TAG_ROWS_PER_STRIP, ROWS_PER_STRIP));
            directory.addTIFFField(bytesField(TAG_ICC_PROFILE, "ICC Profile", profile.getData()));

            // Khoi mo ta kenh muc rieng. Thieu no thi file van du nam kenh nhung mo ra chi
            // thay "Alpha 1", va may in khong biet kenh do la muc trang.
            if (photoshop != null) {
                directory.addTIFFField(bytesField(TAG_PHOTOSHOP, "Photoshop", photoshop));
            }
            // Lop mang theo do trong suot, de mo ra thay o caro chu khong phai nen trang.
            if (layers != null) {
                directory.addTIFFField(bytesField(TAG_IMAGE_SOURCE_DATA, "ImageSourceData", layers));
            }

            return directory.getAsMetadata();
        } catch (Exception ex) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Khong ghi duoc do phan giai vao file TIF nen khong phat hanh file: "
                            + ex.getMessage() + ". File thieu do phan giai se in ra sai kich thuoc.");
        }
    }

    /**
     * Chuyen anh RGB sang CMYK bang ho so ICC.
     *
     * <p>Dung {@link ColorConvertOp} chu KHONG dung cong thuc tay kieu
     * {@code K = 1 - max(R,G,B)}. Cong thuc tay chay nhanh va nhin qua thi "ra CMYK",
     * nhung no bo qua toan bo dac tinh muc va giay ma ho so ICC mo ta - mau in ra lech
     * thay ro, va lech khong deu nen khong the bu lai bang cach chinh tay.
     *
     * <p>Buoc nay ngon bo nho: anh RGB va anh CMYK cung ton tai mot luc. O 300 DPI voi
     * tam 57 x 100 cm la 318 MB + 318 MB. Vi vay {@code guardSize} phai tinh ca hai.
     */
    private BufferedImage toCmyk(BufferedImage source, ICC_Profile profile) {
        ICC_ColorSpace space = new ICC_ColorSpace(profile);
        ComponentColorModel model = new ComponentColorModel(
                space, false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
        WritableRaster raster = model.createCompatibleWritableRaster(
                source.getWidth(), source.getHeight());
        BufferedImage target = new BufferedImage(model, raster, false, null);

        long start = System.currentTimeMillis();
        new ColorConvertOp(null).filter(source, target);
        log.info("Da chuyen sang CMYK trong {} ms", System.currentTimeMillis() - start);
        return target;
    }

    /**
     * Ho so CMYK dich, doc tu cau hinh va giu lai sau lan dau.
     *
     * <p>Doc file ICC moi lan tai file la phi - ho so vai nghin byte nhung phai phan tich
     * lai tu dau. Giu lai mot ban dung chung cho moi yeu cau.
     */
    private ICC_Profile cmykProfile() {
        ICC_Profile cached = cmykCache.get();
        if (cached != null) {
            return cached;
        }

        String location = properties.tiff().cmykProfile();
        if (location == null || location.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Dang dat che do mau CMYK nhung chua khai bao ho so ICC. "
                            + "Xin file .icc cua may in tu nha cung cap RIP roi dat duong dan "
                            + "vao APP_TIFF_CMYK_PROFILE, hoac doi APP_TIFF_COLOR_MODE ve rgb.");
        }

        try (InputStream in = open(location)) {
            ICC_Profile profile = ICC_Profile.getInstance(in);
            if (profile.getColorSpaceType() != ColorSpace.TYPE_CMYK) {
                throw new ApiException(ErrorCode.INVALID_REQUEST,
                        "File \"" + location + "\" khong phai ho so CMYK "
                                + "(no co " + profile.getNumComponents() + " kenh mau).");
            }
            log.info("Da nap ho so CMYK tu {} ({} kenh, {} byte)",
                    location, profile.getNumComponents(), profile.getData().length);
            cmykCache.set(profile);
            return profile;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Khong doc duoc ho so ICC \"" + location + "\": " + ex.getMessage());
        }
    }

    /** Ho so nam trong resources ({@code classpath:...}) hay tren dia. */
    private InputStream open(String location) throws IOException {
        if (location.startsWith(CLASSPATH_PREFIX)) {
            String name = location.substring(CLASSPATH_PREFIX.length());
            InputStream in = TiffComposer.class.getClassLoader().getResourceAsStream(name);
            if (in == null) {
                throw new IOException("khong tim thay trong resources");
            }
            return in;
        }
        return Files.newInputStream(Path.of(location));
    }

    private static ICC_Profile srgbProfile() {
        return ICC_Profile.getInstance(ColorSpace.CS_sRGB);
    }

    /**
     * Mot tag chua byte tho - ho so ICC, khoi Photoshop, du lieu lop.
     *
     * <p>Phai dung {@link TIFFField} chu khong di duong cay sieu du lieu: duong do nhan
     * gia tri qua CHUOI SO cach nhau bang dau phay, nen mot khoi 20 MB thanh chuoi 71
     * trieu ky tu chiem 136 MB bo nho. Khoi du lieu lop de dang co co do.
     */
    private static TIFFField bytesField(int number, String name, byte[] raw) {
        TIFFTag tag = new TIFFTag(name, number, 1 << TIFFTag.TIFF_UNDEFINED);
        return new TIFFField(tag, TIFFTag.TIFF_UNDEFINED, raw.length, raw);
    }

    private static TIFFField shortField(int number, int value) {
        return new TIFFField(baseline(number), TIFFTag.TIFF_SHORT, 1, new char[]{(char) value});
    }

    private static TIFFField longField(int number, int value) {
        return new TIFFField(baseline(number), TIFFTag.TIFF_LONG, 1, new long[]{value});
    }

    /** Kieu RATIONAL la mot cap "tu so, mau so". */
    private static TIFFField rationalField(int number, int value) {
        return new TIFFField(baseline(number), TIFFTag.TIFF_RATIONAL, 1,
                new long[][]{{value, 1}});
    }

    private static TIFFTag baseline(int number) {
        return BaselineTIFFTagSet.getInstance().getTag(number);
    }

    /** Uoc luong bo nho mot tam se chiem, dung cho log va bai test. */
    public static long estimatedBytes(Sheet sheet, int dpi) {
        return Units.mmToPixels(sheet.widthMm(), dpi)
                * Units.mmToPixels(sheet.lengthMm(), dpi)
                * BYTES_PER_PIXEL;
    }
}
