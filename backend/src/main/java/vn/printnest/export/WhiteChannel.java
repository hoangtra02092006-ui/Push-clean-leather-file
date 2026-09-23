package vn.printnest.export;

import java.awt.image.BufferedImage;

/**
 * Dung mat na muc trang (spot channel W1) cho in DTF.
 *
 * <p>May in DTF phun mot lop muc TRANG LOT xuong truoc, roi moi phun mau len tren. Khong
 * co lop lot thi mau in len vai toi se chim het. Mat na nay noi cho may biet cho nao can
 * lot trang.
 *
 * <p><b>Lay tu dau.</b> Tu kenh alpha cua anh da raster hoa: diem nao co hinh thi lot
 * trang, diem nao la phim trong thi khong. KHONG lay tu "diem nao khong trang" - mot logo
 * co chi tiet mau trang that se bi coi la nen va mat lop lot. Day dung la cai bay ma he
 * thong nay da tranh o phan cat vien quanh hinh.
 *
 * <p><b>Vi sao phai co vao trong.</b> Lop trang phai nam LOT ben trong lop mau. Neu hai
 * lop bang nhau, chi can may keo phim lech nua milimet la vien trang lo ra quanh hinh -
 * nhin thay ro tren ao mau toi. Co vao vai diem anh la doi lay khoang an toan do.
 */
public final class WhiteChannel {

    /** Gia tri trong mat na: co muc / khong co muc. */
    private static final byte INK = (byte) 255;
    private static final byte NONE = 0;

    /**
     * Co hinh, nhung mau gan trang - CHUA biet la nen hay la chi tiet trang that.
     *
     * <p>Danh dau rieng de buoc loang tu mep chi phai doc mat na, khong phai hoi lai anh
     * goc lan nua. Sau khi loang xong, cho nao con mang dau nay tuc la khong noi ra duoc
     * mep, nen no la chi tiet trang that va duoc lot trang binh thuong.
     */
    private static final byte PALE = 1;

    private WhiteChannel() {
    }

    /**
     * Dung mat na tu kenh alpha roi co vao trong.
     *
     * @param image          anh da raster hoa
     * @param threshold      nguong alpha 0..255; tren nguong thi coi la co hinh
     * @param choke          so diem anh co vao trong moi phia
     * @param whiteTolerance do lech cho phep so voi trang tuyet doi khi do nen
     * @return mang mot byte moi diem: 255 = lot trang, 0 = khong
     */
    public static byte[] build(BufferedImage image, int threshold, int choke, int whiteTolerance) {
        return choke(coverage(image, threshold, whiteTolerance),
                image.getWidth(), image.getHeight(), choke);
    }

    /**
     * Co vung phu vao trong de duoc mat na muc trang.
     *
     * @param pixels so diem anh co vao moi phia; 0 thi giu nguyen
     */
    public static byte[] choke(byte[] coverage, int width, int height, int pixels) {
        return pixels > 0 ? erode(coverage, width, height, pixels) : coverage;
    }

    /**
     * Vung co hinh, CHUA co vao trong.
     *
     * <p>Khac mat na muc trang o dung mot cho: khong co vao. Lop mau trai het ra den mep
     * hinh, chi rieng lop trang moi phai thut vao de khoi lo vien. Dung cho kenh do trong
     * suot cua lop Photoshop - xem {@link PhotoshopLayers}.
     *
     * @return mang mot byte moi diem: 255 = co hinh, 0 = trong
     */
    public static byte[] coverage(BufferedImage image, int threshold, int whiteTolerance) {
        int width = image.getWidth();
        int height = image.getHeight();
        boolean hasAlpha = image.getColorModel().hasAlpha();
        byte[] mask = new byte[width * height];
        int floor = 255 - whiteTolerance;

        // Muon THANG mang diem anh khi co the. PDFBox ve ra anh INT_ARGB, ma getRGB cua
        // no van phai goi qua mo hinh mau tung diem - 79 trieu lan cho mot tam 57 x 100 cm
        // o 300 DPI. Doc thang mang thi bo han chang do.
        int[] direct = directPixels(image);
        int[] row = direct == null ? new int[width] : null;

        for (int y = 0; y < height; y++) {
            int offset = y * width;
            int[] source = direct;
            int from = offset;
            if (direct == null) {
                image.getRGB(0, y, width, 1, row, 0, width);
                source = row;
                from = 0;
            }

            for (int x = 0; x < width; x++) {
                int argb = source[from + x];
                int alpha = hasAlpha ? argb >>> 24 : 255;
                if (alpha <= threshold) {
                    mask[offset + x] = NONE;
                    continue;
                }
                boolean pale = ((argb >> 16) & 0xFF) >= floor
                        && ((argb >> 8) & 0xFF) >= floor
                        && (argb & 0xFF) >= floor;
                mask[offset + x] = pale ? PALE : INK;
            }
        }

        floodBackground(mask, width, height);

        // Cho nao gan trang ma khong noi ra mep thi la chi tiet trang that: lot binh thuong.
        for (int i = 0; i < mask.length; i++) {
            if (mask[i] == PALE) {
                mask[i] = INK;
            }
        }

        return mask;
    }

