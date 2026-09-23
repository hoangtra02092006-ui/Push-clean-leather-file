package vn.printnest.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;

/**
 * Dung khoi du lieu lop cua Photoshop cho tag 37724 ({@code ImageSourceData}).
 *
 * <p><b>Vi sao phai co.</b> Anh gop trong file TIFF khong co khai niem trong suot: cho
 * khong co muc thi CMYK bang 0 0 0 0, ma Photoshop ve CMYK rong thanh mau TRANG. Nhin vao
 * thi khong phan biet duoc "nen trang" voi "khong co vung in". Tho can nhin thay o caro
 * xam de biet chac may chi in phan chi tiet.
 *
 * <p>Do trong suot khong nam trong anh gop ma nam trong mot LOP rieng, luu o tag 37724.
 * Doc tu file mau {@code samples/IN6 2209.tif}:
 *
 * <pre>
 *   Chu ky      : "Adobe Photoshop Document Data Block"
 *   Khoi "Layr" : 34.884.548 byte
 *     1 lop, khung (0,0)-(6689,10516) = phu kin tam
 *     5 kenh:  -1 (trong suot)  0 (Cyan)  1 (Magenta)  2 (Yellow)  3 (Black)
 *     che do hoa "norm", do dac 255, co 0x08
 *     ten lop "Layer 1", nen kieu 1 (RLE)
 * </pre>
 *
 * <p><b>Cai gia phai tra.</b> Anh bi luu HAI LAN trong cung mot file: mot ban gop cho
 * phan mem nao cung doc duoc, va mot ban lop mang theo do trong suot. File mau 58 MB thi
 * 34,9 MB la phan lop nay. Khong co duong nao tranh - anh gop la bat buoc cua chuan TIFF.
 *
 * <p><b>Thu tu byte.</b> Khoi nay di theo thu tu byte cua CHINH file TIFF bao quanh no,
 * khac voi khoi 34377 luon la byte lon truoc. File mau la {@code II} nen khoi trong do
 * nguoc het, doc ra thay chu ky thanh "MIB8" va "ryaL". Java ghi file {@code MM} nen o
 * day ghi byte lon truoc - dung chieu thuan cua dinh dang.
 */
final class PhotoshopLayers {

    /** Chuoi mo dau khoi, ket thuc bang mot byte 0. */
    private static final String SIGNATURE = "Adobe Photoshop Document Data Block";

    private static final byte[] BIM = {'8', 'B', 'I', 'M'};
    private static final byte[] LAYR = {'L', 'a', 'y', 'r'};
    private static final byte[] NORMAL_BLEND = {'n', 'o', 'r', 'm'};

    /** Ma kenh: -1 la do trong suot, 0..3 la C, M, Y, K. */
    private static final short CHANNEL_TRANSPARENCY = -1;
    private static final int CMYK_CHANNELS = 4;

    /**
     * Kieu nen du lieu kenh cua lop.
     *
     * <p>File mau dung RLE (PackBits) vi do la mac dinh cua Photoshop. Nhung ZIP cho file
     * nho hon han. Do A/B tren CUNG mot tam 6.732 x 11.463 diem:
     *
     * <pre>
     *   RLE  file 21,6 MB, dung lop mat 1.485 ms
     *   ZIP  file 15,8 MB, dung lop mat 5.024 ms
     * </pre>
     *
     * <p>Tuc la doi 3,5 giay dung them de bot 5,8 MB tai ve - hoa von o toc do tai
     * 1,7 MB/s. Chon ZIP vi file duoc GIU LAI sau lan dung dau: tien dung tra MOT lan,
     * tien tai tra moi lan.
     *
     * <p>Doi kieu nen KHONG doi cau truc lop: van dung mot lop, nam kenh, cung thu tu.
     * May in doc anh GOP chu khong doc lop, nen cho nay chi anh huong den phan mem mo file
     * de xem - ma Photoshop doc ZIP tu lau.
     */
    private static final short COMPRESSION_RLE = 1;
    private static final short COMPRESSION_ZIP = 2;

