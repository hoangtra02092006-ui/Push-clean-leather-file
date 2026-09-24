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
                            Tiff tiff, Cut cut) {

    /**
     * Xuat file cat cho may cat Graphtec.
     *
     * <p>File cat la ban PDF VECTOR rieng, chi chua duong cat quanh moi hinh va 4 dau dinh
     * vi. No KHONG phai ban in va khong thay the ban in: tho mo no bang Illustrator hoac
     * CorelDRAW roi day sang may cat qua plugin Cutting Master.
     *
     * <p>Moi con so mac dinh o day doc tu anh chup man hinh Cutting Master cua xuong, khong
     * con nao tu nghi ra.
     *
     * @param dpi            do phan giai de do vien, bang dung ban in. 300 DPI cho mot
     *                       diem anh 0,085 mm; xuong 150 DPI thi thanh 0,169 mm va duong
     *                       cat bam net kem han o cho co chi tiet nho
     * @param offsetMm       no duong cat ra NGOAI vien lop W1 bao nhieu milimet. 0 la chay
     *                       dung tren vien do - mac dinh, vi day la cai tho nhin thay khi
     *                       bat kenh W1 len trong Photoshop
     * @param simplifyMm     sai so cho phep khi bot dinh cua duong cat
     * @param minAreaMm2     bo qua mang nho hon muc nay - hat bui hay net le trong file
     *                       khong dang mot duong cat rieng
     * @param strokePt       do day net cua duong cat, don vi point
     * @param spotName       ten mau muc rieng cua duong cat. Illustrator va Cutting Master
     *                       deu nhan dang duong cat bang TEN nay
     * @param mark           thong so dau dinh vi
     */
    public record Cut(int dpi, double offsetMm, double simplifyMm, double minAreaMm2,
                      double strokePt, String spotName, Mark mark) {

        /**
         * Dau dinh vi o 4 goc, kieu "Graphtec 4 Points Type 1".
         *
         * <p>May cat dung camera do 4 dau nay de biet phim nam lech bao nhieu so voi luc
         * in, roi bu lai. Sai thong so thi may do khong ra dau va tu choi chay.
         *
         * <p><b>Hinh dau khai RIENG cho tung goc.</b> Khong co file PDF mau de doi chieu
         * tung milimet, nen de han o cau hinh: may cat do khong ra dau thi sua mot dong
         * chu khong phai sua code. Mac dinh ca bon goc deu
         * {@link Shape#SQUARE_WITH_EDGE}.
         *
         * @param lengthMm     chieu dai canh o vuong dau. 15 mm la so xuong chot; anh chup
         *                     Cutting Master ghi 10 mm nhung tho muon dau to hon cho camera
         *                     de bat
         * @param thicknessMm  do day net
         * @param clearanceMm  vung trong BAT BUOC quanh dau. Co hinh lan vao day thi camera
         *                     do nham, nen gap truong hop do thi ban cat TU NOI DAI trang ra
         *                     cho den khi bon goc sach - be ngang giu nguyen. Noi toi da
         *                     bang mot {@link #zoneMm()}
         * @param topLeft      hinh dau goc tren-trai; xem {@link Shape}
         * @param topRight     hinh dau goc tren-phai
         * @param bottomLeft   hinh dau goc duoi-trai
         * @param bottomRight  hinh dau goc duoi-phai
         */
        public record Mark(double lengthMm, double thicknessMm, double clearanceMm,
                           Shape topLeft, Shape topRight, Shape bottomLeft, Shape bottomRight) {

            /** Cac kieu dau ve duoc. */
            public enum Shape {
                /**
                 * Hai net nam o hai canh PHIA TRONG, khep o vuong cung voi mep trang.
                 *
                 * <p>Chi ve hai net; hai canh con lai cua o vuong chinh la hai mep giay o
                 * goc do. May cat nhin ra o vuong khep kin va hieu day la moc vung cat.
                 *
                 * <pre>
                 *   goc duoi-trai:
                 *     mep trai  |                  net doc nam o x = 10 mm
                 *               |  . . . . . █     net ngang nam o y = 10 mm
                 *               |  . . . . . █
                 *               |  █████████ █  &lt;- net ngang
                 *               +---------------
                 *                  mep duoi
                 * </pre>
                 */
                SQUARE_WITH_EDGE,
                /** Hai canh gap goc, op vao dung hai mep trang. */
                L,
                /** O vuong ve du bon canh, giua de trong. */
                SQUARE_OUTLINE,
                /** O vuong to day. */
                SQUARE_FILLED
            }

            /**
             * Canh cua o vuong phai de trong o moi goc: dau cong vung trong quanh no.
             *
             * <p>Cung la muc NOI DAI toi da cua trang cat: day hinh len bang day thi moi
             * diem anh deu nam ngoai o vuong o goc, bat ke no o dau.
             */
            public double zoneMm() {
                return lengthMm + clearanceMm;
            }
        }
    }

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
     * @param maxHeapFraction phan bo nho JVM duoc phep dung cho MOT tam, 0..1.
     *                       {@code maxMegapixels} la tran co dinh nen no khong biet may
     *                       chu that co bao nhieu RAM: 100 trieu diem o che do CMYK kem
     *                       kenh trang la 1 GB, qua thua tren mot may 512 MB. Ma het bo
     *                       nho o muc container thi JVM bi HE DIEU HANH giet - khong co
     *                       ngoai le nao de bat, may chu khoi dong lai va moi lan ghep
     *                       dang giu trong bo nho mat sach. Nen phai do theo heap that
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
    public record Tiff(int dpi, int maxMegapixels, double maxHeapFraction, String compression,
                       double compressionQuality, String colorMode, String cmykProfile,
                       boolean transparentLayer, boolean layerZip, White white) {

        /** So byte mot tam duoc phep chiem, suy tu heap that cua may chu dang chay. */
        public long maxBytesPerSheet() {
            return (long) (Runtime.getRuntime().maxMemory() * maxHeapFraction);
        }

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
