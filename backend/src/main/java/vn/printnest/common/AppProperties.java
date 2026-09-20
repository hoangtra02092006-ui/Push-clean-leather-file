package vn.printnest.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Cau hinh rieng cua ung dung, doc tu tien to {@code app} trong {@code application.yml}.
 *
 * @param storage cau hinh luu file
 * @param cors    cau hinh CORS
 * @param trim    cau hinh cat khoang trang quanh hinh
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Storage storage, Cors cors, Trim trim) {

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
}
