package vn.printnest.nesting;

import org.springframework.stereotype.Service;
import vn.printnest.common.ApiException;
import vn.printnest.common.ErrorCode;
import vn.printnest.file.FileService;
import vn.printnest.file.StoredFile;
import vn.printnest.job.Job;
import vn.printnest.job.JobStore;
import vn.printnest.nesting.engine.EngineInput;
import vn.printnest.nesting.engine.EngineItem;
import vn.printnest.nesting.model.NestItemRequest;
import vn.printnest.nesting.model.NestRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dieu phoi mot lan ghep file: nhan yeu cau, tao job, chay thuat toan o luong nen.
 */
@Service
public class NestingService {

    private final FileService fileService;
    private final JobStore jobStore;
    private final NestingJobRunner jobRunner;

    public NestingService(FileService fileService, JobStore jobStore,
                          NestingJobRunner jobRunner) {
        this.fileService = fileService;
        this.jobStore = jobStore;
        this.jobRunner = jobRunner;
    }

    /**
     * Tao job moi va tra ve ngay lap tuc.
     *
     * <p>Tham so duoc kiem tra DONG BO o day de loi nhap lieu (thieu file, hinh rong hon
     * kho) bao ngay cho nguoi dung, thay vi bat ho cho poll roi moi biet sai.
     *
     * @param request tham so ghep
     * @return job o trang thai PENDING
     */
    public Job submit(NestRequest request) {
        EngineInput input = toEngineInput(request);
        // Chay truoc phan kiem tra nhanh de bat loi "hinh rong hon kho" ngay tai day.
        validateWidths(input);

        Job job = Job.pending(UUID.randomUUID().toString(), request);
        jobStore.save(job);
        jobRunner.run(job.jobId(), input);
        return job;
    }

    /** Doc trang thai job. */
    public Job require(String jobId) {
        return jobStore.find(jobId).orElseThrow(() -> new ApiException(ErrorCode.JOB_NOT_FOUND,
                "Khong tim thay lan ghep " + jobId + ". Co the may chu da khoi dong lai."));
    }

    /** Doc job da chay xong, kem ket qua. */
    public Job requireDone(String jobId) {
        Job job = require(jobId);
        if (job.result() == null) {
            throw new ApiException(ErrorCode.JOB_NOT_READY,
                    "Lan ghep nay chua co ket qua (trang thai: " + job.status() + ").");
        }
        return job;
    }

    /**
     * Doi yeu cau tang API sang dau vao thuan cua thuat toan.
     *
     * <p>Kich thuoc uu tien gia tri nguoi dung ghi de; khong co thi lay tu metadata file.
     * Moi {@code fileId} duoc gan mot chi so mau on dinh de preview to mau nhat quan.
     */
    private EngineInput toEngineInput(NestRequest request) {
        Map<String, Integer> categories = new LinkedHashMap<>();
        List<EngineItem> items = new ArrayList<>(request.items().size());

        for (NestItemRequest item : request.items()) {
            StoredFile file = fileService.require(item.fileId());
            double width = item.widthMm() != null ? item.widthMm() : file.widthMm();
            double height = item.heightMm() != null ? item.heightMm() : file.heightMm();
            int categoryIndex = categories.computeIfAbsent(item.fileId(), key -> categories.size());

            items.add(new EngineItem(item.fileId(), file.originalName(), width, height,
                    item.quantity(), item.allowRotate(), categoryIndex));
        }

        return new EngineInput(request.sheetWidthMm(), request.marginMm(), request.gapMm(),
                request.maxSheetLengthMm(), request.allowRotateGlobal(), items);
    }

    /** Bao loi ngay neu co hinh khong the vua kho, truoc khi tao job. */
    private void validateWidths(EngineInput input) {
        double usable = input.sheetWidthMm() - 2 * input.marginMm();
        for (EngineItem item : input.items()) {
            boolean canRotate = input.allowRotateGlobal() && item.allowRotate();
            double narrowest = canRotate
                    ? Math.min(item.widthMm(), item.heightMm())
                    : item.widthMm();
            if (narrowest > usable) {
                throw new ApiException(ErrorCode.ITEM_WIDER_THAN_SHEET,
                        "Hinh \"" + item.label() + "\" " + fmt(item.widthMm()) + "x" + fmt(item.heightMm())
                                + " cm rong hon kho " + fmt(input.sheetWidthMm()) + " cm"
                                + (canRotate ? " (da tinh ca truong hop xoay)" : " va khong duoc phep xoay") + ".",
                        Map.of("fileId", item.fileId(), "label", item.label()));
            }
        }
    }

    private static String fmt(double mm) {
        return String.valueOf(Math.round(mm / 10d * 10d) / 10d);
    }
}