    /**
     * Co bao "ban ghi nay theo chuan tu Photoshop 5.0 tro di".
     *
     * <p>Doc duoc 0x08 tu file mau. Cac bit con lai deu 0: lop hien, khong khoa do trong
     * suot.
     */
    private static final int LAYER_FLAGS = 0x08;

    /**
     * Muc nen ZIP 0..9.
     *
     * <p>Do tren du lieu lop that cua mot tam 6.732 x 11.463 diem, ca nam kenh:
     *
     * <pre>
     *   muc 1   10.648.058 byte   1.734 ms
     *   muc 2   10.239.047 byte   1.749 ms
     *   muc 3    9.462.773 byte   2.339 ms
     *   muc 4    7.772.622 byte   2.605 ms
     *   muc 6    6.834.808 byte   3.266 ms
     *   RLE     13.865.461 byte     552 ms
     * </pre>
     *
     * <p>Chon muc 4: tu muc 1 len muc 4 bot duoc 2,9 MB ma chi them 0,9 giay, con len
     * muc 6 thi phai them 0,7 giay nua de bot 0,9 MB - khong dang.
     */
    private static final int ZIP_LEVEL = 4;

    /** PackBits chi ma hoa duoc tung doan toi da 128 byte. */
    private static final int MAX_RUN = 128;

    private PhotoshopLayers() {
    }

