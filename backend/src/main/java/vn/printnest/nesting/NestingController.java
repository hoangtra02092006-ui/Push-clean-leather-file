package vn.printnest.nesting;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.printnest.job.Job;
import vn.printnest.nesting.model.NestRequest;

/** Tao va theo doi cac lan ghep file. */
@RestController
@RequestMapping("/api/v1/nesting/jobs")
public class NestingController {

    private final NestingService nestingService;

    public NestingController(NestingService nestingService) {
        this.nestingService = nestingService;
    }

    /**
     * Tao job ghep moi.
     *
     * <p>Tra ve {@code 202 Accepted} kem {@code jobId}: cong viec that chay o luong nen,
     * frontend hoi trang thai bang endpoint ben duoi.
     */
    @PostMapping
    public ResponseEntity<Job> create(@Valid @RequestBody NestRequest request) {
        Job job = nestingService.submit(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }

    /** Trang thai va ket qua cua mot job. */
    @GetMapping("/{jobId}")
    public Job status(@PathVariable String jobId) {
        return nestingService.require(jobId);
    }
}
