package vn.printnest.job;

import java.util.Optional;

/**
 * Noi luu trang thai cac job.
 *
 * <p>Tach thanh interface de MVP dung ban in-memory, sau nay thay bang database ma khong
 * dong toi tang service.
 */
public interface JobStore {

    /** Luu hoac ghi de mot job. */
    void save(Job job);

    /** Doc mot job theo ma. */
    Optional<Job> find(String jobId);

    /** Xoa cac job da ket thuc qua lau de khong phinh bo nho. */
    int purgeOlderThan(java.time.Duration age);
}
