package vn.printnest.nesting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import vn.printnest.common.ApiErrorResponse;
import vn.printnest.common.ApiException;
import vn.printnest.common.AsyncConfig;
import vn.printnest.common.ErrorCode;
import vn.printnest.job.Job;
import vn.printnest.job.JobStore;
import vn.printnest.nesting.engine.EngineInput;
import vn.printnest.nesting.engine.NestingEngine;
import vn.printnest.nesting.model.NestResult;

/**
 * Chay thuat toan dan khuon o luong nen.
 *
 * <p>Tach thanh bean RIENG chu khong de chung voi {@link NestingService} la co y: Spring
 * hien thuc {@code @Async} bang proxy, nen mot bean tu goi phuong thuc {@code @Async} cua
 * chinh no se chay dong bo nhu thuong - loi kinh dien lam ca yeu cau HTTP bi treo vai giay.
 * Goi qua mot bean khac thi loi goi di qua proxy va thuc su chay nen.
 */
@Component
public class NestingJobRunner {

    private static final Logger log = LoggerFactory.getLogger(NestingJobRunner.class);

    private final NestingEngine engine;
    private final JobStore jobStore;

    public NestingJobRunner(NestingEngine engine, JobStore jobStore) {
        this.engine = engine;
        this.jobStore = jobStore;
    }

    /** Chay job va cap nhat trang thai; moi loi deu duoc ghi vao job chu khong nem ra ngoai. */
    @Async(AsyncConfig.NESTING_EXECUTOR)
    public void run(String jobId, EngineInput input) {
        Job job = jobStore.find(jobId).orElse(null);
        if (job == null) {
            return;
        }
        jobStore.save(job.running(10));
        long startedAt = System.currentTimeMillis();

        try {
            NestResult result = engine.nest(input);
            jobStore.save(job.done(result));
            log.info("Job {} xong sau {} ms: {} tam, {} mm, lap day {}%",
                    jobId, System.currentTimeMillis() - startedAt,
                    result.stats().totalSheets(), result.stats().totalLengthMm(),
                    Math.round(result.stats().fillRate() * 1000) / 10d);
        } catch (ApiException ex) {
            log.warn("Job {} that bai: {}", jobId, ex.getMessage());
            jobStore.save(job.failed(new ApiErrorResponse.ErrorBody(
                    ex.code().name(), ex.getMessage(), ex.details())));
        } catch (RuntimeException ex) {
            log.error("Job {} loi khong luong truoc", jobId, ex);
            jobStore.save(job.failed(new ApiErrorResponse.ErrorBody(
                    ErrorCode.NESTING_FAILED.name(),
                    "Thuat toan gap loi khong mong muon: " + ex.getMessage(), null)));
        }
    }
}
