package vn.printnest.export;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiem thu bo nho dem file thanh pham.
 *
 * <p>Dung mot ban TIF mat vai giay, gan het la thoi gian nen anh. Tho hay tai mot tam de
 * xem roi moi tai ca bo .zip - khong co bo nho dem thi moi tam bi dung lai LAN THU HAI.
 *
 * <p>Nhung bo nho dem khong co tran la mot cho ro ri moi, nen phai chung minh ca hai
 * dieu: co dung lai that, VA co bo bot khi day.
 */
class ExportCacheTest {

    @Test
    @DisplayName("Lan thu hai dung lai ban da dung, khong dung lai tu dau")
    void secondCallReusesTheBuiltFile() {
        ExportCache cache = new ExportCache();
        AtomicInteger builds = new AtomicInteger();

        byte[] first = cache.get("job-1", 0, "tif", () -> {
            builds.incrementAndGet();
            return new byte[1024];
        });
        byte[] second = cache.get("job-1", 0, "tif", () -> {
            builds.incrementAndGet();
            return new byte[1024];
        });

        assertThat(builds).hasValue(1);
        assertThat(second).as("phai la dung mang do, khong phai ban sao").isSameAs(first);
    }

    @Test
    @DisplayName("Khac tam hoac khac dinh dang thi la ban khac")
    void differentSheetOrFormatIsADifferentEntry() {
        ExportCache cache = new ExportCache();
        AtomicInteger builds = new AtomicInteger();

        cache.get("job-1", 0, "tif", () -> bytes(builds));
        cache.get("job-1", 1, "tif", () -> bytes(builds));
        cache.get("job-1", 0, "pdf", () -> bytes(builds));

        assertThat(builds).as("ba khoa khac nhau thi phai dung ba lan").hasValue(3);
    }

    @Test
    @DisplayName("Don ban cua mot lan ghep khi lan ghep do bi xoa")
    void purgingAJobDropsItsFiles() {
        ExportCache cache = new ExportCache();
        cache.get("job-1", 0, "tif", () -> new byte[2048]);
        cache.get("job-2", 0, "tif", () -> new byte[2048]);

        int removed = cache.purgeJob("job-1");

        assertThat(removed).isEqualTo(1);
        assertThat(cache.usedBytes()).as("chi con ban cua job-2").isEqualTo(2048);
    }

    /**
     * Bo nho dem phai co tran.
     *
     * <p>Khong co tran thi day la mot cho ro ri bo nho moi - dung kieu loi ma he thong
     * nay vua phai sua o cho khac.
     */
    @Test
    @DisplayName("Day thi bo ban lau khong ai dung, khong phinh vo han")
    void staysWithinItsMemoryBudget() {
        ExportCache cache = new ExportCache();
        int chunk = 8 * 1024 * 1024;

        // Nhoi 40 ban x 8 MB = 320 MB, vuot han tran 200 MB.
        for (int i = 0; i < 40; i++) {
            cache.get("job-1", i, "tif", () -> new byte[chunk]);
        }

        assertThat(cache.usedBytes())
                .as("dung luong giu lai phai nam trong tran 200 MB")
                .isLessThanOrEqualTo(200L * 1024 * 1024);
    }

    @Test
    @DisplayName("Xoa sach thi khong con giu byte nao")
    void clearReleasesEverything() {
        ExportCache cache = new ExportCache();
        cache.get("job-1", 0, "tif", () -> new byte[4096]);

        cache.clear();

        assertThat(cache.usedBytes()).isZero();
    }

    private static byte[] bytes(AtomicInteger counter) {
        counter.incrementAndGet();
        return new byte[10];
    }
}
