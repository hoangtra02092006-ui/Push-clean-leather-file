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

    @Override
    public int purgeOlderThan(Duration age) {
        Instant cutoff = Instant.now().minus(age);
        int before = jobs.size();
        jobs.values().removeIf(job -> job.finishedAt() != null && job.finishedAt().isBefore(cutoff));
        return before - jobs.size();
    }
}
