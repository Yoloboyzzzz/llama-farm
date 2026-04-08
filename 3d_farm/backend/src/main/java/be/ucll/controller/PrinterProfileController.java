package be.ucll.controller;

import be.ucll.model.PrinterProfile;
import be.ucll.repository.PrinterProfileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/printer-profiles")
public class PrinterProfileController {

    private final PrinterProfileRepository profileRepository;

    @Value("${printer.profiles.base-path}")
    private String basePath;

    public PrinterProfileController(PrinterProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @GetMapping
    public List<PrinterProfile> getAllProfiles() {
        return profileRepository.findAll();
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadProfile(@RequestParam("file") MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".ini")) {
            return ResponseEntity.badRequest().body("Only .ini files are accepted");
        }

        // Material = filename without extension, e.g. "PLA.ini" → "PLA"
        String material = originalFilename.substring(0, originalFilename.lastIndexOf('.')).toUpperCase();

        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Failed to read file: " + e.getMessage());
        }

        // Parse printer_model from ini content
        String printerModel = parseIniValue(content, "printer_model");
        if (printerModel == null || printerModel.isBlank()) {
            return ResponseEntity.badRequest().body("Could not find 'printer_model' in the ini file");
        }
        printerModel = printerModel.toUpperCase();

        // Parse bed dimensions
        double width = 250, depth = 210, height = 220;
        String bedShape = parseIniValue(content, "bed_shape");
        if (bedShape != null) {
            double[] dims = parseBedShape(bedShape);
            width = dims[0];
            depth = dims[1];
        }
        String maxHeight = parseIniValue(content, "max_print_height");
        if (maxHeight != null) {
            try { height = Double.parseDouble(maxHeight.trim()); } catch (NumberFormatException ignored) {}
        }

        // Save file to disk: {basePath}/{printerModel}/{material}.ini
        Path targetDir = Paths.get(basePath, printerModel);
        Path targetFile = targetDir.resolve(material + ".ini");
        try {
            Files.createDirectories(targetDir);
            Files.write(targetFile, file.getBytes());
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Failed to save file: " + e.getMessage());
        }

        // Create or update DB record
        Optional<PrinterProfile> existing = profileRepository.findByPrinterModelAndMaterial(printerModel, material);
        PrinterProfile profile = existing.orElseGet(PrinterProfile::new);
        profile.setPrinterModel(printerModel);
        profile.setMaterial(material);
        profile.setConfigPath(targetFile.toString());
        profile.setPlateWidthMm(width);
        profile.setPlateDepthMm(depth);
        profile.setPlateHeightMm(height);
        profileRepository.save(profile);

        return ResponseEntity.ok(profile);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProfile(@PathVariable Long id) {
        if (!profileRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        profileRepository.deleteById(id);
        return ResponseEntity.ok("Profile deleted");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String parseIniValue(String content, String key) {
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(key + " =") || trimmed.startsWith(key + "=")) {
                int idx = trimmed.indexOf('=');
                return trimmed.substring(idx + 1).trim();
            }
        }
        return null;
    }

    /** bed_shape = 0x0,250x0,250x210,0x210 → [maxX, maxY] */
    private double[] parseBedShape(String bedShape) {
        double maxX = 250, maxY = 210;
        for (String point : bedShape.split(",")) {
            String[] parts = point.trim().split("x");
            if (parts.length == 2) {
                try {
                    double x = Double.parseDouble(parts[0].trim());
                    double y = Double.parseDouble(parts[1].trim());
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                } catch (NumberFormatException ignored) {}
            }
        }
        return new double[]{maxX, maxY};
    }
}
