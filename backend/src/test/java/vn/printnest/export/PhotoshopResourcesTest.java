package vn.printnest.export;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Doi chieu khoi tai nguyen Photoshop voi BAN LAM TAY.
 *
 * <p>File mau {@code samples/IN6 2209.tif} la ban do tho tu dung trong Photoshop. Toan bo
 * con so trong bai nay doc nguyen hex tu file do ra, khong con nao tu nghi.
 *
 * <p>Bai test khong doc file mau (55 MB, khong nen bo vao kho ma cung khong nen bat bai
 * test phu thuoc vao no) ma cheo san ket qua da doc duoc. Neu mai nay co ai doi cach dung
 * khoi nay, bai test se chi thang ra cho lech so voi ban Photoshop ghi.
 */
class PhotoshopResourcesTest {

    private static String hex(byte[] data) {
        StringBuilder text = new StringBuilder();
        for (byte b : data) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(String.format("%02x", b));
        }
        return text.toString();
    }

    /**
     * Toan bo khoi, so voi bon khoi doc duoc tu file mau.
     *
     * <p>Moi khoi: chu ky {@code 38 42 49 4d} ("8BIM"), so hieu 2 byte, ten rong 2 byte,
     * do dai 4 byte, roi den du lieu va byte don cho du do dai chan.
     */
    @Test
    @DisplayName("Khoi 34377 trung khit ban Photoshop ghi ra")
    void matchesTheHandMadeSample() {
        assertThat(hex(PhotoshopResources.spotChannel("W1"))).isEqualTo(
                // 8BIM 1006 (0x03ee) - ten kenh dang chuoi Pascal "W1", don 1 byte
                "38 42 49 4d 03 ee 00 00 00 00 00 03 02 57 31 00 "
                        // 8BIM 1045 (0x0415) - ten Unicode, dem 3 ky tu gom ca \0
                        + "38 42 49 4d 04 15 00 00 00 00 00 0a 00 00 00 03 00 57 00 31 00 00 "
                        // 8BIM 1077 (0x0435) - DisplayInfo: ban 1, mau do, dac 100%, kind 2
                        + "38 42 49 4d 04 35 00 00 00 00 00 11 "
                        + "00 00 00 01 00 00 ff ff 00 00 00 00 00 00 00 64 02 00 "
                        // 8BIM 1053 (0x041d) - so hieu kenh alpha
                        + "38 42 49 4d 04 1d 00 00 00 00 00 04 00 00 00 03");
    }

    /**
     * Byte quyet dinh ca bai: {@code kind = 2}.
     *
     * <p>Dat 0 thi Photoshop hieu day la kenh alpha thuong - tuc la lop TRONG SUOT, va no
     * se lam mo lop mau theo. Dat 2 no moi hieu la mau muc rieng. File van mo ra binh
     * thuong trong ca hai truong hop, nen sai cho nay khong lo ra o dau het.
     */
    @Test
    @DisplayName("DisplayInfo ghi kind = 2, tuc la mau muc rieng")
    void displayInfoSaysSpotChannel() {
        byte[] block = PhotoshopResources.spotChannel("W1");
        String all = hex(block);

        int start = all.indexOf("38 42 49 4d 04 35");
        assertThat(start).as("phai co khoi 1077 DisplayInfo").isNotNegative();

        // Trong khoi 1077: 12 byte dau la phan bao, roi 4 byte phien ban, 12 byte mo ta
        // mau va do dac, byte thu 29 la kind.
        int kindAt = start / 3 + 12 + 4 + 12;
        assertThat(block[kindAt]).as("kind phai la 2 = mau muc rieng").isEqualTo((byte) 2);
    }

    /**
     * Ten kenh phai ghi o CA HAI khoi.
     *
     * <p>Ban cu doc khoi nao la tuy phan mem, nen thieu mot cai la co phan mem khong thay
     * ten kenh.
     */
    @Test
    @DisplayName("Doi ten kenh thi doi o ca hai cho")
    void channelNameGoesInBothResources() {
        String block = hex(PhotoshopResources.spotChannel("WHT"));

        // Chuoi Pascal: do dai 3 roi den "WHT".
        assertThat(block).as("khoi 1006 phai co ten dang Pascal").contains("03 57 48 54");
        // Unicode: dem 4 ky tu gom ca \0, roi den "WHT\0" dang UTF-16.
        assertThat(block).as("khoi 1045 phai co ten dang Unicode")
                .contains("00 00 00 04 00 57 00 48 00 54 00 00");
    }

    /**
     * Moi khoi phai co do dai chan.
     *
     * <p>Le mot byte thi cac khoi dung sau bi lech va khong doc duoc nua - ma phan mem
     * doc file thuong bo qua lang le chu khong bao loi.
     */
    @Test
    @DisplayName("Ten kenh do dai chan van khong lam lech khoi")
    void everyBlockKeepsAnEvenLength() {
        for (String name : new String[]{"W", "W1", "WHT", "WHITE"}) {
            byte[] block = PhotoshopResources.spotChannel(name);
            int position = 0;
            int blocks = 0;

            while (position < block.length) {
                assertThat(new String(block, position, 4, java.nio.charset.StandardCharsets.US_ASCII))
                        .as("khoi thu %d cua ten \"%s\" phai bat dau bang 8BIM", blocks, name)
                        .isEqualTo("8BIM");
                int size = ((block[position + 8] & 0xFF) << 24) | ((block[position + 9] & 0xFF) << 16)
                        | ((block[position + 10] & 0xFF) << 8) | (block[position + 11] & 0xFF);
                position += 12 + size + (size % 2);
                blocks++;
            }

            assertThat(position).as("ten \"%s\": cac khoi phai khop het, khong thua byte nao", name)
                    .isEqualTo(block.length);
            assertThat(blocks).as("ten \"%s\": phai du bon khoi", name).isEqualTo(4);
        }
    }
}