    /**
     * Bo lop trang o nhung vung gan trang NOI RA MEP tam.
     *
     * <p><b>Vi sao khong chi lay "diem nao khong trang".</b> Mot logo co chu trang that
     * thi chu do van phai duoc lot trang - in len ao mau toi ma khong lot thi chu bien
     * mat. Nhung mot anh JPG nen trang thi phan nen cung la mau trang y het. Nhin rieng
     * mot diem anh thi khong the phan biet.
     *
     * <p>Phan biet duoc bang chuyen KHAC: nen thi noi lien ra mep, con chu trang thi bi
     * mau bao quanh. Nen o day loang tu mep vao, di qua cac diem gan trang. Cho nao loang
     * toi thi la nen. Dung dung cach tho bam magic wand vao nen trong Photoshop.
     *
     * <p>Vung trong suot cung la diem xuat phat, nen anh co san nen trong suot van chay
     * dung y nhu cu.
     *
     * <p><b>Cho van chiu:</b> thiet ke co vien trang CHAM MEP anh thi vien do noi ra
     * ngoai, se bi coi la nen. Khong co cach nao phan biet, ke ca lam tay.
     */
    private static void floodBackground(byte[] mask, int width, int height) {
        int[] stack = new int[1024];
        int top = 0;

        // Diem xuat phat: chi lay diem GAN TRANG nam sat mot diem trong, hoac nam tren
        // vien tam.
        //
        // Ban dau toi nap moi diem trong vao day. Sai o cho tam nao cung day khoang
        // trong: mot tam 57 x 100 cm co 16 trieu diem trong, tuc 16 trieu luot nap va
        // mot mang 64 MB phai nhan doi muoi may lan. Ma diem trong chi co moi viec la
        // doi mau nhung diem gan trang canh no - nen chi can nap dung nhung diem gan
        // trang nam o ria la du, ket qua y het.
        for (int i = 0; i < mask.length; i++) {
            if (mask[i] != PALE) {
                continue;
            }
            int x = i % width;
            int y = i / width;
            boolean onBorder = x == 0 || y == 0 || x == width - 1 || y == height - 1;
            boolean touchesGap = (x > 0 && mask[i - 1] == NONE)
                    || (x < width - 1 && mask[i + 1] == NONE)
                    || (y > 0 && mask[i - width] == NONE)
                    || (y < height - 1 && mask[i + width] == NONE);

            if (onBorder || touchesGap) {
                mask[i] = NONE;
                stack = push(stack, top++, i);
            }
        }

        while (top > 0) {
            int index = stack[--top];
            int x = index % width;
            int y = index / width;

            for (int dir = 0; dir < 4; dir++) {
                int nx = x + (dir == 0 ? -1 : dir == 1 ? 1 : 0);
                int ny = y + (dir == 2 ? -1 : dir == 3 ? 1 : 0);
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) {
                    continue;
                }
                int next = ny * width + nx;
                if (mask[next] != PALE) {
                    continue;
                }
                mask[next] = NONE;
                stack = push(stack, top, next);
                top++;
            }
        }
    }

    /**
     * Mang diem anh tho cua anh, neu muon thang duoc.
     *
     * <p>Chi muon duoc khi anh xep kieu int don gian - dung mot mang lien mach, moi diem
     * mot int, khong co le hang hay lech goc. PDFBox ve ra dung kieu do. Khong khop thi
     * tra ve null de quay lai duong {@code getRGB} cho an toan.
     */
    private static int[] directPixels(BufferedImage image) {
        int type = image.getType();
        if (type != BufferedImage.TYPE_INT_ARGB && type != BufferedImage.TYPE_INT_RGB) {
            return null;
        }
        if (!(image.getRaster().getDataBuffer() instanceof java.awt.image.DataBufferInt buffer)
                || buffer.getNumBanks() != 1) {
            return null;
        }
        int[] pixels = buffer.getData();
        return pixels.length == image.getWidth() * image.getHeight() ? pixels : null;
    }

    private static int[] push(int[] stack, int top, int value) {
        if (top == stack.length) {
            stack = java.util.Arrays.copyOf(stack, stack.length * 2);
        }
        stack[top] = value;
        return stack;
    }

    /**
     * Co mat na vao trong {@code radius} diem moi phia.
     *
     * <p>Lam hai luot mot chieu thay vi quet ca o vuong quanh moi diem: ket qua y het nhau
     * voi nhan hinh vuong, nhung mot tam 300 DPI co gan 80 trieu diem - quet o 5x5 la 2 ty
     * phep so, con hai luot truot chi la 160 trieu.
     */
    static byte[] erode(byte[] mask, int width, int height, int radius) {
        byte[] pass = new byte[mask.length];

        // Diem nam NGOAI tam luon coi la khong co muc. Nho vay hinh cham mep tam cung bi
        // co vao, chu khong dinh nguyen o mep - do moi la cho de lo vien trang nhat.
        for (int y = 0; y < height; y++) {
            int row = y * width;
            int gaps = radius;
            for (int x = 0; x <= radius; x++) {
                if (x >= width || mask[row + x] == NONE) {
                    gaps++;
                }
            }
            for (int x = 0; x < width; x++) {
                pass[row + x] = gaps == 0 ? INK : NONE;

                int leaving = x - radius;
                int entering = x + radius + 1;
                if (leaving < 0 || mask[row + leaving] == NONE) {
                    gaps--;
                }
                if (entering >= width || mask[row + entering] == NONE) {
                    gaps++;
                }
            }
        }

        byte[] out = new byte[mask.length];

        // Luot doc tren ket qua luot ngang.
        for (int x = 0; x < width; x++) {
            int gaps = radius;
            for (int y = 0; y <= radius; y++) {
                if (y >= height || pass[y * width + x] == NONE) {
                    gaps++;
                }
            }
            for (int y = 0; y < height; y++) {
                out[y * width + x] = gaps == 0 ? INK : NONE;

                int leaving = y - radius;
                int entering = y + radius + 1;
                if (leaving < 0 || pass[leaving * width + x] == NONE) {
                    gaps--;
                }
                if (entering >= height || pass[entering * width + x] == NONE) {
                    gaps++;
                }
            }
        }

        return out;
    }

    /**
     * Ghi mat na vao kenh thu nam cua anh, THEO CHIEU NGUOC LAI.
     *
     * <p><b>Day la cho de lam hong nhat ca bai, va no khong the suy ra duoc.</b> Trong
     * mat na o tren, 255 la co muc trang - cach hieu tu nhien, va cung la cach ve anh xem
     * truoc. Nhung trong file TIFF ma Photoshop ghi ra thi kenh muc rieng nam NGUOC: 0 moi
     * la co muc.
     *
     * <p>Doc thang du lieu diem anh cua file mau {@code samples/IN6 2209.tif} - giai nen
     * LZW roi go predictor - tren nam dai anh, 234.115 diem:
     *
     * <pre>
     *   Cho co hinh (CMYK co muc) : W1 trung binh = 3
     *   Cho trong   (CMYK = 0000) : W1 trung binh = 255
     * </pre>
     *
     * <p>Dat nguoc chieu nay thi lop trang ra AM BAN: may phun trang day vao khoang trong
     * quanh hinh va bo trang chinh cho co hinh. File van mo ra binh thuong, van du nam
     * kenh, van dung ten - chi den luc muc len ao moi lo ra.
     *
     * @param mask        mat na tu {@link #build}, 255 = co muc trang
     * @param samples     mang diem anh xen ke cua ca anh nam kenh
     * @param band        thu tu kenh can ghi, tinh tu 0
     * @param pixelStride so kenh moi diem anh
     */
    public static void writeSpotBand(byte[] mask, byte[] samples, int band, int pixelStride) {
        for (int i = 0; i < mask.length; i++) {
            samples[i * pixelStride + band] = (byte) (255 - (mask[i] & 0xFF));
        }
    }
}
