package vn.printnest.job;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import vn.printnest.common.ApiErrorResponse;
import vn.printnest.nesting.model.NestRequest;
import vn.printnest.nesting.model.NestResult;

import java.time.Instant;

/**
 * Trang thai mot lan ghep file.
 *
 * <p>Doi tuong nay bat bien: moi lan doi trang thai sinh ra ban moi. Nho vay luong HTTP
 * doc job khong bao gio nhin thay trang thai nua voi nua chin trong khi luong nen dang ghi.
 *
 * @param jobId    ma job
 * @param status   trang thai hien tai
 * @param progress tien do 0..100
 * @param request  tham so dau vao, giu lai de xuat PDF. KHONG tra ve cho client:
 *                 frontend da co san tham so nay, ma job bi hoi lai moi 700 ms nen
 *                 lap lai ca khoi tham so moi lan la phi bang thong vo ich
 * @param result   ket qua khi da xong
 * @param error    thong tin loi khi that bai
 * @param createdAt thoi diem tao
 * @param finishedAt thoi diem ket thuc
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Job(
        String jobId,
        JobStatus status,
        int progress,
        @JsonIgnore NestRequest request,
        NestResult result,
        ApiErrorResponse.ErrorBody error,
        Instant createdAt,
        Instant finishedAt
) {
    public static Job pending(String jobId, NestRequest request) {
        return new Job(jobId, JobStatus.PENDING, 0, request, null, null, Instant.now(), null);
    }

    public Job running(int progress) {
        return new Job(jobId, JobStatus.RUNNING, progress, request, null, null, createdAt, null);
    }

    public Job done(NestResult result) {
        return new Job(jobId, JobStatus.DONE, 100, request, result, null, createdAt, Instant.now());
    }

    public Job failed(ApiErrorResponse.ErrorBody error) {
        return new Job(jobId, JobStatus.FAILED, progress, request, null, error, createdAt, Instant.now());
    }
}
