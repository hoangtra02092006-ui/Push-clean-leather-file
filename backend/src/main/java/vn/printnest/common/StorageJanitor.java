package vn.printnest.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.printnest.file.FileService;
import vn.printnest.job.JobStore;

import java.time.Duration;

/**
 * Don rac dinh ky: xoa file va ban ghi job da qua han giu.
 *
 * <p><b>Vi sao phai co.</b> Truoc day khong co gi don ca. File upload nam lai tren dia
 * mai mai, con sieu du lieu, anh xem truoc va ban ghi job thi nam lai trong bo nho mai
 * mai. Ham {@code purgeOlderThan} da ton tai tu dau nhung KHONG CHO NAO GOI - no chi la
 * mot loi hua chua ai thuc hien.
 *
 * <p>Hau qua khong den ngay ma den dan: co gan volume thi dia day dan roi dung han;
 * khong gan volume thi bo nho phinh toi luc JVM het cho va tu khoi dong lai - mat sach
 * file dang lam do giua chung, dung vao luc dang ban nhat.
 *
 * <p><b>Job dang chay thi sao.</b> Neu don trung mot job dang chay, luong nen sau do se
 * ghi lai ban ghi do voi dau thoi gian moi - tuc la no chi bi don o lan quet sau, khong
 * ro ri gi. O that dieu nay khong xay ra vi mot job dang chay khong the 48 gio tuoi.
 *
 * <p><b>Thu tu don co chu y:</b> don job TRUOC roi moi don file. Job giu tham so de xuat
 * lai PDF; neu xoa file truoc ma job van con, se co mot khoang thoi gian job ton tai
 * nhung file nguon da bay - tho bam tai PDF se gap loi kho hieu thay vi loi "khong tim
 * thay lan ghep" ro rang.
 */
@Component
public class StorageJanitor {

    private static final Logger log = LoggerFactory.getLogger(StorageJanitor.class);

    private final FileService fileService;
    private final JobStore jobStore;
    private final AppProperties properties;

    public StorageJanitor(FileService fileService, JobStore jobStore, AppProperties properties) {
        this.fileService = fileService;
        this.jobStore = jobStore;
        this.properties = properties;
    }

    /**
     * Quet mot lan.
     *
     * <p>Chu ky doc tu {@code app.retention.sweep-minutes}. Dung {@code fixedDelay} chu
     * khong phai {@code fixedRate}: dem tu luc quet truoc KET THUC, nen mot lan quet cham
     * khong lam cac lan sau don len nhau.
     */
    @Scheduled(
            fixedDelayString = "${app.retention.sweep-minutes:60}",
            initialDelayString = "${app.retention.sweep-minutes:60}",
            timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    public void sweep() {
        AppProperties.Retention retention = properties.retention();
        if (retention == null || !retention.enabled()) {
            return;
        }

        Duration age = Duration.ofHours(retention.hours());
        int jobs = jobStore.purgeOlderThan(age);
        FileService.Purge purge = fileService.purgeOlderThan(age);

        // Chi ghi log khi that su co don duoc gi. Quet moi gio ma lan nao cung ghi mot
        // dong thi log day nhung dong vo nghia, den luc can tim thi khong thay gi.
        if (jobs > 0 || purge.files() > 0) {
            log.info("Don rac: xoa {} lan ghep va {} file, giai phong {} MB (giu lai {} gio gan nhat)",
                    jobs, purge.files(), purge.freedBytes() / (1024 * 1024), retention.hours());
        }
    }
}
