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
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Storage storage, Cors cors, Trim trim, Retention retention) {

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
}
