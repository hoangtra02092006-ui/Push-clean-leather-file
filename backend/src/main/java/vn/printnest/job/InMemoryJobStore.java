package vn.printnest.job;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ban cai dat {@link JobStore} luu trong bo nho tien trinh.
 *
 * <p>Du cho MVP mot may chu. Khoi dong lai may chu la mat het job - da luong y va duoc
 * bao cho nguoi dung bang thong bao "hay tai file len lai".
 */
@Component
public class InMemoryJobStore implements JobStore {

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    @Override
    public void save(Job job) {
        jobs.put(job.jobId(), job);
    }

    @Override
    public Optional<Job> find(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Lay moc thoi gian la luc KET THUC neu job da xong, con khong thi lay luc TAO.
     * Truoc day chi xet {@code finishedAt}, nen mot job ket o trang thai dang chay se nam
     * lai mai mai - khong nhieu, nhung du de bo nho khong bao gio ve dung muc nghi.
     */
    @Override
    public int purgeOlderThan(Duration age) {
        Instant cutoff = Instant.now().minus(age);
        int before = jobs.size();
        jobs.values().removeIf(job -> {
            Instant stamp = job.finishedAt() != null ? job.finishedAt() : job.createdAt();
            return stamp != null && stamp.isBefore(cutoff);
        });
        return before - jobs.size();
    }
}