    /**
     * Dung khoi 37724 chua mot lop co do trong suot.
     *
     * <p><b>Kenh CMYK cua LOP nam NGUOC voi anh gop.</b> PSD luu CMYK lat lai: 0 la muc
     * DAY, 255 la khong muc - con anh gop TIFF thi 0 la khong muc. Do tren file mau, kenh
     * Cyan, mot dai anh o giua:
     *
     * <pre>
     *   x=137   anh gop 21   lop 234     tong 255
     *   x=274   anh gop 58   lop 197     tong 255
     *   x=1644  anh gop 53   lop 202     tong 255
     * </pre>
     *
     * <p>Ghi thang gia tri anh gop vao lop thi mot hinh mau KEM (rat it muc) bi doc thanh
     * DEN DAC. Anh gop van dung, nen mo file ra nhin thay den ma so lieu anh gop lai dung
     * - rat de tuong la loi chuyen mau.
     *
     * <p>Rieng kenh do trong suot KHONG lat: 0 la trong, 255 la co hinh, dung nhu file mau.
     *
     * @param samples mang diem anh xen ke cua anh gop
     * @param stride  so kenh moi diem anh trong {@code samples}
     * @param alpha   do trong suot: 255 = co hinh, 0 = trong. Day la vung phu CHUA co vao -
     *                lop mau trai het ra den mep, khac voi mat na muc trang
     * @param name    ten lop hien trong bang Layers
     * @param useZip  nen kenh bang ZIP thay vi RLE - xem {@link #COMPRESSION_ZIP}
     */
    static byte[] transparentLayer(byte[] samples, int stride, byte[] alpha,
                                   int width, int height, String name, boolean useZip)
            throws IOException {
        // Nen tung kenh mot. Kenh do trong suot di truoc, roi den C, M, Y, K - dung thu tu
        // file mau khai trong ban ghi.
        byte[][] channels = new byte[1 + CMYK_CHANNELS][];
        channels[0] = packChannel(alpha, 0, 1, width, height, false, useZip);
        for (int c = 0; c < CMYK_CHANNELS; c++) {
            channels[c + 1] = packChannel(samples, c, stride, width, height, true, useZip);
        }

        ByteArrayOutputStream layer = new ByteArrayOutputStream();
        writeLayerRecord(layer, channels, width, height, name);
        for (byte[] channel : channels) {
            layer.writeBytes(channel);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(SIGNATURE.getBytes(StandardCharsets.US_ASCII));
        out.write(0);
        out.writeBytes(BIM);
        out.writeBytes(LAYR);

        byte[] body = layer.toByteArray();
        // Khoi phai co do dai chan. Byte don nam TRONG do dai khai bao, khac voi khoi
        // 34377 don o ngoai - chep dung theo file mau.
        int padded = body.length + (body.length % 2);
        out.writeBytes(int32(2 + padded));
        out.writeBytes(int16((short) 1));
        out.writeBytes(body);
        if (padded != body.length) {
            out.write(0);
        }
        return out.toByteArray();
    }

    /**
     * Ban ghi mo ta lop: nam o dau, to bao nhieu, gom nhung kenh nao.
     *
     * <p>Do dai moi kenh o day TINH CA hai byte khai kieu nen. Ke thieu hai byte do thi
     * moi kenh lech dan, va Photoshop doc ra anh nhieu hat chu khong bao loi.
     */
    private static void writeLayerRecord(ByteArrayOutputStream out, byte[][] channels,
                                         int width, int height, String name) {
        out.writeBytes(int32(0));
        out.writeBytes(int32(0));
        out.writeBytes(int32(height));
        out.writeBytes(int32(width));

        out.writeBytes(int16((short) channels.length));
        out.writeBytes(int16(CHANNEL_TRANSPARENCY));
        out.writeBytes(int32(channels[0].length));
        for (int c = 0; c < CMYK_CHANNELS; c++) {
            out.writeBytes(int16((short) c));
            out.writeBytes(int32(channels[c + 1].length));
        }

        out.writeBytes(BIM);
        out.writeBytes(NORMAL_BLEND);
        out.write(255);
        out.write(0);
        out.write(LAYER_FLAGS);
        out.write(0);

        byte[] extra = layerExtras(name);
        out.writeBytes(int32(extra.length));
        out.writeBytes(extra);
    }

    /**
     * Phan duoi cua ban ghi lop: mat na, dai hoa, ten lop.
     *
     * <p>Khong co mat na va khong gioi han dai hoa nen hai cho dau deu bang 0. File mau
     * co 40 byte dai hoa va muoi khoi phu (luni, lyid, shmd...) - do la dau vet thoi quen
     * lam viec trong Photoshop, khong dinh gi den do trong suot nen khong chep lai.
     */
    private static byte[] layerExtras(String name) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(int32(0));
        out.writeBytes(int32(0));

        byte[] ascii = name.getBytes(StandardCharsets.US_ASCII);
        out.write(ascii.length);
        out.writeBytes(ascii);
        // Ten lop don len boi cua 4, TINH CA byte do dai o dau. Hai so 0 phia tren da
        // chiem dung 8 byte nen cu don den khi ca khoi chia het cho 4 la vua.
        while (out.size() % 4 != 0) {
            out.write(0);
        }
        return out.toByteArray();
    }

    /**
     * Tach mot kenh ra khoi anh xen ke roi nen bang PackBits.
     *
     * <p>Cach xep khac han anh gop: o day moi kenh la mot mang RIENG, va moi HANG duoc nen
     * doc lap. Truoc du lieu la bang do dai tung hang, de doc mot hang bat ky khong phai
     * giai nen tu dau.
     *
     * @param invert lat gia tri lai. Xem {@link #transparentLayer} ve chieu gia tri
     */
    private static byte[] packChannel(byte[] source, int band, int stride,
                                      int width, int height, boolean invert,
                                      boolean useZip) throws IOException {
        return useZip
                ? zipChannel(source, band, stride, width, height, invert)
                : rleChannel(source, band, stride, width, height, invert);
    }

