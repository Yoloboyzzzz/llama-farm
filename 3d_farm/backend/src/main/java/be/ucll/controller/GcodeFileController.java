package be.ucll.controller;

import be.ucll.model.GcodeFile;
import be.ucll.repository.GcodeFileRepository;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import be.ucll.service.GcodeFileService;

import java.io.File;

@RestController
@RequestMapping("/api/gcode-files")
public class GcodeFileController {

    private final GcodeFileRepository gcodeFileRepository;
    private final GcodeFileService gcodeFileService;

    public GcodeFileController(GcodeFileRepository gcodeFileRepository, GcodeFileService gcodeFileService) {
        this.gcodeFileRepository = gcodeFileRepository;
        this.gcodeFileService = gcodeFileService;
    }

    // ✅ Download a file
    @GetMapping("/{id}/download")
    public ResponseEntity<FileSystemResource> downloadFile(@PathVariable Long id) {
        GcodeFile file = gcodeFileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("File not found"));

        File gcodeFile = new File(file.getPath());
        if (!gcodeFile.exists()) {
            return ResponseEntity.notFound().build();
        }

        FileSystemResource resource = new FileSystemResource(gcodeFile);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + file.getName())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @PostMapping("/{id}/requeue")
    public ResponseEntity<String> requeueFile(@PathVariable Long id) {
        try {
            gcodeFileService.requeueFile(id);
            return ResponseEntity.ok("G-code file " + id + " has been requeued");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Delete a G-code file - unlinks it from the job
     * The actual file remains on disk
     * DELETE /api/gcode-files/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteFile(@PathVariable Long id) {
        try {
            gcodeFileService.deleteFile(id);
            return ResponseEntity.ok("G-code file " + id + " has been deleted");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
