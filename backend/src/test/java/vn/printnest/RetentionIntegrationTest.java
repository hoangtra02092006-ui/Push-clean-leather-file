package vn.printnest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import vn.printnest.common.StorageJanitor;
import vn.printnest.file.FileService;
import vn.printnest.job.Job;
import vn.printnest.job.JobStore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kiem thu viec don rac.
 *
 * <p>Truoc day khong co gi don ca: file upload nam tren dia mai mai, con sieu du lieu,
 * anh xem truoc va ban ghi job nam trong bo nho mai mai. Ham {@code purgeOlderThan} co
 * san tu dau nhung KHONG CHO NAO GOI.
 *
 * <p>Vi vay dieu phai chung minh o day khong phai "ham co chay khong" ma la <b>no co don
 * DU BA CHO khong</b>: dia, sieu du lieu, va anh xem truoc. Quen mot cho thi bo nho van
 * phinh dan ma nhin ben ngoai khong thay gi bat thuong - dung kieu loi am tham nhat.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.storage.path=${java.io.tmpdir}/printnest-test-retention",
        // Tat lich tu dong: bai test tu goi de kiem soat thoi diem.
        "app.retention.sweep-minutes=10000",
        "logging.level.vn.printnest=WARN"
})
class RetentionIntegrationTest {

    /**
     * Tuoi am: moc cat lui ve TUONG LAI mot giay, nen moi thu deu coi la qua han.
     *
     * <p>Khong dung {@link Duration#ZERO}: no dat moc cat dung bang {@code now}, ma dau
     * thoi gian cua file/job co the trung khit {@code now} khi dong ho he dieu hanh co
     * do phan giai tho - luc do {@code isBefore} tra false va bai test do that thuong.
     */
    private static final Duration EVERYTHING = Duration.ofSeconds(-1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FileService fileService;

    @Autowired
    private JobStore jobStore;

    @Autowired
    private StorageJanitor janitor;

    @Test
    @DisplayName("Don rac xoa file ca tren dia lan trong bo nho")
    void purgeRemovesFileFromDiskAndMemory() throws Exception {
        String id = upload("de-don.pdf");
        Path onDisk = diskPathOf(id);

        assertThat(onDisk).as("file phai nam tren dia sau khi upload").exists();
        mockMvc.perform(get("/api/v1/files/{id}/preview", id)).andExpect(status().isOk());

        // EVERYTHING: moi thu deu "qua han", nen don het.
        FileService.Purge purge = fileService.purgeOlderThan(EVERYTHING);

        assertThat(purge.files()).as("so file da don").isGreaterThanOrEqualTo(1);
        assertThat(onDisk).as("file phai bien mat khoi dia").doesNotExist();

        // Sieu du lieu va anh xem truoc cung phai di theo: hoi lai thi bao khong tim thay
        // chu khong duoc tra ve anh cu dang nam trong bo nho dem.
        mockMvc.perform(get("/api/v1/files/{id}/preview", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FILE_NOT_FOUND"));
    }

    @Test
    @DisplayName("File con trong han giu thi khong bi dong toi")
    void freshFilesAreKept() throws Exception {
        String id = upload("con-moi.pdf");
        Path onDisk = diskPathOf(id);

        // Chi don nhung gi cu hon mot gio - file vua upload thi phai duoc giu.
        fileService.purgeOlderThan(Duration.ofHours(1));

        assertThat(onDisk).as("file vua upload khong duoc xoa").exists();
        mockMvc.perform(get("/api/v1/files/{id}/preview", id)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Don rac xoa ca ban ghi lan ghep")
    void purgeRemovesJobs() throws Exception {
        String fileId = upload("co-job.pdf");
        String jobId = createJob(fileId);
        awaitFinished(jobId);

        assertThat(jobStore.find(jobId)).as("job vua tao phai co").isPresent();

        int removed = jobStore.purgeOlderThan(EVERYTHING);

        assertThat(removed).isGreaterThanOrEqualTo(1);
        assertThat(jobStore.find(jobId)).as("job phai bi don di").isEmpty();
    }

    /**
     * Job ket o trang thai dang chay cung phai bi don.
     *
     * <p>Ban cu chi xet {@code finishedAt}, nen mot job khong bao gio ket thuc se nam lai
     * mai mai - khong nhieu, nhung du de bo nho khong bao gio ve dung muc nghi.
     */
    @Test
    @DisplayName("Job chua ket thuc cung bi don khi qua han")
    void unfinishedJobsArePurgedToo() {
        // Dung thang mot job gia lap chu khong chay that: chay that thi luong nen se ghi
        // de len ban ghi ngay sau khi don, lam bai test do that thuong. O day chi can
        // kiem dung mot dieu - job KHONG CO finishedAt van phai bi don.
        Job stuck = Job.pending("job-treo-mai-mai", null);
        jobStore.save(stuck);

        assertThat(jobStore.find(stuck.jobId()).orElseThrow().finishedAt()).isNull();

        jobStore.purgeOlderThan(EVERYTHING);

        assertThat(jobStore.find(stuck.jobId())).as("job treo van phai bi don").isEmpty();
    }

    @Test
    @DisplayName("Bo quet dinh ky goi duoc va khong nem loi khi khong co gi de don")
    void sweepRunsCleanlyOnEmptyStore() {
        fileService.purgeOlderThan(EVERYTHING);
        jobStore.purgeOlderThan(EVERYTHING);

        janitor.sweep();
    }

    // ------------------------------------------------------------------

    private String upload(String name) throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", name,
                MediaType.APPLICATION_PDF_VALUE, buildPdf());
        MvcResult uploaded = mockMvc.perform(multipart("/api/v1/files").file(file))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(uploaded.getResponse().getContentAsString())
                .get(0).get("id").asText();
    }

    private String createJob(String fileId) throws Exception {
        String body = """
                {
                  "sheetWidthMm": 570, "marginMm": 5, "gapMm": 3,
                  "allowRotateGlobal": true, "drawCutLines": false,
                  "items": [{"fileId": "%s", "quantity": 2, "allowRotate": true}]
                }
                """.formatted(fileId);
        MvcResult created = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/nesting/jobs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString())
                .get("jobId").asText();
    }

    /**
     * Cho toi khi job chay xong.
     *
     * <p>Ghep file chay o luong nen. Don rac ngay sau khi tao job thi luong nen ghi de
     * lai ban ghi vua bi don - o that khong xay ra (job dang chay khong the 48 gio tuoi),
     * nhung trong bai test thi lam ket qua chap chon.
     */
    private void awaitFinished(String jobId) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            if (jobStore.find(jobId).map(job -> job.finishedAt() != null).orElse(false)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("Job " + jobId + " khong chay xong trong 10 giay");
    }

    /** Duong dan that tren dia cua mot file da upload. */
    private Path diskPathOf(String id) {
        return Path.of(fileService.require(id).storedPath());
    }

    private static byte[] buildPdf() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(283.46f, 141.73f));
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.addRect(20, 20, 200, 100);
                content.fill();
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            document.save(bytes);
            return bytes.toByteArray();
        }
    }

    @SuppressWarnings("unused")
    private static void ensureDirExists(Path dir) throws IOException {
        Files.createDirectories(dir);
    }
}
