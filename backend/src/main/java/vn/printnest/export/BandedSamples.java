package vn.printnest.export;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Vector;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Anh nam kenh dua cho bo ghi TIFF, giai nen tung DAI khi duoc hoi den.
 *
 * <p><b>Vi sao khong dua thang mot mang.</b> Mang nam kenh cua tam 57 x 100 cm o 300 DPI
 * la 397 MB. Nhung bo ghi TIFF cua Java khong doc ca anh mot luc: no hoi tung dai bang
 * {@code getData(rect)}, moi lan dung bang {@code RowsPerStrip} hang. Do that tren mot
 * anh 2.000 x 3.000: 375 lan hoi, khong lan nao doi ca anh.
 *
 * <p>Nen o day giu du lieu da NEN theo tung dai, va chi giai nen dai dang duoc hoi. Dinh
 * bo nho tut tu 397 MB xuong con mot dai - vai chuc MB - cong phan da nen, ma ban in
 * phan lon la giay trang nen no nen rat gon.
 *
 * <p>Giu lai dai vua giai nen: mot dai 1.024 hang phuc vu 16 lan hoi lien tiep neu moi
 * dai TIFF la 64 hang, khong giu thi giai nen lai 16 lan.
 */
final class BandedSamples implements RenderedImage {

    private final int width;
    private final int height;
    private final int bandRows;
    private final int stripRows;
    private final int bands;
    private final List<byte[]> compressed;
    private final ColorModel colorModel;

    private byte[] current;
    private int currentBand = -1;

    /**
     * @param compressed du lieu tung dai da nen bang Deflate, theo thu tu tu tren xuong
     * @param bandRows   so hang moi dai DA NEN; dai cuoi co the ngan hon
     * @param stripRows  so hang moi dai TIFF, tuc o luoi ma anh nay khai ra ngoai
     */
    BandedSamples(int width, int height, int bandRows, int stripRows, int bands,
                  List<byte[]> compressed, ColorModel colorModel) {
        this.width = width;
        this.height = height;
        this.bandRows = bandRows;
        this.stripRows = stripRows;
        this.bands = bands;
        this.compressed = compressed;
        this.colorModel = colorModel;
    }

    /**
     * Mot dai TIFF, CHEP RA mang rieng bat dau tu byte 0.
     *
     * <p><b>Khong duoc tra ve mot khung nhin co offset.</b> Cach hien nhien la tro thang
     * vao dai da giai nen bang {@code new DataBufferByte(data, size, offset)} - dung ve
     * mat API, nhung bo ghi TIFF cua Java lay {@code getDataBuffer().getData()} roi danh
     * chi so TU 0, tuc la BO QUA offset. Do that tren mot anh 64 x 128 chia hai dai: dai
     * thu hai ghi ra dung noi dung cua dai thu nhat, so diem co muc trang thanh 8.192
     * thay vi 4.096. File van mo binh thuong, chi la nua duoi lap lai nua tren.
     *
     * <p>Nen phai chep. Mot dai 64 hang o kho 57 cm la 2 MB - khong dang ke so voi 397 MB
     * ma cach nay tranh duoc.
     */
    @Override
    public Raster getData(Rectangle rect) {
        byte[] data = new byte[rect.width * rect.height * bands];
        int rowBytes = rect.width * bands;

        for (int y = 0; y < rect.height; y++) {
            int row = rect.y + y;
            int band = row / bandRows;
            byte[] source = inflate(band);
            int from = (row - band * bandRows) * width * bands + rect.x * bands;
            System.arraycopy(source, from, data, y * rowBytes, rowBytes);
        }

        int[] offsets = new int[bands];
        for (int i = 0; i < bands; i++) {
            offsets[i] = i;
        }
        SampleModel model = new PixelInterleavedSampleModel(
                DataBuffer.TYPE_BYTE, rect.width, rect.height, bands, rowBytes, offsets);
        return Raster.createRaster(model,
                new DataBufferByte(data, data.length), new Point(rect.x, rect.y));
    }

    private byte[] inflate(int band) {
        if (band == currentBand) {
            return current;
        }
        byte[] target = new byte[rowsIn(band) * width * bands];
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed.get(band));
            int at = 0;
            while (at < target.length) {
                int written = inflater.inflate(target, at, target.length - at);
                if (written == 0) {
                    throw new IllegalStateException(
                            "Dai " + band + " giai nen thieu: moi duoc " + at + "/" + target.length);
                }
                at += written;
            }
        } catch (DataFormatException ex) {
            throw new UncheckedIOException(new java.io.IOException(
                    "Hong du lieu dai " + band + ": " + ex.getMessage(), ex));
        } finally {
            inflater.end();
        }

        current = target;
        currentBand = band;
        return target;
    }

    private int rowsIn(int band) {
        return Math.min(bandRows, height - band * bandRows);
    }

    // ------------------------------------------------------------------
    // Phan con lai cua giao dien: khai bao hinh dang anh, khong giu du lieu nao.

    @Override
    public Vector<RenderedImage> getSources() {
        return null;
    }

    @Override
    public Object getProperty(String name) {
        return java.awt.Image.UndefinedProperty;
    }

    @Override
    public String[] getPropertyNames() {
        return null;
    }

    @Override
    public ColorModel getColorModel() {
        return colorModel;
    }

    @Override
    public SampleModel getSampleModel() {
        int[] offsets = new int[bands];
        for (int i = 0; i < bands; i++) {
            offsets[i] = i;
        }
        return new PixelInterleavedSampleModel(
                DataBuffer.TYPE_BYTE, width, height, bands, width * bands, offsets);
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getMinX() {
        return 0;
    }

    @Override
    public int getMinY() {
        return 0;
    }

    @Override
    public int getNumXTiles() {
        return 1;
    }

    @Override
    public int getNumYTiles() {
        return (height + stripRows - 1) / stripRows;
    }

    @Override
    public int getMinTileX() {
        return 0;
    }

    @Override
    public int getMinTileY() {
        return 0;
    }

    @Override
    public int getTileWidth() {
        return width;
    }

    @Override
    public int getTileHeight() {
        return stripRows;
    }

    @Override
    public int getTileGridXOffset() {
        return 0;
    }

    @Override
    public int getTileGridYOffset() {
        return 0;
    }

    @Override
    public Raster getTile(int tileX, int tileY) {
        int first = tileY * stripRows;
        return getData(new Rectangle(0, first, width, Math.min(stripRows, height - first)));
    }

    /**
     * Ca anh mot luc - dung cai ma lop nay sinh ra de tranh.
     *
     * <p>Bao loi chu khong lang le cap phat 397 MB: im lang o day la quay ve dung cai loi
     * vua sua, va chi lo ra tren may chu that chu khong phai trong bai test.
     */
    @Override
    public Raster getData() {
        throw new IllegalStateException(
                "Bo ghi doi ca anh mot luc - dung cai ma cach ghi theo dai tranh. "
                        + "Kiem tra lai RowsPerStrip va kieu nen.");
    }

    @Override
    public WritableRaster copyData(WritableRaster raster) {
        throw new IllegalStateException("Khong ho tro chep ca anh - xem getData().");
    }
}
