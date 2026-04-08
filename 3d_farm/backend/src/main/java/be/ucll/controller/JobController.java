package be.ucll.controller;

import be.ucll.dto.JobDTO;
import be.ucll.model.GcodeFile;
import be.ucll.model.Job;
import be.ucll.model.STLFile;
import be.ucll.repository.GcodeFileRepository;
import be.ucll.service.JobService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;
    private final GcodeFileRepository gcodeFileRepository;

    public JobController(JobService jobService, GcodeFileRepository gcodeFileRepository) {
        this.jobService = jobService;
        this.gcodeFileRepository = gcodeFileRepository;
    }

    // ✅ Step 1: create job + save uploaded files
    @PostMapping(value = "/create", consumes = "multipart/form-data")
    public JobDTO createJobWithFiles(
            @RequestParam("jobName") String jobName,
            @RequestParam("userId") Long userId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("metadata") String metadataJson
    ) throws IOException {

        System.out.println("Received job: " + jobName + " from user: " + userId);
        System.out.println("Files count: " + files.size());

        Job job = jobService.createEmptyJob(jobName, userId);
        List<String> failedFiles = jobService.saveJobFiles(job, files, metadataJson);
        JobDTO dto = jobService.convertToDTO(job);
        dto.setFailedFiles(failedFiles);
        return dto;
    }


    @GetMapping("/latest")
    public List<JobDTO> getLatestJobs(@RequestParam(defaultValue = "50") int limit) {
        return jobService.getLatestJobs(limit);
    }

    /** Returns the list of STL files linked to a given G-code file. */
    @GetMapping("/gcode/{id}/stl-files")
    public ResponseEntity<?> getStlFilesForGcode(@PathVariable Long id) {
        GcodeFile gcode = gcodeFileRepository.findByIdWithStlFiles(id)
                .orElse(null);
        if (gcode == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(gcode.getStlFiles());
    }

    /** Downloads all STL/3MF files linked to a G-code file as a ZIP. */
    @GetMapping("/gcode/{id}/stl-files/download")
    public ResponseEntity<byte[]> downloadStlFiles(@PathVariable Long id) throws IOException {
        GcodeFile gcode = gcodeFileRepository.findByIdWithStlFiles(id)
                .orElse(null);
        if (gcode == null) return ResponseEntity.notFound().build();

        List<STLFile> stlFiles = gcode.getStlFiles();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (STLFile stl : stlFiles) {
                Path filePath = Paths.get(stl.getPath());
                if (!Files.exists(filePath)) continue;

                String ext = filePath.getFileName().toString()
                        .substring(filePath.getFileName().toString().lastIndexOf('.'));
                String entryName = stl.getName().replaceFirst("\\.[^.]+$", "") + ext;

                zos.putNextEntry(new ZipEntry(entryName));
                zos.write(Files.readAllBytes(filePath));
                zos.closeEntry();
            }
        }

        String filename = "stl-files-gcode-" + id + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(baos.toByteArray());
    }

    @PutMapping("/{id}/rename")
    public ResponseEntity<String> renameJob(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        try {
            String newName = body.get("name");
            if (newName == null || newName.isBlank()) {
                return ResponseEntity.badRequest().body("Name must not be empty");
            }
            jobService.renameJob(id, newName.trim());
            return ResponseEntity.ok("Job renamed successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteJob(@PathVariable Long id) {
        try {
            jobService.deleteJob(id);
            return ResponseEntity.ok("Job " + id + " has been deleted");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
