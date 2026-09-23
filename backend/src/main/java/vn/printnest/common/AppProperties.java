package vn.printnest.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Cau hinh rieng cua ung dung, doc tu tien to {@code app} trong {@code application.yml}.
 *
 * @param storage   cau hinh luu file
 * @param cors      cau hinh CORS
 * @param trim      cau hinh cat khoang trang quanh hinh
 * @param retention cau hinh don rac
 * @param tiff      cau hinh xuat file TIF
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Storage storage, Cors cors, Trim trim, Retention retention,
                            Tiff tiff) {

    /**
     * @param path        thu muc luu file tam tren dia
     * @param maxFileSize dung luong toi da moi file, tinh bang byte
     */
    public record Storage(String path, long maxFileSize) {
    }

    /**
     * @param allowedOrigins danh sach origin duoc phep goi API
     */
    public record Cors(List<String> allowedOrigins) {
    }

    /**
     * @param enabled co doc content stream de cat bo khoang trang quanh hinh khong.
     *                Tat di thi quay ve lay tron kho trang nhu truoc - dung khi gap file
     *                la khien viec cat cho ket qua sai.
     */
    public record Trim(boolean enabled) {
    }

    /**
     * Don rac dinh ky.
     *
     * <p>File upload, anh xem truoc va ban ghi job deu duoc giu lai de tho quay lai tai
     * PDF. Neu khong co gi don, chung tich luy MAI MAI: dia day dan (khi co gan volume),
     * con bo nho thi phinh toi luc JVM het cho va tu khoi dong lai - mat sach file dang
     * lam do giua chung.
     *
     * @param hours        giu file va job bao nhieu gio truoc khi xoa. 0 hoac am = tat han
     *                     viec don rac
     * @param sweepMinutes bao lau quet mot lan
     */
    public record Retention(int hours, int sweepMinutes) {

        /** Co bat don rac khong. */
        public boolean enabled() {
            return hours > 0;
        }
    }

    /**
     * Xuat ban TIF.
     *
     * <p>TIF la anh bitmap nen phai chot truoc do phan giai, va do phan giai quyet dinh
     * toan bo chi phi: mot tam 57 x 100 cm o 150 DPI la 20 trieu diem (80 MB trong bo
     * nho), o 300 DPI thanh 80 trieu diem (318 MB). Gap doi DPI la gap BON lan bo nho.
     *
     * @param dpi            do phan giai. 300 la muc chuan cho in an
     * @param maxMegapixels  tran cung: tam lon hon muc nay thi bao loi ro rang thay vi de
     *                       may chu het bo nho roi tu khoi dong lai
     * @param compression    kieu nen TIFF: {@code Deflate} (mac dinh) hoac {@code LZW}
     *                       (duoc moi RIP doi cu ho tro)
     * @param compressionQuality muc nen 0..1. So NHO la nhanh va file to hon. Do that
     *                       tren mot tam 57 x 100 cm o 300 DPI: muc 0,3 nen het 583 ms
     *                       cho ra 5.094 KB, con muc mac dinh nen het 3.054 ms cho ra
     *                       3.965 KB - cham gap 5 lan de bot 1 MB
     * @param colorMode      {@code rgb} hoac {@code cmyk}
     * @param cmykProfile    duong dan file ICC cua khong gian CMYK dich. BAT BUOC khi
     *                       {@code colorMode = cmyk}. Duong dan tren dia, hoac
     *                       {@code classpath:ten-file.icc} neu de trong resources.
     *                       Ho so nay duoc NHUNG vao tung file xuat ra, nen kich thuoc
     *                       cua no cong thang vao moi tam
     * @param transparentLayer co kem lop mang do trong suot khong. Anh gop cua TIFF khong
     *                       co khai niem trong suot - cho khong co muc thi CMYK bang
     *                       0 0 0 0 va Photoshop ve thanh mau TRANG, nhin vao khong phan
     *                       biet duoc "nen trang" voi "khong co vung in". Lop nay cho thay
     *                       o caro xam. Doi lai anh bi luu HAI lan trong cung mot file
     * @param layerZip       nen du lieu lop bang ZIP thay vi RLE. Do tren mot tam that:
     *                       RLE 4.738.303 byte mat 1.330 ms, ZIP 2.542.473 byte mat 735 ms
     * @param white          cau hinh kenh muc trang (spot channel) cho in DTF
     */
    public record Tiff(int dpi, int maxMegapixels, String compression,
                       double compressionQuality, String colorMode, String cmykProfile,
                       boolean transparentLayer, boolean layerZip, White white) {

        /**
         * Kenh muc trang (spot channel) cho in DTF.
         *
         * <p>May in DTF phun mot lop muc TRANG LOT xuong truoc roi moi phun mau len tren.
         * Khong co lop lot thi mau in len vai toi se chim het.
         *
         * @param enabled        co them kenh nay vao file TIF khong
         * @param channelName    ten kenh, phai khop dung voi cai RIP cho doi
         * @param alphaThreshold nguong alpha 0..255; tren nguong thi coi la co hinh
         * @param chokePixels    so diem anh co lop trang vao trong moi phia. Tho dang lam
         *                       tay bang Select > Modify > Contract voi so 1, nen mac dinh
         *                       cung la 1. Luu y day la diem anh o DO PHAN GIAI XUAT FILE:
         *                       1 diem o 300 DPI la 0,085 mm
         * @param whiteTolerance do lech cho phep so voi trang tuyet doi khi do xem mot diem
         *                       co phai nen khong. Phai co dung sai vi anh JPG bi nen mat
         *                       du lieu nen nen trang cua no thuong ra 250-255
         */
        public record White(boolean enabled, String channelName, int alphaThreshold,
                            int chokePixels, int whiteTolerance) {
        }

        /**
         * Co xuat CMYK khong.
         *
         * <p>Mac dinh la CMYK vi RIP cua xuong nhan dinh dang do, va vi ho so mac dinh
         * dung DUNG ho so xuong dang dung trong Photoshop (U.S. Web Coated SWOP v2) nen
         * khong them mot lan lech mau nao.
         *
         * <p>Neu doi sang mot ho so chung chung khong phai cua may in that thi CMYK lai
         * la lua chon KEM hon RGB: RGB kem ho so sRGB de RIP tu chuyen sang khong gian
         * cua chinh may in la cach it lech mau nhat.
         */
        public boolean cmyk() {
            return "cmyk".equalsIgnoreCase(colorMode);
        }
    }
}
