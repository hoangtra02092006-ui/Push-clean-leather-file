package vn.printnest.export;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Giu lai file thanh pham da dung, de lan tai sau khong phai dung lai tu dau.
 *
 * <p><b>Vi sao can.</b> Dung mot ban TIF 57 x 100 cm o 300 DPI mat khoang 4 giay, gan het
 * la thoi gian nen anh. Ma tho hay tai mot tam de xem truoc roi moi tai ca bo .zip - luc
 * do MOI TAM DEU BI DUNG LAI LAN THU HAI. Do la ca chuc giay ngoi cho cho mot viec vua
 * lam xong.
 *
 * <p>Giu lai KET QUA chu khong phai anh trung gian: ket qua chi vai megabyte, con anh
 * trung gian toi hang tram. Cai dat thay tram lan bo nho ma van cat duoc gan het thoi
 * gian cho.
 *
 * <p>Co tran dung luong va don theo kieu <i>dung lau nhat thi bo truoc</i>. Khong co tran
 * thi day chinh la mot cho ro ri bo nho moi - dung kieu loi ma he thong nay vua sua xong
 * o cho khac.
 */
@Component
public class ExportCache {

    private static final Logger log = LoggerFactory.getLogger(ExportCache.class);

    /**
     * Tran dung luong, tinh bang byte.
     *
     * <p>200 MB chua duoc khoang 50 tam o muc 4 MB moi tam - du cho vai don lam cung luc.
     * Con so nay nho so voi dinh bo nho luc DUNG mot tam (khoang 640 MB o che do CMYK),
     * nen no khong phai la thu quyet dinh may chu can bao nhieu RAM.
     */
    private static final long MAX_BYTES = 200L * 1024 * 1024;

    /** Bang theo thu tu DUNG GAN NHAT, nen phan tu dau bang la thu lau khong ai dung. */
    private final Map<String, byte[]> entries = new LinkedHashMap<>(16, 0.75f, true);

    private long usedBytes;

    /**
     * Lay ra tu bo nho, khong co thi dung roi giu lai.
     *
     * @param jobId  ma lan ghep
     * @param index  so thu tu tam
     * @param format {@code pdf} hoac {@code tif}
     * @param build  cach dung file neu chua co
     */
    public byte[] get(String jobId, int index, String format, Supplier<byte[]> build) {
        String key = jobId + '/' + index + '.' + format;

        synchronized (this) {
            byte[] hit = entries.get(key);
            if (hit != null) {
                log.debug("Dung lai ban da dung cho {}", key);
                return hit;
            }
        }

        // Dung file NGOAI khoi dong bo: mot ban TIF mat vai giay, khoa ca bang trong luc
        // do thi moi yeu cau khac deu phai xep hang cho - ke ca nhung yeu cau chi can
        // doc mot thu da co san.
        byte[] built = build.get();

        synchronized (this) {
            // Co the co yeu cau khac vua dung xong dung file nay. Giu ban da co de hai
            // ben cung tro toi mot mang, khong nhan doi bo nho.
            byte[] raced = entries.get(key);
            if (raced != null) {
                return raced;
            }
            if (built.length <= MAX_BYTES) {
                entries.put(key, built);
                usedBytes += built.length;
                evictUntilWithinLimit();
            }
        }
        return built;
    }

    /** Bo cac ban da dung cua mot lan ghep, goi khi lan ghep do bi don di. */
    public synchronized int purgeJob(String jobId) {
        String prefix = jobId + '/';
        int removed = 0;
        Iterator<Map.Entry<String, byte[]>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, byte[]> entry = it.next();
            if (entry.getKey().startsWith(prefix)) {
                usedBytes -= entry.getValue().length;
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    /** Bo sach, dung khi don rac dinh ky. */
    public synchronized void clear() {
        entries.clear();
        usedBytes = 0;
    }

    /** Dung luong dang giu, tinh bang byte. */
    public synchronized long usedBytes() {
        return usedBytes;
    }

    private void evictUntilWithinLimit() {
        Iterator<Map.Entry<String, byte[]>> it = entries.entrySet().iterator();
        while (usedBytes > MAX_BYTES && it.hasNext()) {
            Map.Entry<String, byte[]> oldest = it.next();
            usedBytes -= oldest.getValue().length;
            it.remove();
        }
    }
}
