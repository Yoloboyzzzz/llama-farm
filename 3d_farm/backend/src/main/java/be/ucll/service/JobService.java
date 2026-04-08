package be.ucll.service;
import be.ucll.tasks.QueueScheduler;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;
import be.ucll.dto.GcodeFileDTO;
import be.ucll.dto.JobDTO;
import be.ucll.model.GcodeFile;
import be.ucll.model.Job;
import be.ucll.model.Printer;
import be.ucll.model.PrinterProfile;
import be.ucll.model.STLFile;
import be.ucll.model.User;
import be.ucll.repository.GcodeFileRepository;
import be.ucll.repository.JobRepository;
import be.ucll.repository.PrinterProfileRepository;
import be.ucll.repository.PrinterRepository;
import be.ucll.repository.UserRepository;
import be.ucll.service.BuildPlateNester.NestingConfig;
import be.ucll.service.BuildPlateNester.NestingResult;
import be.ucll.service.BuildPlateNester.ObjectInstance;
import be.ucll.service.BuildPlateNester.PlacedObject;
import be.ucll.service.BuildPlateNester.ScheduledPlate;
import be.ucll.util.GcodeMetadataReader;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final GcodeFileRepository gcodeFileRepository;
    private final PrinterRepository printerRepository;
    private final PrinterProfileRepository printerProfileRepository;
    private final SlicingOrchestrator slicingOrchestrator;
    private final QueueScheduler queueScheduler;

    private final Path rootUploadDir;

    public JobService(
            JobRepository jobRepository,
            UserRepository userRepository,
            GcodeFileRepository gcodeFileRepository,
            PrinterRepository printerRepository,
            PrinterProfileRepository printerProfileRepository,
            SlicingOrchestrator slicingOrchestrator,
            QueueScheduler queueScheduler
    ) {
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
        this.gcodeFileRepository = gcodeFileRepository;
        this.printerRepository = printerRepository;
        this.printerProfileRepository = printerProfileRepository;
        this.slicingOrchestrator = slicingOrchestrator;
        this.queueScheduler = queueScheduler;

        String baseDir = System.getProperty("user.dir");
        if (baseDir == null) baseDir = new File(".").getAbsolutePath();

        this.rootUploadDir = Paths.get(baseDir, "files");

        try {
            Files.createDirectories(rootUploadDir);
            System.out.println("✅ Root upload directory: " + rootUploadDir.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create upload directory: " + rootUploadDir, e);
        }
    }

    // ---------------- Existing methods kept ----------------

    public List<JobDTO> getNewest50Jobs() {
        return jobRepository.findTop50ByOrderByCreatedAtDesc()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public Job createEmptyJob(String jobName, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Job job = new Job();
        String cleanedName = jobName.replaceAll("_.*", "");
        job.setName(cleanedName);
        job.setStatus("pending");
        job.setUser(user);

        return jobRepository.save(job);
    }

    // ------------- MAIN: save files + slice + queue ----------------

    public List<String> saveJobFiles(Job job, List<MultipartFile> uploadedFiles, String metadataJson) throws IOException {
        Path jobFolder = rootUploadDir.resolve(String.valueOf(job.getId()));
        Files.createDirectories(jobFolder);

        List<STLFile> stlFiles = new ArrayList<>();
        List<GcodeFile> gcodeFiles = new ArrayList<>();

        // Parse metadata JSON
        List<FileMetadata> metadataList = new ArrayList<>();
        if (metadataJson != null && !metadataJson.isBlank()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                metadataList = List.of(mapper.readValue(metadataJson, FileMetadata[].class));
            } catch (Exception e) {
                System.err.println("⚠️ Failed to parse file metadata JSON: " + e.getMessage());
            }
        }
        
        // Create a default slicer for initial STL analysis
        SlicerService defaultSlicer = new SlicerService(
            "C:/Program Files/Prusa3D/PrusaSlicer/prusa-slicer-console.exe",
            Paths.get("C:/Users/FabLab_Leuven/Documents/prusa_scripts/CLI/server/MarcMK4_skirt.ini"),
            Paths.get("C:/Users/FabLab_Leuven/Downloads/work")
        );

        int nextPosition = gcodeFileRepository.findMaxQueuePosition() + 1;

        // 1) Save uploaded files and analyze STLs
        for (MultipartFile file : uploadedFiles) {
            String originalName = file.getOriginalFilename();
            if (originalName == null || originalName.isBlank()) continue;

            String cleanName = Paths.get(originalName).getFileName().toString();
            Path filePath = jobFolder.resolve(cleanName);
            File destinationFile = filePath.toFile();
            destinationFile.getParentFile().mkdirs();
            file.transferTo(destinationFile);

            String extension = cleanName.substring(cleanName.lastIndexOf('.') + 1).toLowerCase();

            FileMetadata meta = metadataList.stream()
                    .filter(m -> cleanName.equals(m.getFilename()))
                    .findFirst()
                    .orElse(new FileMetadata());

            // CORRECTED STL ANALYSIS SECTION - Replace from line ~137 to ~260

            if (extension.equals("stl")) {
                STLFile stl = new STLFile();
                
                // Sanitize filename - remove spaces and special characters
                String sanitizedName = cleanName.replaceAll("[^a-zA-Z0-9._-]", "_");
                Path sanitizedFilePath = jobFolder.resolve(sanitizedName);
                
                // If we renamed the file, move it
                if (!sanitizedName.equals(cleanName)) {
                    Files.move(filePath, sanitizedFilePath);
                    System.out.println("  📝 Renamed: " + cleanName + " → " + sanitizedName);
                    filePath = sanitizedFilePath;
                }
                
                stl.setName(sanitizedName);
                stl.setPath(filePath.toString());
                stl.setColor(meta.getColor());
                stl.setMaterial(meta.getMaterial());
                try {
                    stl.setInfill(Integer.parseInt(meta.getInfill()));
                } catch (Exception e) {
                    stl.setInfill(20);
                }
                stl.setBrim(parseBoolean(meta.getBrim()));
                String normalizedSupport = normalizeSupportType(meta.getSupport());
                System.out.println(normalizedSupport);
                stl.setSupport(normalizedSupport); 
                stl.setInstances(meta.getInstances() > 0 ? meta.getInstances() : 1);
                stl.setStatus("waiting");
                stl.setJob(job);
                
                // Analyze STL for dimensions and time
                Path tempGcodePath = Paths.get(filePath.toString().replaceAll("(?i)\\.stl$", ".gcode"));
                Path threeMFPath = Paths.get(filePath.toString().replaceAll("(?i)\\.stl$", ".3mf"));
                
                try {
                    // Ensure output directory exists
                    Files.createDirectories(tempGcodePath.getParent());
                    Files.createDirectories(threeMFPath.getParent());
                    
                    System.out.println("  🔍 Analyzing: " + sanitizedName);
                    
                    defaultSlicer.exportStlTo3mf(Paths.get(stl.getPath()), threeMFPath);
                    stl.setPath(threeMFPath.toString());
                    
                    defaultSlicer.edit3mfSettings(
                        threeMFPath, 
                        stl.getInfill(), 
                        stl.getBrim() ? "yes" : "no", 
                        stl.getSupport(),
                        0.0, 0.0, 0.0
                    );
                    
                    System.out.println("  🔪 Test slicing...");
                    defaultSlicer.slice3mfToGcode(threeMFPath, tempGcodePath);
                    
                    System.out.println("  ✅ Sliced successfully");
                    
                    // Extract metadata from successfully sliced file
                    double[] bounds = defaultSlicer.analyzeGcodeBounds(tempGcodePath);
                    System.out.println("DEBUG bounds: minX=" + bounds[0] + ", maxX=" + bounds[1] + 
                   ", minY=" + bounds[2] + ", maxY=" + bounds[3] + ", maxZ=" + bounds[4]);
                    stl.setMinX(bounds[0]);
                    stl.setMinY(bounds[2]);
                    stl.setWidthMm(Math.ceil(bounds[1] - bounds[0])+3.0);   // ← Rounds UP
                    stl.setDepthMm(Math.ceil(bounds[3] - bounds[2])+3.0);   // ← Rounds UP
                    int printTimeSeconds = defaultSlicer.extractPrintTimeSeconds(tempGcodePath);
                    stl.setPrintTimeSeconds(printTimeSeconds);
                    stl.setHeightMm(bounds[4]);
                    
                    // Calculate support offset if supports are enabled
                    if (!normalizedSupport.equalsIgnoreCase("off")) {
                        System.out.println("  📐 Calculating support offset...");
                        double[] offsets = defaultSlicer.calculateSupportOffset(
                            filePath, // Use original STL file path
                            stl.getInfill(),
                            stl.getBrim() ? "yes" : "no",
                            normalizedSupport
                        );
                        stl.setOffsetX(offsets[0]);
                        stl.setOffsetY(offsets[1]);
                        System.out.println("  ✅ Support offset: X=" + offsets[0] + "mm, Y=" + offsets[1] + "mm");
                    } else {
                        // No supports, so offset is 0
                        stl.setOffsetX(0.0);
                        stl.setOffsetY(0.0);
                        System.out.println("  ℹ️  No supports - offset: X=0.0mm, Y=0.0mm");
                    }
                    
                } catch (Exception e) {
                    System.err.println("❌ Failed to analyze " + sanitizedName + ": " + e.getMessage());

                    // Check if it's a PrusaSlicer crash
                    if (e.getMessage() != null &&
                        (e.getMessage().contains("-1073741819") ||
                         e.getMessage().contains("0xC0000005") ||
                         e.getMessage().contains("Access Violation"))) {
                        System.err.println("⚠️  PrusaSlicer CRASHED on this file!");
                        System.err.println("   This STL has geometry that triggers a PrusaSlicer bug.");
                        System.err.println("   SKIPPING this file and continuing with others...");
                        stl.setStatus("error - prusaslicer crash");
                    } else {
                        System.err.println("   SKIPPING this file and continuing with others...");
                        stl.setStatus(shortErrorStatus(e.getMessage()));
                    }
                    
                    e.printStackTrace();
                    
                    // Set default values for failed analysis
                    stl.setMinX(0.0);
                    stl.setMinY(0.0);
                    stl.setWidthMm(50);
                    stl.setDepthMm(50);
                    stl.setHeightMm(50);
                    stl.setPrintTimeSeconds(3600);
                    stl.setOffsetX(0.0);
                    stl.setOffsetY(0.0);
                    
                    // Add file with error status but don't throw - continue with other files
                    stlFiles.add(stl);
                    continue; // Skip to next file
                }
                
                stlFiles.add(stl);

            } else if (extension.equals("gcode")) {
                int numInstances = Math.max(meta.getInstances(), 1);

                for (int i = 1; i <= numInstances; i++) {
                    GcodeFile gcode = new GcodeFile();
                    
                    // Sanitize filename
                    String baseName = cleanName.replace(".gcode", "");
                    String sanitizedBase = baseName.replaceAll("[^a-zA-Z0-9._-]", "_");
                    String instanceName = numInstances > 1 
                        ? sanitizedBase + "_copy" + i + ".gcode"
                        : sanitizedBase + ".gcode";

                    gcode.setName(instanceName);
                    gcode.setPath(filePath.toString());
                    gcode.setColor(meta.getColor());
                    gcode.setInstances(1);
                    gcode.setStatus("waiting");
                    gcode.setJob(job);

                    gcode.setQueuePostion(nextPosition++);

                    try {
                        GcodeMetadataReader.GcodeInfo info = GcodeMetadataReader.extractInfo(filePath.toString());
                        gcode.setDuration(info.durationSeconds);
                        gcode.setModel(info.printerModel);
                        gcode.setMaterial(info.filamentType);
                        gcode.setWeight((int) Math.ceil(info.filamentUsedGrams));
                    } catch (Exception e) {
                        System.err.println("⚠️ Failed to read G-code metadata for " + cleanName + ": " + e.getMessage());
                    }
                    gcodeFiles.add(gcode);
                }
            }
        }

        job.setStlFiles(stlFiles);
        job.setGcodeFiles(gcodeFiles);
        jobRepository.save(job);

        // Collect all failed file names to return to caller
        List<String> failedFiles = new ArrayList<>();
        stlFiles.stream()
                .filter(stl -> stl.getStatus().startsWith("error"))
                .map(STLFile::getName)
                .forEach(failedFiles::add);

        // 2) Intelligent nesting & scheduling per (material, color) group
        List<GcodeFile> generatedGcodes = new ArrayList<>();

        // Only nest STLs that passed initial analysis
        List<STLFile> validStlFiles = stlFiles.stream()
                .filter(stl -> !stl.getStatus().startsWith("error"))
                .collect(Collectors.toList());

        if (!validStlFiles.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            List<Printer> allPrinters = printerRepository.findByEnabledTrue();

            // Define deadlines
            LocalTime firstDeadlineTime = LocalTime.of(16, 0);
            LocalTime secondDeadlineTime = LocalTime.of(8, 0);

            LocalDateTime firstDeadline = now.toLocalDate().atTime(firstDeadlineTime);
            if (firstDeadline.isBefore(now)) {
                firstDeadline = firstDeadline.plusDays(1);
            }
            LocalDateTime secondDeadline = firstDeadline.toLocalDate().plusDays(1).atTime(secondDeadlineTime);

            // Group STLs by material+color
            Map<String, List<STLFile>> groups = validStlFiles.stream()
                    .collect(Collectors.groupingBy(
                            stl -> (stl.getMaterial() + "|" + stl.getColor())
                    ));

            int plateCounter = 1;
            
            for (var entry : groups.entrySet()) {
                List<STLFile> groupStls = entry.getValue();
                if (groupStls.isEmpty()) continue;

                String material = groupStls.get(0).getMaterial();
                String color = groupStls.get(0).getColor();

                // All printers with matching material+color (Phase 1 & 3)
                List<Printer> printersForGroup = allPrinters.stream()
                        .filter(p -> materialEquals(p.getMaterial(), material)
                                && colorEquals(p.getColor(), color))
                        .collect(Collectors.toList());

                // Idle-only subset for Phase 2 (deadline run)
                List<Printer> idlePrintersForGroup = printersForGroup.stream()
                        .filter(p -> "idle".equalsIgnoreCase(p.getStatus()))
                        .collect(Collectors.toList());

                if (printersForGroup.isEmpty()) {
                    System.out.println("⚠️ No printers loaded with " + material + " " + color + " for job " + job.getId());

                    for (STLFile stl : groupStls) {
                        stl.setStatus("error - no printer available");
                    }
                    continue;
                }

                // Configure nesting
                NestingConfig config = new NestingConfig(
                    printersForGroup,
                    idlePrintersForGroup,
                    now,
                    firstDeadline,
                    secondDeadline
                );

                // Run nesting algorithm
                System.out.println("\n🔧 Nesting " + groupStls.size() + " STL(s) for " + 
                                 material + " " + color + "...");
                
                NestingResult nestingResult = BuildPlateNester.nestAndSchedule(groupStls, config);
                
                // Print report
                String report = BuildPlateNester.generateReport(nestingResult, config);
                System.out.println(report);

                // Slice each scheduled plate with printer-specific config
                for (ScheduledPlate scheduledPlate : nestingResult.scheduledPlates) {
                    try {
                        // Get printer-specific config from database
                        Printer assignedPrinter = scheduledPlate.printer;
                        Path printerConfigPath = getPrinterConfigPath(assignedPrinter, material);
                        
                        // Create slicer with printer-specific config
                        SlicerService printerSlicer = new SlicerService(
                            "C:/Program Files/Prusa3D/PrusaSlicer/prusa-slicer-console.exe",
                            printerConfigPath,
                            Paths.get("C:/Users/FabLab_Leuven/Downloads/work")
                        );
                        
                        // Create ObjectConfigs for SlicerService
                        List<SlicerService.ObjectConfig> objectConfigs = new ArrayList<>();
                        
                        for (PlacedObject placed : scheduledPlate.plate.objects) {
                            // FR-004: null-coalesce minX/minY to 0.0 (objects uploaded before fix)
                            Double rawMinX = placed.stlFile.getMinX();
                            Double rawMinY = placed.stlFile.getMinY();
                            double safeMinX = rawMinX != null ? rawMinX.doubleValue() : 0.0;
                            double safeMinY = rawMinY != null ? rawMinY.doubleValue() : 0.0;
                            SlicerService.ObjectConfig configuration = new SlicerService.ObjectConfig(
                                Paths.get(placed.stlFile.getPath()),
                                placed.x,   // nesting-grid coordinate — offsetX already encoded in widthMm
                                placed.y,   // nesting-grid coordinate — offsetY already encoded in depthMm
                                0.0,
                                placed.stlFile.getInfill(),
                                placed.stlFile.getBrim() ? "yes" : "no",
                                placed.stlFile.getSupport() != null ? placed.stlFile.getSupport() : "off",
                                safeMinX,   // stored G-code origin (FR-002)
                                safeMinY
                            );
                            objectConfigs.add(configuration);
                        }

                        // Output paths for this plate
                        String plateName = String.format("plate_%03d_%s_%s_%s", 
                            plateCounter++, 
                            assignedPrinter.getName().replaceAll("[^a-zA-Z0-9]", "_"),
                            material, 
                            color);
                        Path output3mf = jobFolder.resolve(plateName + ".3mf");
                        Path outputGcode = jobFolder.resolve(plateName + ".gcode");

                        // Slice the combined plate
                        System.out.println("✂️  Slicing " + plateName);
                        System.out.println("   Objects: " + objectConfigs.size());
                        System.out.println("   Printer: " + assignedPrinter.getName() + " (" + assignedPrinter.getModel() + ")");
                        
                        SlicerService.SliceResult sliceResult = printerSlicer.sliceMultipleObjects(
                            objectConfigs,
                            output3mf,
                            outputGcode
                        );

                        // Collect any per-object failures from this plate
                        if (!sliceResult.failedObjectIds.isEmpty()) {
                            System.err.println("⚠️ " + sliceResult.failedObjectIds.size() +
                                    " object(s) skipped in plate " + plateName + ": " + sliceResult.failedObjectIds);
                            failedFiles.addAll(sliceResult.failedObjectIds);
                            // Mark the corresponding STL files as failed
                            for (PlacedObject placed : scheduledPlate.plate.objects) {
                                if (sliceResult.failedObjectIds.contains(placed.stlFile.getName().replace(".stl", "")
                                        .replaceAll("[^a-zA-Z0-9._-]", "_"))) {
                                    placed.stlFile.setStatus("error - slicing failed");
                                }
                            }
                        }

                        // FR-007: warn when actual G-code footprint exceeds stored dims
                        try {
                            double[] plateBounds = printerSlicer.analyzeGcodeBounds(outputGcode);
                            double actualW = plateBounds[1] - plateBounds[0];
                            double actualD = plateBounds[3] - plateBounds[2];
                            for (PlacedObject placed : scheduledPlate.plate.objects) {
                                if (actualW > placed.stlFile.getWidthMm() ||
                                        actualD > placed.stlFile.getDepthMm()) {
                                    System.out.printf("⚠️ G-code footprint larger than stored dims for %s: " +
                                            "stored=%.1fx%.1f actual=%.1fx%.1f%n",
                                            placed.stlFile.getName(),
                                            placed.stlFile.getWidthMm(), placed.stlFile.getDepthMm(),
                                            actualW, actualD);
                                }
                            }
                        } catch (Exception ignored) {
                            // FR-007 check is advisory — never block slicing
                        }

                        // Create G-code file entity
                        GcodeFile gf = new GcodeFile();
                        gf.setName(plateName + ".gcode");
                        gf.setPath(outputGcode.toString());
                        gf.setStatus("waiting");
                        gf.setInstances(1);
                        gf.setQueuePostion(nextPosition++);
                        gf.setColor(color);
                        gf.setMaterial(material);
                        gf.setDuration(sliceResult.printTimeSeconds);
                        gf.setModel(assignedPrinter.getModel());

                        try {
                            GcodeMetadataReader.GcodeInfo info = GcodeMetadataReader.extractInfo(outputGcode.toString());
                            gf.setWeight((int) Math.ceil(info.filamentUsedGrams));
                        } catch (Exception e) {
                            System.err.println("⚠️ Could not read weight from G-code");
                            gf.setWeight(0);
                        }

                        gf.setStartedAt(null);
                        gf.setJob(job);

                        // Link the distinct STL files from this plate to the GcodeFile
                        List<STLFile> plateStls = scheduledPlate.plate.objects.stream()
                            .map(placed -> placed.stlFile)
                            .distinct()
                            .collect(Collectors.toList());
                        gf.setStlFiles(plateStls);

                        generatedGcodes.add(gf);

                        System.out.println("✅ Sliced successfully");
                        System.out.println("   Duration: " + sliceResult.printTimeFormatted);
                        System.out.println("   Scheduled: " + scheduledPlate.startTime + " → " + scheduledPlate.endTime);
                        System.out.println();

                    } catch (Exception e) {
                        System.err.println("❌ Failed to slice plate: " + e.getMessage());
                        e.printStackTrace();
                        // Mark all objects on this plate as failed
                        for (PlacedObject placed : scheduledPlate.plate.objects) {
                            placed.stlFile.setStatus("error - slicing failed");
                            failedFiles.add(placed.stlFile.getName());
                        }
                    }
                }

                // Mark STLs based on scheduling result
                for (STLFile stl : groupStls) {
                    boolean wasScheduled = nestingResult.scheduledPlates.stream()
                        .anyMatch(sp -> sp.plate.objects.stream()
                            .anyMatch(obj -> obj.stlFile.getId().equals(stl.getId())));
                    
                    if (wasScheduled) {
                        stl.setStatus("sliced");
                    } else {
                        stl.setStatus("unscheduled - no capacity");
                    }
                }
                
                // Warn about unscheduled objects
                if (!nestingResult.unscheduledObjects.isEmpty()) {
                    System.out.println("\n⚠️  WARNING: " + nestingResult.unscheduledObjects.size() + 
                                     " objects could not be scheduled!");
                    for (ObjectInstance obj : nestingResult.unscheduledObjects) {
                        System.out.println("   - " + obj.stlFile.getName() + " (copy " + 
                                         obj.instanceNumber + ")");
                    }
                }
            }

            job.getGcodeFiles().addAll(generatedGcodes);
        }

        // 3) Rename all G-code files
        renameGcodeFiles(job, job.getGcodeFiles());
        job.setStatus("queued");
        jobRepository.save(job);

        System.out.println("✅ Saved " + uploadedFiles.size() + " file(s) for job " + job.getId());

        return failedFiles;
    }

    @Transactional
    public void deleteJob(Long id) {
        Job job = jobRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Job not found with id: " + id));
        
        // Count files before deletion
        int fileCount = job.getGcodeFiles() != null ? job.getGcodeFiles().size() : 0;
        
        // Delete the job (cascade will handle gcode_files and stl_files)
        jobRepository.delete(job);
        
        System.out.println("✅ Deleted job: " + job.getName() + " (ID: " + id + ")");
        System.out.println("   Unlinked " + fileCount + " G-code files (files remain on disk)");
    }

    /**
     * Get the config file path for a specific printer and material
     * Uses PrinterProfile database lookup
     */
    private Path getPrinterConfigPath(Printer printer, String material) {
        PrinterProfile profile = printerProfileRepository
            .findByPrinterModelAndMaterial(printer.getModel(), material)
            .orElseThrow(() -> new RuntimeException(
                String.format("❌ No PrinterProfile found for model='%s' material='%s'. " +
                             "Please add an entry to the printer_profiles table.",
                             printer.getModel(), material)
            ));
        
        if (profile.getConfigPath() == null || profile.getConfigPath().isEmpty()) {
            throw new RuntimeException(
                "PrinterProfile id=" + profile.getId() + " has no configPath set"
            );
        }
        
        Path configPath = Paths.get(profile.getConfigPath());
        
        if (!Files.exists(configPath)) {
            throw new RuntimeException(
                String.format("Config file not found: %s (from PrinterProfile id=%d)",
                             configPath, profile.getId())
            );
        }
        
        System.out.println(String.format("  📋 Config: %s [%s/%s]",
            configPath.getFileName(), printer.getModel(), material));
        
        return configPath;
    }

    /**
     * Normalize support type to valid PrusaSlicer values
     * Valid values: "off", "snug", "organic", "grid"
     */
    private String normalizeSupportType(String support) {
        if (support == null || support.isEmpty()) {
            return "off";
        }
        
        String lower = support.toLowerCase().trim();
        
        // Map common values to valid types
        switch (lower) {
            case "off":
            case "no":
            case "none":
            case "false":
                return "off";
            
            case "snug":
                return "snug";
            
            case "organic":
            case "tree":
                return "organic";
            
            case "on":
            case "yes":
            case "true":
            case "grid":
            default:
                return "snug"; // Default to grid supports
        }
    }

    // Material/color equality helpers
    private boolean materialEquals(String a, String b) {
        if (a == null || b == null) return false;
        return a.trim().equalsIgnoreCase(b.trim());
    }

    private boolean colorEquals(String a, String b) {
        if (a == null || b == null) return false;
        return a.trim().equalsIgnoreCase(b.trim());
    }

    // ---------------- public rename (job + gcodes) ----------------

    @Transactional
    public void renameJob(Long jobId, String newName) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found with id: " + jobId));

        String oldName = job.getName();
        job.setName(newName);

        if (job.getGcodeFiles() != null) {
            for (GcodeFile gf : job.getGcodeFiles()) {
                String currentName = gf.getName();
                // Strip .gcode extension, find last 6 underscores, replace prefix
                String withoutExt = currentName.endsWith(".gcode")
                        ? currentName.substring(0, currentName.length() - 6)
                        : currentName;

                // Find the position of the 6th-from-last underscore
                int underscoreCount = 0;
                int pos = withoutExt.length();
                while (pos > 0 && underscoreCount < 6) {
                    pos--;
                    if (withoutExt.charAt(pos) == '_') underscoreCount++;
                }

                if (underscoreCount == 6) {
                    // Replace everything before pos with the new name
                    String suffix = withoutExt.substring(pos); // starts with "_"
                    gf.setName(newName + suffix + ".gcode");
                } else {
                    // Fallback: replace old job name prefix if present
                    if (currentName.startsWith(oldName)) {
                        gf.setName(newName + currentName.substring(oldName.length()));
                    }
                }
            }
        }

        jobRepository.save(job);
    }

    // ---------------- rename logic (kept) ----------------

    private void renameGcodeFiles(Job job, List<GcodeFile> gcodeFiles) {
        long totalCombinations = gcodeFiles.stream()
                .map(f -> f.getMaterial() + "_" + f.getColor())
                .distinct()
                .count();

        var grouped = gcodeFiles.stream()
                .collect(Collectors.groupingBy(f -> f.getMaterial() + "_" + f.getColor()));

        for (var entry : grouped.entrySet()) {
            List<GcodeFile> group = entry.getValue();
            int totalPerCombo = group.size();

            for (int i = 0; i < totalPerCombo; i++) {
                GcodeFile f = group.get(i);
                String newName = String.format(
                        "%s_%s_%d_of_%d_of_%d.gcode",
                        job.getName(),
                        f.getColor(),
                        i + 1,
                        totalPerCombo,
                        totalCombinations
                );
                f.setName(newName);
            }
        }
    }

    // ------------- existing DTO / helpers --------------

    public JobDTO convertToDTO(Job job) {
        return new JobDTO(
                job.getId(),
                job.getName(),
                job.getStatus(),
                job.getCreatedAt(),
                job.getUser() != null ? job.getUser().getName() : null,
                job.getUser() != null ? job.getUser().getEmail() : null,
                job.getGcodeFiles() != null
                        ? job.getGcodeFiles().stream()
                        .map(g -> new GcodeFileDTO(
                                g.getId(),
                                g.getName(),
                                g.getStatus(),
                                g.getStartedAt(),
                                g.getDuration(),
                                "/api/gcode-files/" + g.getId() + "/download"
                        ))
                        .collect(Collectors.toList())
                        : List.of()
        );
    }

    /** Returns a status string that fits in VARCHAR(50). Uses the first line of msg, truncated. */
    private String shortErrorStatus(String msg) {
        if (msg == null) return "error - unknown";
        String firstLine = msg.contains("\n") ? msg.substring(0, msg.indexOf('\n')) : msg;
        String status = "error - " + firstLine;
        return status.length() > 50 ? status.substring(0, 50) : status;
    }

    private boolean parseBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof String s)
            return s.equalsIgnoreCase("yes") || s.equalsIgnoreCase("true");
        return false;
    }

    public List<JobDTO> getLatestJobs(int limit) {
        return jobRepository.findTopNByOrderByCreatedAtDesc(limit)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public JobDTO getJobById(Long id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Job not found"));
        return toDTO(job);
    }

    public JobDTO toDTO(Job job) {
        JobDTO dto = new JobDTO();
        dto.setId(job.getId());
        dto.setName(job.getName());
        dto.setStatus(job.getStatus());
        dto.setCreatedAt(job.getCreatedAt());
        dto.setUserName(job.getUser() != null ? job.getUser().getName() : "Unknown");
        dto.setUserEmail(job.getUser() != null ? job.getUser().getEmail() : "");
        dto.setGcodeFiles(
                job.getGcodeFiles().stream()
                        .map(file -> {
                            GcodeFileDTO f = new GcodeFileDTO();
                            f.setId(file.getId());
                            f.setFilename(file.getName());
                            f.setStatus(file.getStatus());
                            f.setStartedAt(file.getStartedAt());
                            f.setDurationSeconds(file.getDuration());
                            f.setDownloadUrl("/api/gcode-files/" + file.getId() + "/download");
                            return f;
                        })
                        .collect(Collectors.toList())
        );
        return dto;
    }

    public static class FileMetadata {
        private String filename;
        private String material;
        private String color;
        private String infill;
        private String brim;
        private String support;
        private int instances;

        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }
        public String getMaterial() { return material; }
        public void setMaterial(String material) { this.material = material; }
        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
        public String getInfill() { return infill; }
        public void setInfill(String infill) { this.infill = infill; }
        public String getBrim() { return brim; }
        public void setBrim(String brim) { this.brim = brim; }
        public String getSupport() { return support; }
        public void setSupport(String support) { this.support = support; }
        public int getInstances() { return instances; }
        public void setInstances(int instances) { this.instances = instances; }
    }
}