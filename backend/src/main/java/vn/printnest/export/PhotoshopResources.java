package vn.printnest.export;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Dung khoi "Photoshop Image Resources" cho tag 34377 cua file TIFF.
 *
 * <p><b>Vi sao phai co khoi nay.</b> Mot file TIFF co the co kenh thu nam, nhung ban than
 * chuan TIFF chi noi duoc "co mot kenh phu" - no khong co cho nao de ghi TEN kenh, cung
 * khong phan biet duoc kenh do la lop trong suot hay la mot mau muc rieng. Photoshop va
 * cac RIP giai quyet bang cach nhet them mot khoi rieng vao tag 34377. Thieu khoi nay thi
 * mo file ra chi thay "Alpha 1" chu khong thay "W1" trong bang Channels, va may in khong
 * biet do la muc trang.
 *
 * <p><b>Moi con so o day deu doc tu file mau, khong cai nao tu nghi ra.</b> File mau la
 * {@code samples/IN6 2209.tif} - ban do tho lam tay trong Photoshop. Doc nguyen hex ra
 * duoc bon khoi:
 *
 * <pre>
 *   8BIM 1006 (3 byte)  : 02 57 31                          ten kenh, chuoi Pascal "W1"
 *   8BIM 1045 (10 byte) : 00 00 00 03 00 57 00 31 00 00     ten Unicode, DEM CA ky tu \0
 *   8BIM 1077 (17 byte) : 00 00 00 01                       DisplayInfo, phien ban 1
 *                         00 00                             khong gian mau (0 = RGB)
 *                         ff ff 00 00 00 00 00 00           mau hien thi tren man hinh
 *                         00 64                             do dac 100%
 *                         02                                kind = 2 = SPOT CHANNEL
 *   8BIM 1053 (4 byte)  : 00 00 00 03                       so hieu kenh alpha
 * </pre>
 *
 * <p>Trong bon khoi do, {@code kind = 2} o khoi 1077 moi la cho quyet dinh: dat 0 thi
 * Photoshop hieu day la kenh alpha thuong (lop trong suot), dat 2 no moi hieu la mau muc
 * rieng. Ten kenh phai ghi o CA HAI khoi 1006 va 1045 - ban cu doc khoi nao la tuy phan
 * mem, nen ghi thieu mot cai la co phan mem khong thay ten.
 *
 * <p>File mau con gan 25 khoi 8BIM khac (luoi, duong dan, luoc do, ban xem truoc...).
 * Chung la ve dau vet thoi quen lam viec trong Photoshop, khong lien quan den viec dinh
 * nghia kenh muc, nen o day khong chep lai.
 *
 * <p>Ca khoi nay LUON theo thu tu byte lon truoc (big-endian), khong phu thuoc thu tu byte
 * cua file TIFF bao quanh no.
 */
final class PhotoshopResources {

    /** Chu ky mo dau moi khoi tai nguyen cua Photoshop. */
    private static final byte[] SIGNATURE = {'8', 'B', 'I', 'M'};

    private static final int ID_CHANNEL_NAMES = 1006;
    private static final int ID_UNICODE_NAMES = 1045;
    private static final int ID_DISPLAY_INFO = 1077;
    private static final int ID_ALPHA_IDENTIFIERS = 1053;

    /** Gia tri bao "kenh nay la mau muc rieng" chu khong phai lop trong suot. */
    private static final byte KIND_SPOT_CHANNEL = 2;

    /** So hieu kenh alpha, lay dung nhu file mau. */
    private static final int ALPHA_IDENTIFIER = 3;

    /** Do dac cua kenh muc, tinh theo phan tram. */
    private static final int OPACITY_PERCENT = 100;

    private PhotoshopResources() {
    }

    /**
     * Dung khoi tai nguyen mo ta mot kenh muc rieng.
     *
     * @param channelName ten kenh se hien trong bang Channels, vi du {@code W1}
     */
    static byte[] spotChannel(String channelName) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        block(out, ID_CHANNEL_NAMES, pascalName(channelName));
        block(out, ID_UNICODE_NAMES, unicodeName(channelName));
        block(out, ID_DISPLAY_INFO, displayInfo());
        block(out, ID_ALPHA_IDENTIFIERS, bigEndian(4).putInt(ALPHA_IDENTIFIER).array());
        return out.toByteArray();
    }

    /** Ten kenh dang chuoi Pascal: mot byte do dai roi den cac ky tu. */
    private static byte[] pascalName(String name) {
        byte[] ascii = name.getBytes(StandardCharsets.US_ASCII);
        byte[] result = new byte[ascii.length + 1];
        result[0] = (byte) ascii.length;
        System.arraycopy(ascii, 0, result, 1, ascii.length);
        return result;
    }

    /**
     * Ten kenh dang Unicode: so ky tu roi den cac ky tu UTF-16.
     *
     * <p>So ky tu DEM CA ky tu ket thuc chuoi - file mau ghi 3 cho ten hai chu "W1". Ghi
     * 2 thi van doc ra "W1" nhung lech mot ky tu so voi ban Photoshop ghi ra.
     */
    private static byte[] unicodeName(String name) {
        ByteBuffer buffer = bigEndian(4 + (name.length() + 1) * 2);
        buffer.putInt(name.length() + 1);
        for (char c : name.toCharArray()) {
            buffer.putChar(c);
        }
        buffer.putChar('\0');
        return buffer.array();
    }

    /**
     * Mo ta cach hien thi kenh: mau gi tren man hinh, dam nhat bao nhieu, loai gi.
     *
     * <p>Mau {@code [65535, 0, 0, 0]} la mau DO. No chi la mau tuong trung de nhin tren
     * man hinh cho de phan biet - khong dinh gi den muc thuc su phun ra, va file mau cung
     * de nhu vay.
     */
    private static byte[] displayInfo() {
        ByteBuffer buffer = bigEndian(17);
        buffer.putInt(1);
        buffer.putShort((short) 0);
        buffer.putShort((short) 0xFFFF);
        buffer.putShort((short) 0);
        buffer.putShort((short) 0);
        buffer.putShort((short) 0);
        buffer.putShort((short) OPACITY_PERCENT);
        buffer.put(KIND_SPOT_CHANNEL);
        return buffer.array();
    }

    /**
     * Ghi mot khoi: chu ky, so hieu, ten rong, do dai, du lieu.
     *
     * <p>Moi khoi phai co do dai CHAN. Khoi le byte thi them mot byte 0 vao cuoi - thieu
     * cho nay thi cac khoi dung sau bi lech mot byte va khong doc duoc nua.
     */
    private static void block(ByteArrayOutputStream out, int id, byte[] data) {
        out.writeBytes(SIGNATURE);
        out.write(id >> 8);
        out.write(id & 0xFF);
        // Ten khoi: chuoi Pascal rong, va cung phai don cho du do dai chan.
        out.write(0);
        out.write(0);
        out.writeBytes(bigEndian(4).putInt(data.length).array());
        out.writeBytes(data);
        if (data.length % 2 != 0) {
            out.write(0);
        }
    }

    private static ByteBuffer bigEndian(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN);
    }
}
