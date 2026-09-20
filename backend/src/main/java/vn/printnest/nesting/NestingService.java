package vn.printnest.nesting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.printnest.common.ApiException;
import vn.printnest.common.ErrorCode;
import vn.printnest.file.CavityFinder;
import vn.printnest.file.FileService;
import vn.printnest.file.OccupancyMask;
import vn.printnest.file.StoredFile;
import vn.printnest.job.Job;
import vn.printnest.job.JobStore;
import vn.printnest.nesting.engine.EngineInput;
import vn.printnest.nesting.engine.EngineItem;
import vn.printnest.nesting.engine.ShapeMask;
import vn.printnest.nesting.model.NestItemRequest;
import vn.printnest.nesting.model.NestRequest;
import vn.printnest.nesting.model.NestingMode;

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

    private static final Logger log = LoggerFactory.getLogger(NestingService.class);

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

        // Ghi lai DUNG tham so nhan duoc. Khi nguoi dung bao "doi tham so ma ket qua khong
        // doi", dong log nay tra loi ngay duoc cau hoi "giao dien co gui gia tri moi len
        // khong", thay vi phai doan.
        log.info("Nhan yeu cau ghep: che do={} kho={}mm le={}mm gap={}mm daiToiDa={} xoay={} soLoai={} soBan={}",
                request.modeOrDefault(), request.sheetWidthMm(), request.marginMm(),
                request.gapMm(), request.maxSheetLengthMm(), request.allowRotateGlobal(),
                request.items().size(),
                request.items().stream().mapToInt(NestItemRequest::quantity).sum());

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

        boolean nestInsideShapes = request.modeOrDefault() == NestingMode.FREE;

        for (NestItemRequest item : request.items()) {
            StoredFile file = fileService.require(item.fileId());
            double width = item.widthMm() != null ? item.widthMm() : file.widthMm();
            double height = item.heightMm() != null ? item.heightMm() : file.heightMm();
            int categoryIndex = categories.computeIfAbsent(item.fileId(), key -> categories.size());

            boolean sizeKept = Math.abs(width - file.widthMm()) <= 0.01
                    && Math.abs(height - file.heightMm()) <= 0.01;

            items.add(new EngineItem(item.fileId(), file.originalName(), width, height,
                    item.quantity(), item.allowRotate(), categoryIndex,
                    cavitiesFor(file, item, width, height, request.gapMm(), nestInsideShapes),
                    shapeFor(file, request.modeOrDefault(), sizeKept)));
        }

        return new EngineInput(request.sheetWidthMm(), request.marginMm(), request.gapMm(),
                request.maxSheetLengthMm(), request.allowRotateGlobal(), items,
                request.modeOrDefault() == NestingMode.TRUE_SHAPE);
    }

    /**
     * Tim cac o trong ben trong khung bao cua mot hinh, de hinh nho khac chui vao.
     *
     * <p>Chi lam o che do {@link NestingMode#FREE}. Ngoai ra con ba dieu kien phai dung
     * het, neu khong thi tra ve rong cho an toan:
     * <ul>
     *   <li>File phai co ban do chiem cho - tuc la file PDF doc duoc vector. Anh khong co.</li>
     *   <li>Nguoi dung khong duoc ghi de kich thuoc. Ghi de tuc la hinh bi keo gian so voi
     *       file goc, ban do chiem cho khong con khop nua.</li>
     *   <li>Trang khong duoc mang co xoay, vi ban do nam trong he toa do chua xoay.</li>
     * </ul>
     */
    private List<EngineItem.CavityMm> cavitiesFor(StoredFile file, NestItemRequest item,
                                                  double widthMm, double heightMm,
                                                  double gapMm, boolean enabled) {
        if (!enabled || file.occupancy() == null || file.pageRotation() != 0) {
            return List.of();
        }
        boolean resized = Math.abs(widthMm - file.widthMm()) > 0.01
                || Math.abs(heightMm - file.heightMm()) > 0.01;
        if (resized) {
            return List.of();
        }
        return CavityFinder.find(file.occupancy(), gapMm);
    }

    /**
     * Doi ban do chiem cho cua file thanh mat na hinh dang cho thuat toan xep long.
     *
     * <p>Chi lam o che do {@link NestingMode#TRUE_SHAPE}. Nhung dieu kien an toan giong
     * het truong hop hoc lom: phai co ban do vector, khong duoc ghi de kich thuoc, va
     * trang khong duoc mang co xoay. Thieu bat ky dieu nao thi tra ve null - luc do engine
     * coi hinh la khung bao dac, ket qua kem hon nhung khong bao gio sai.
     */
    private ShapeMask shapeFor(StoredFile file, NestingMode mode, boolean sizeKept) {
        if (mode != NestingMode.TRUE_SHAPE || !sizeKept || file.pageRotation() != 0) {
            return null;
        }
        OccupancyMask occupancy = file.occupancy();
        if (occupancy == null) {
            return null;
        }
        return toShapeMask(occupancy, file.widthMm(), file.heightMm());
    }

    /**
     * Lay mau lai ban do chiem cho sang luoi 1 mm ma engine dung.
     *
     * <p>Lam tron RA NGOAI: o dich duoc danh dau dac neu co bat ky phan nao cua no cham
     * vao vung dac cua ban goc. Sai ve phia coi la dac chi lam ket qua kem chut, con sai
     * ve phia coi la trong thi hai hinh de len nhau.
     */
    private ShapeMask toShapeMask(OccupancyMask occupancy, double widthMm, double heightMm) {
        double cell = 1.0;
        int cols = Math.max(1, (int) Math.ceil(widthMm / cell));
        int rows = Math.max(1, (int) Math.ceil(heightMm / cell));

        return ShapeMask.of(cols, rows, cell, (col, row) -> {
            int from = (int) Math.floor(col * cell / occupancy.cellMm());
            int to = (int) Math.ceil((col + 1) * cell / occupancy.cellMm());
            int rowFrom = (int) Math.floor(row * cell / occupancy.cellMm());
            int rowTo = (int) Math.ceil((row + 1) * cell / occupancy.cellMm());
            for (int r = rowFrom; r <= rowTo; r++) {
                for (int c = from; c <= to; c++) {
                    if (r >= 0 && c >= 0 && r < occupancy.rows() && c < occupancy.cols()
                            && occupancy.isOccupied(c, r)) {
                        return true;
                    }
                }
            }
            return false;
        });
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