    /**
     * ZIP: mot luong nen lien cho ca kenh, khong co bang do dai hang.
     *
     * <p>Nap tung hang vao bo nen chu khong tach ca kenh ra mang rieng truoc. Voi mot tam
     * 57 x 100 cm o 300 DPI, mang rieng do la 79 MB - nhan voi nam kenh la 395 MB cap phat
     * chi de vut di, ngay trong luc bo nho dang cang nhat.
     */
    private static byte[] zipChannel(byte[] source, int band, int stride,
                                     int width, int height, boolean invert) {
        Deflater deflater = new Deflater(ZIP_LEVEL);
        ByteArrayOutputStream out = new ByteArrayOutputStream(width * height / 4);
        out.writeBytes(int16(COMPRESSION_ZIP));

        byte[] row = new byte[width];
        byte[] chunk = new byte[1 << 16];
        for (int y = 0; y < height; y++) {
            readRow(source, band, stride, width, y, invert, row);
            deflater.setInput(row);
            while (!deflater.needsInput()) {
                out.write(chunk, 0, deflater.deflate(chunk));
            }
        }

        deflater.finish();
        while (!deflater.finished()) {
            out.write(chunk, 0, deflater.deflate(chunk));
        }
        deflater.end();
        return out.toByteArray();
    }

    /**
     * RLE: moi HANG nen doc lap, va truoc du lieu la bang do dai tung hang.
     *
     * <p>Co bang do dai nen doc mot hang bat ky khong phai giai nen tu dau - cai loi ma
     * ZIP khong co, nhung doi lai file to hon han.
     */
    private static byte[] rleChannel(byte[] source, int band, int stride,
                                     int width, int height, boolean invert) throws IOException {
        byte[] row = new byte[width];
        ByteArrayOutputStream packed = new ByteArrayOutputStream();
        short[] rowLengths = new short[height];

        for (int y = 0; y < height; y++) {
            readRow(source, band, stride, width, y, invert, row);
            int before = packed.size();
            packBits(row, packed);
            rowLengths[y] = (short) (packed.size() - before);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(packed.size() + height * 2 + 2);
        out.writeBytes(int16(COMPRESSION_RLE));
        for (short length : rowLengths) {
            out.writeBytes(int16(length));
        }
        packed.writeTo(out);
        return out.toByteArray();
    }

    /** Rut mot hang cua mot kenh ra khoi mang xen ke, lat gia tri neu can. */
    private static void readRow(byte[] source, int band, int stride,
                                int width, int y, boolean invert, byte[] row) {
        int at = y * width * stride + band;
        for (int x = 0; x < width; x++, at += stride) {
            row[x] = invert ? (byte) (255 - (source[at] & 0xFF)) : source[at];
        }
    }

    /**
     * Nen mot hang bang PackBits.
     *
     * <p>Hai kieu doan: doan LAP ghi {@code 257-n} roi mot byte, doan KHAC NHAU ghi
     * {@code n-1} roi n byte. Toi da 128 byte moi doan.
     *
     * <p>Doan lap chi dang ghi khi dai tu 3 byte tro len: hai byte giong nhau ma tach
     * thanh doan lap thi ton dung bang de nguyen, ma lai cat vun doan khac nhau dang chay
     * ngon tru.
     */
    private static void packBits(byte[] data, ByteArrayOutputStream out) {
        int at = 0;
        while (at < data.length) {
            int runLength = 1;
            while (at + runLength < data.length
                    && runLength < MAX_RUN
                    && data[at + runLength] == data[at]) {
                runLength++;
            }

            if (runLength >= 3) {
                out.write(257 - runLength);
                out.write(data[at]);
                at += runLength;
                continue;
            }

            // Gom cac byte khac nhau lai, dung ngay truoc mot doan lap dang gia.
            int start = at;
            int literal = 0;
            while (at < data.length && literal < MAX_RUN) {
                if (at + 2 < data.length && data[at] == data[at + 1] && data[at] == data[at + 2]) {
                    break;
                }
                at++;
                literal++;
            }
            out.write(literal - 1);
            out.write(data, start, literal);
        }
    }

    private static byte[] int32(int value) {
        return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array();
    }

    private static byte[] int16(short value) {
        return ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(value).array();
    }
}
