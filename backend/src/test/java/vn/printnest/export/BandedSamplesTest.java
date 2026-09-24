package vn.printnest.export;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Anh nam kenh giai nen theo dai phai tra ve DUNG diem anh da nap vao.
 *
 * <p>Sai mot ly o day thi file van mo duoc, van du nam kenh, chi la mau va kenh muc trang
 * lech di - ma kenh muc trang ghi NGUOC (0 moi la co muc) nen mot vung bi tra ve toan so 0
 * se thanh "lot trang kin", may phun trang day ra ngoai hinh.
 */
class BandedSamplesTest {

    private static final int BANDS = 5;

    /** Gia tri dat truoc cho moi diem, de doi chieu duoc tung byte. */
    private static byte value(int x, int y, int band) {
        return (byte) ((x * 7 + y * 13 + band * 31) & 0xFF);
    }

    private static BandedSamples build(int width, int height, int bandRows) {
        List<byte[]> packed = new ArrayList<>();
        for (int first = 0; first < height; first += bandRows) {
            int rows = Math.min(bandRows, height - first);
            byte[] raw = new byte[rows * width * BANDS];
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < width; x++) {
                    for (int b = 0; b < BANDS; b++) {
                        raw[(y * width + x) * BANDS + b] = value(x, first + y, b);
                    }
                }
            }
            packed.add(deflate(raw));
        }

        ComponentColorModel model = new ComponentColorModel(
                java.awt.color.ColorSpace.getInstance(java.awt.color.ColorSpace.CS_sRGB),
                new int[]{8, 8, 8, 8, 8}, true, false,
                java.awt.Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE);
        return new BandedSamples(width, height, bandRows, 4, BANDS, packed, model);
    }

    private static byte[] deflate(byte[] source) {
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(source);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        while (!deflater.finished()) {
            out.write(chunk, 0, deflater.deflate(chunk));
        }
        deflater.end();
        return out.toByteArray();
    }

    @Test
    @DisplayName("Doc theo tung dai TIFF: moi diem dung gia tri da nap")
    void everyStripReadsBackExactly() {
        int width = 37;
        int height = 100;
        int bandRows = 16;
        BandedSamples image = build(width, height, bandRows);

        // Bo ghi hoi tung dai TIFF mot; dai TIFF khong nhat thiet bang dai cua ta.
        for (int stripRows : new int[]{1, 4, 16, 64}) {
            for (int y = 0; y < height; y += stripRows) {
                int rows = Math.min(stripRows, height - y);
                Raster raster = image.getData(new Rectangle(0, y, width, rows));

                // Bo ghi TIFF cua Java lay thang mang cua DataBuffer roi danh chi so TU 0,
                // bo qua offset. Nen mang tra ve phai la mang RIENG cua dai nay.
                byte[] buffer = ((java.awt.image.DataBufferByte) raster.getDataBuffer()).getData();
                assertThat(buffer.length)
                        .as("dai %d hang tai y=%d phai la mang rieng, khong phai khung nhin",
                                stripRows, y)
                        .isEqualTo(rows * width * BANDS);
                assertThat(buffer[4])
                        .as("byte dau cua mang phai la diem dau cua dai, khong phai cua tam")
                        .isEqualTo(value(0, y, 4));

                for (int ry = 0; ry < rows; ry++) {
                    for (int x = 0; x < width; x++) {
                        for (int b = 0; b < BANDS; b++) {
                            assertThat((byte) raster.getSample(x, y + ry, b))
                                    .as("dai %d hang: diem (%d, %d) kenh %d",
                                            stripRows, x, y + ry, b)
                                    .isEqualTo(value(x, y + ry, b));
                        }
                    }
                }
            }
        }
    }

    /**
     * Dai cuoi thuong ngan hon cac dai kia - cho de sai nhat.
     */
    @Test
    @DisplayName("Dai cuoi ngan hon van doc dung")
    void theShortLastBandReadsBackExactly() {
        int width = 11;
        int height = 37;
        BandedSamples image = build(width, height, 16);

        Raster raster = image.getData(new Rectangle(0, 32, width, 5));
        for (int ry = 0; ry < 5; ry++) {
            for (int x = 0; x < width; x++) {
                assertThat((byte) raster.getSample(x, 32 + ry, 0))
                        .as("diem (%d, %d)", x, 32 + ry)
                        .isEqualTo(value(x, 32 + ry, 0));
            }
        }
    }
}
