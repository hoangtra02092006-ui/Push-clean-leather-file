package vn.printnest.file;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Tiep nhan file in va tra anh xem truoc. */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /** Tai len mot hoac nhieu file PDF / PNG / JPG. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<FileResponse> upload(@RequestParam("files") List<MultipartFile> files) {
        return fileService.store(files);
    }

    /**
     * Anh xem truoc cua mot file, da cat khoang trang va thu nho.
     *
     * <p>Kieu MIME do {@link FileService} quyet dinh chu khong co dinh: net ve tu PDF tra
     * ve PNG cho sac canh, con anh chup tra ve JPEG cho nhe.
     */
    @GetMapping("/{id}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable String id) {
        FileService.Preview preview = fileService.renderPreview(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(preview.contentType()))
                .header("Cache-Control", "public, max-age=3600")
                .body(preview.bytes());
    }
}
