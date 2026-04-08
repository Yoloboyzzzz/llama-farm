package be.ucll.service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Service class for PrusaSlicer operations and 3MF manipulation.
 * Provides step-by-step control over the slicing workflow.
 */
public class SlicerService {

    private final String prusaSlicerPath;
    private final Path iniConfigPath;
    private final Path workingDirectory;

    /**
     * Configuration for a single object in a 3MF file
     */
    public static class ObjectConfig {
        public Path stlPath;
        public double targetX, targetY, targetZ;
        public int infill;
        public String brim;           // "yes" or "no"
        public String supportType;    // "snug", "organic", or "off"
        public double storedMinX;     // G-code minX at origin from DB (FR-002)
        public double storedMinY;     // G-code minY at origin from DB (FR-002)

        // Calculated values (populated during processing)
        public String objectId;
        public int objectIndex;
        public double actualX, actualY, actualZ;

        public ObjectConfig(Path stlPath, double targetX, double targetY, double targetZ,
                          int infill, String brim, String supportType,
                          double storedMinX, double storedMinY) {
            this.stlPath = stlPath;
            this.targetX = targetX;
            this.targetY = targetY;
            this.targetZ = targetZ;
            this.infill = infill;
            this.brim = brim;
            this.supportType = supportType;
            this.storedMinX = storedMinX;
            this.storedMinY = storedMinY;
            this.objectId = stlPath.getFileName().toString().replace(".stl", "").replace(".3mf", "");
        }
    }

    /**
     * Result of processing a single object
     */
    public static class ProcessedObject {
        public ObjectConfig config;
        public String meshXml;
        public String metadataXml;
        public int vertexCount;
        
        public ProcessedObject(ObjectConfig config) {
            this.config = config;
        }
    }

    /**
     * Result containing slicing information
     */
    public static class SliceResult {
        public Path outputGcode;
        public Path output3mf;
        public String printTimeFormatted;
        public int printTimeSeconds;
        public double[] bounds; // [minX, maxX, minY, maxY]
        public List<String> failedObjectIds = new ArrayList<>(); // objects that could not be sliced

        public SliceResult(Path output3mf, Path outputGcode) {
            this.output3mf = output3mf;
            this.outputGcode = outputGcode;
        }
    }

    // ============================================================
    // Constructor
    // ============================================================

    public SlicerService(String prusaSlicerPath, Path iniConfigPath, Path workingDirectory) {
        this.prusaSlicerPath = prusaSlicerPath;
        this.iniConfigPath = iniConfigPath;
        this.workingDirectory = workingDirectory;
        
        if (!Files.exists(Paths.get(prusaSlicerPath))) {
            throw new IllegalArgumentException("PrusaSlicer not found at: " + prusaSlicerPath);
        }
        if (!Files.exists(iniConfigPath)) {
            throw new IllegalArgumentException("INI config not found at: " + iniConfigPath);
        }
    }

    // ============================================================
    // STEP 1: Export STL to 3MF
    // ============================================================

    /**
     * Converts an STL file to 3MF format using PrusaSlicer CLI
     */
    public Path exportStlTo3mf(Path stlPath, Path output3mfPath) throws Exception {
        requireExists(stlPath);
        Files.createDirectories(output3mfPath.getParent());
        
        run(List.of(
            prusaSlicerPath,
            "--load", iniConfigPath.toString(),
            "--export-3mf", stlPath.toString(),
            "--output", output3mfPath.toString()
        ), "Export 3MF failed");

        // Normalize vertices to origin so all downstream operations (analysis, nesting,
        // slicing) work with coordinates near (0,0,0) instead of extreme values.
        normalizeVerticesInPlace(output3mfPath);

        return output3mfPath;
    }
    // Add these methods to SlicerService.java

    /**
     * Calculates the support offset by analyzing G-code with and without supports.
     * Returns [offsetX, offsetY] - the difference between support bounds and object bounds.
     * 
     * This method:
     * 1. Slices the object WITH supports to get total bounds (support + object)
     * 2. Slices the object WITHOUT supports to get pure object bounds
     * 3. Calculates the difference to determine support offset
     * 
     * @param stlPath Path to the STL file
     * @param infill Infill percentage
     * @param brim Whether to use brim
     * @param supportType Support type ("snug", "organic", "grid", or "off")
     * @return double[2] containing [offsetX, offsetY] in millimeters
     */
    public double[] calculateSupportOffset(Path stlPath, int infill, String brim, 
                                          String supportType) throws Exception {
        requireExists(stlPath);
        
        Path tempDir = workingDirectory.resolve("offset_analysis");
        Files.createDirectories(tempDir);
        
        String baseName = stlPath.getFileName().toString().replace(".stl", "");
        
        // Step 1: Slice WITH supports
        Path temp3mfWithSupport = tempDir.resolve(baseName + "_with_support.3mf");
        Path tempGcodeWithSupport = tempDir.resolve(baseName + "_with_support.gcode");
        
        exportStlTo3mf(stlPath, temp3mfWithSupport);
        edit3mfSettings(temp3mfWithSupport, infill, brim, supportType, 0, 0, 0);
        slice3mfToGcode(temp3mfWithSupport, tempGcodeWithSupport);
        
        double[] boundsWithSupport = analyzeGcodeBounds(tempGcodeWithSupport);
        // boundsWithSupport = [minX, maxX, minY, maxY] including support structure
        
        // Step 2: Slice WITHOUT supports
        Path temp3mfNoSupport = tempDir.resolve(baseName + "_no_support.3mf");
        Path tempGcodeNoSupport = tempDir.resolve(baseName + "_no_support.gcode");
        
        exportStlTo3mf(stlPath, temp3mfNoSupport);
        edit3mfSettings(temp3mfNoSupport, infill, brim, "off", 0, 0, 0);
        slice3mfToGcode(temp3mfNoSupport, tempGcodeNoSupport);
        
        double[] boundsNoSupport = analyzeGcodeBounds(tempGcodeNoSupport);
        // boundsNoSupport = [minX, maxX, minY, maxY] pure object bounds
        
        // Step 3: Calculate offset
        // Support extends equally in all directions, so we measure the difference at minX and minY
        double offsetX = boundsNoSupport[0] - boundsWithSupport[0]; // object.minX - support.minX
        double offsetY = boundsNoSupport[2] - boundsWithSupport[2]; // object.minY - support.minY
        
        System.out.println("  📐 Support offset analysis:");
        System.out.println("     With support: minX=" + boundsWithSupport[0] + ", minY=" + boundsWithSupport[2]);
        System.out.println("     Without support: minX=" + boundsNoSupport[0] + ", minY=" + boundsNoSupport[2]);
        System.out.println("     Calculated offset: X=" + offsetX + "mm, Y=" + offsetY + "mm");
        
        // Cleanup temporary files
        try {
            Files.deleteIfExists(temp3mfWithSupport);
            Files.deleteIfExists(tempGcodeWithSupport);
            Files.deleteIfExists(temp3mfNoSupport);
            Files.deleteIfExists(tempGcodeNoSupport);
        } catch (Exception e) {
            // Ignore cleanup errors
        }
        
        return new double[]{offsetX, offsetY};
    }

    /**
     * Extract object bounds from 3MF file by analyzing the mesh geometry.
     * This is faster than slicing but requires parsing the 3MF mesh data.
     * Returns [minX, maxX, minY, maxY, minZ, maxZ]
     */
    public double[] extractObjectBoundsFrom3mf(Path threeMfPath) throws Exception {
        requireExists(threeMfPath);
        
        Map<String, String> env = new HashMap<>();
        env.put("create", "false");
        
        try (FileSystem zipfs = FileSystems.newFileSystem(threeMfPath, env)) {
            Path modelFile = zipfs.getPath("/3D/3dmodel.model");
            String modelContent = Files.readString(modelFile, StandardCharsets.UTF_8);
            
            // Extract all vertex coordinates
            Pattern vertexPattern = Pattern.compile("<vertex\\s+x=\"([^\"]+)\"\\s+y=\"([^\"]+)\"\\s+z=\"([^\"]+)\"");
            Matcher matcher = vertexPattern.matcher(modelContent);
            
            double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE;
            double minY = Double.MAX_VALUE, maxY = Double.MIN_VALUE;
            double minZ = Double.MAX_VALUE, maxZ = Double.MIN_VALUE;
            
            boolean foundVertex = false;
            while (matcher.find()) {
                foundVertex = true;
                double x = Double.parseDouble(matcher.group(1));
                double y = Double.parseDouble(matcher.group(2));
                double z = Double.parseDouble(matcher.group(3));
                
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
            }
            
            if (!foundVertex) {
                throw new RuntimeException("No vertices found in 3MF file");
            }
            
            return new double[]{minX, maxX, minY, maxY, minZ, maxZ};
        }
    }

    // ============================================================
    // STEP 2: Edit 3MF settings and position
    // ============================================================

    /**
     * Modifies 3MF file to set print parameters and object position
     * @param supportType "snug", "organic", or "off"
     */
    public void edit3mfSettings(Path threeMfPath, int infill, String brim, 
                                 String supportType,
                                 double x, double y, double z) throws Exception {
        requireExists(threeMfPath);
        
        Map<String, String> env = new HashMap<>();
        env.put("create", "false");

        try (FileSystem zipfs = FileSystems.newFileSystem(threeMfPath, env)) {
            // Edit metadata config
            Path modelConfig = zipfs.getPath("/Metadata/Slic3r_PE_model.config");
            if (!Files.exists(modelConfig)) {
                throw new IllegalStateException("Slic3r_PE_model.config not found");
            }
            editModelConfigTextBased(modelConfig, infill, brim, supportType);

            // Edit coordinates
            Path modelFile = zipfs.getPath("/3D/3dmodel.model");
            if (Files.exists(modelFile)) {
                edit3DModelCoordinates(modelFile, x, y, z);
            }
        }
    }

    // ============================================================
    // STEP 3: Slice 3MF to G-code
    // ============================================================

    /**
     * Slices a 3MF file to generate G-code
     */
    public Path slice3mfToGcode(Path threeMfPath, Path outputGcodePath) throws Exception {
        requireExists(threeMfPath);
        Files.createDirectories(outputGcodePath.getParent());
        
        // Delete existing file if present
        Files.deleteIfExists(outputGcodePath);

        // PrusaSlicer console on Windows sometimes crashes mid-write (ACCESS_VIOLATION exit -1073741819).
        // Retry up to 3 times before giving up.
        int maxAttempts = 3;
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Files.deleteIfExists(outputGcodePath);
            try {
                run(List.of(
                    prusaSlicerPath,
                    "--load", iniConfigPath.toString(),
                    "--export-gcode", threeMfPath.toString(),
                    "--output", outputGcodePath.toString()
                ), "Export G-code failed");
                lastException = null;
                break; // success
            } catch (RuntimeException e) {
                boolean fileExists = Files.exists(outputGcodePath);
                long fileSize = fileExists ? Files.size(outputGcodePath) : 0;
                if (fileExists && fileSize > 0) {
                    // File was written despite crash — treat as success
                    System.err.println("  ⚠️  PrusaSlicer crashed but G-code was written — continuing.");
                    lastException = null;
                    break;
                }
                lastException = e;
                if (attempt < maxAttempts) {
                    System.err.println("  ⚠️  PrusaSlicer crashed (attempt " + attempt + "/" + maxAttempts + "), retrying...");
                    Thread.sleep(500);
                } else {
                    System.err.println("  ❌  PrusaSlicer crashed on all " + maxAttempts + " attempts.");
                }
            }
        }
        if (lastException != null) {
            throw lastException;
        }

        // Verify the file was created
        if (!Files.exists(outputGcodePath)) {
            throw new RuntimeException(
                "❌ G-code file was not created: " + outputGcodePath + 
                "\nPrusaSlicer completed but no output file found. " +
                "Possible causes:\n" +
                "  - Invalid output path\n" +
                "  - No write permissions\n" +
                "  - Disk full\n" +
                "  - Config file issue"
            );
        }
        
        // Verify file has content
        long fileSize = Files.size(outputGcodePath);
        if (fileSize == 0) {
            throw new RuntimeException("❌ G-code file is empty: " + outputGcodePath);
        }
        
        System.out.println("  ✅ Created: " + outputGcodePath.getFileName() + 
                         " (" + (fileSize / 1024) + " KB)");
        
        return outputGcodePath;
    }

public double[] analyzeGcodeBounds(Path gcodePath) throws IOException {
    requireExists(gcodePath);
    
    final double[] bounds = {
        Double.POSITIVE_INFINITY,  // minX [0] - start high, find minimum
        Double.NEGATIVE_INFINITY,  // maxX [1] - start low, find maximum  
        Double.POSITIVE_INFINITY,  // minY [2] - start high, find minimum
        Double.NEGATIVE_INFINITY,  // maxY [3] - start low, find maximum ← THIS WAS THE BUG!
        Double.POSITIVE_INFINITY,  // minZ [4] - start high, find minimum
        Double.NEGATIVE_INFINITY   // maxZ [5] - start low, find maximum
    };

    try (var lines = Files.lines(gcodePath, StandardCharsets.ISO_8859_1)) {
        final double[] currentPos = {0.0, 0.0, 0.0};
        final boolean[] isPrinting = {false};

        lines.forEach(line -> {
            String trimmed = line.trim();

            if (trimmed.contains(";TYPE:")) {
                isPrinting[0] = true;
            }

            if (trimmed.startsWith("G28") || trimmed.contains("; Retract")) {
                isPrinting[0] = false;
            }

            if ((trimmed.startsWith("G1") || trimmed.startsWith("G0")) && isPrinting[0]) {
                int xIdx = trimmed.indexOf("X");
                int yIdx = trimmed.indexOf("Y");
                int zIdx = trimmed.indexOf("Z");

                if (xIdx != -1) {
                    try {
                        String xStr = extractNumber(trimmed, xIdx + 1);
                        if (!xStr.isEmpty()) {
                            currentPos[0] = Double.parseDouble(xStr);
                        }
                    } catch (NumberFormatException e) {}
                }

                if (yIdx != -1) {
                    try {
                        String yStr = extractNumber(trimmed, yIdx + 1);
                        if (!yStr.isEmpty()) {
                            currentPos[1] = Double.parseDouble(yStr);
                        }
                    } catch (NumberFormatException e) {}
                }

                if (zIdx != -1) {
                    try {
                        String zStr = extractNumber(trimmed, zIdx + 1);
                        if (!zStr.isEmpty()) {
                            currentPos[2] = Double.parseDouble(zStr);
                        }
                    } catch (NumberFormatException e) {}
                }

                boolean isExtruding = trimmed.contains("E") && !trimmed.contains("E-");
                if (isExtruding) {
                    bounds[0] = Math.min(bounds[0], currentPos[0]); // minX
                    bounds[1] = Math.max(bounds[1], currentPos[0]); // maxX
                    bounds[2] = Math.min(bounds[2], currentPos[1]); // minY
                    bounds[3] = Math.max(bounds[3], currentPos[1]); // maxY ← NOW WORKS!
                    bounds[4] = Math.min(bounds[4], currentPos[2]); // minZ
                    bounds[5] = Math.max(bounds[5], currentPos[2]); // maxZ
                }
            }
        });
    }

    // Check if any bounds were found
    if (bounds[0] == Double.POSITIVE_INFINITY) {
        System.err.println("WARNING: No extrusion moves found in G-code!");
        return new double[]{0, 0, 0, 0, 0};
    }

    System.out.println("DEBUG final bounds: minX=" + bounds[0] + ", maxX=" + bounds[1] + 
                       ", minY=" + bounds[2] + ", maxY=" + bounds[3] + 
                       ", minZ=" + bounds[4] + ", maxZ=" + bounds[5]);
    System.out.println("  Calculated dimensions: width=" + (bounds[1]-bounds[0]) + 
                       "mm, depth=" + (bounds[3]-bounds[2]) + 
                       "mm, height=" + bounds[5] + "mm");

    return new double[]{bounds[0], bounds[1], bounds[2], bounds[3], bounds[5]};
}

private String extractNumber(String line, int startIdx) {
    StringBuilder num = new StringBuilder();
    boolean hasDigit = false;
    
    for (int i = startIdx; i < line.length(); i++) {
        char c = line.charAt(i);
        
        if (Character.isDigit(c)) {
            num.append(c);
            hasDigit = true;
        } else if (c == '.' || (c == '-' && !hasDigit)) {
            num.append(c);
        } else if (c == ' ' || c == ';' || Character.isLetter(c)) {
            break;
        }
    }
    
    return hasDigit ? num.toString() : "";
}


    /**
     * Extracts print time from G-code
     * Returns formatted string like "1h 23m 45s"
     */
    public String extractPrintTime(Path gcodePath) throws IOException {
        requireExists(gcodePath);
        
        try (var lines = Files.lines(gcodePath, StandardCharsets.ISO_8859_1)) {
            return lines
                .filter(line -> line.contains("estimated printing time"))
                .filter(line -> line.contains("normal mode"))
                .map(line -> {
                    int idx = line.indexOf("= ");
                    if (idx != -1) {
                        return line.substring(idx + 2).trim();
                    }
                    return "Unknown";
                })
                .findFirst()
                .orElse("Not found in G-code");
        }
    }
    
    /**
     * Extract print time in seconds from G-code
     * Handles formats: "1d 2h 30m 15s", "12h 30m", "45m 30s", etc.
     */
    public int extractPrintTimeSeconds(Path gcodePath) throws IOException {
        String timeStr = extractPrintTime(gcodePath);
        
        if (timeStr.equals("Not found in G-code") || timeStr.equals("Unknown")) {
            return 0;
        }
        
        return parsePrintTimeToSeconds(timeStr);
    }
    
    /**
     * Parse print time string to seconds
     * Handles: "1d 2h 30m 15s", "12h 30m", "45m", etc.
     */
    public static int parsePrintTimeToSeconds(String timeStr) {
        int totalSeconds = 0;
        
        // Parse days
        if (timeStr.contains("d")) {
            int dIdx = timeStr.indexOf("d");
            String daysPart = timeStr.substring(0, dIdx).trim();
            // Handle "1d" or space before like "1 d"
            daysPart = daysPart.replaceAll("[^0-9]", "");
            if (!daysPart.isEmpty()) {
                totalSeconds += Integer.parseInt(daysPart) * 24 * 3600;
            }
            timeStr = timeStr.substring(dIdx + 1).trim();
        }
        
        // Parse hours
        if (timeStr.contains("h")) {
            int hIdx = timeStr.indexOf("h");
            String hoursPart = timeStr.substring(0, hIdx).trim();
            hoursPart = hoursPart.replaceAll("[^0-9]", "");
            if (!hoursPart.isEmpty()) {
                totalSeconds += Integer.parseInt(hoursPart) * 3600;
            }
            timeStr = timeStr.substring(hIdx + 1).trim();
        }
        
        // Parse minutes
        if (timeStr.contains("m")) {
            int mIdx = timeStr.indexOf("m");
            String minsPart = timeStr.substring(0, mIdx).trim();
            minsPart = minsPart.replaceAll("[^0-9]", "");
            if (!minsPart.isEmpty()) {
                totalSeconds += Integer.parseInt(minsPart) * 60;
            }
            timeStr = timeStr.substring(mIdx + 1).trim();
        }
        
        // Parse seconds
        if (timeStr.contains("s")) {
            int sIdx = timeStr.indexOf("s");
            String secsPart = timeStr.substring(0, sIdx).trim();
            secsPart = secsPart.replaceAll("[^0-9]", "");
            if (!secsPart.isEmpty()) {
                totalSeconds += Integer.parseInt(secsPart);
            }
        }
        
        return totalSeconds;
    }


    

    // ============================================================
    // STEP 5: Calculate transformation
    // ============================================================

    /**
     * Calculates the transformation needed to position object at target coordinates.
     * Uses the stored G-code minX/minY (at origin) from the DB — no hardcoded constants.
     *
     * @param storedMinX G-code minX when object was sliced at origin (stl.getMinX())
     * @param storedMinY G-code minY when object was sliced at origin (stl.getMinY())
     * @param targetX    desired nesting-grid X position
     * @param targetY    desired nesting-grid Y position
     * @param targetZ    desired Z position
     * @return [shiftX, shiftY, targetZ] 3MF transform values
     */
    public double[] calculateTransform(double storedMinX, double storedMinY,
                                       double targetX, double targetY, double targetZ) {
        double shiftX = targetX - storedMinX;
        double shiftY = targetY - storedMinY;
        return new double[]{shiftX, shiftY, targetZ};
    }

    // ============================================================
    // HIGH-LEVEL: Complete workflow for single object
    // ============================================================

    /**
     * Complete workflow: STL → positioned G-code in one call
     */
    public SliceResult sliceSingleObject(ObjectConfig config) throws Exception {
        Path tempDir = workingDirectory.resolve("temp");
        Files.createDirectories(tempDir);
        
        Path temp3mf = tempDir.resolve(config.objectId + "_temp.3mf");
        Path tempGcode = tempDir.resolve(config.objectId + "_temp.gcode");
        
        // Step 1: Export STL to 3MF
        exportStlTo3mf(config.stlPath, temp3mf);
        
        // Step 2: Edit settings at origin
        edit3mfSettings(temp3mf, config.infill, config.brim, config.supportType, 0, 0, 0);
        
        // Step 3: Initial slice to find bounds
        slice3mfToGcode(temp3mf, tempGcode);
        
        // Step 4: Analyze bounds
        double[] bounds = analyzeGcodeBounds(tempGcode);
        
        // Step 5: Calculate transformation using bounds from at-origin slice
        double[] transform = calculateTransform(bounds[0], bounds[2], config.targetX, config.targetY, config.targetZ);
        config.actualX = transform[0];
        config.actualY = transform[1];
        config.actualZ = transform[2];
        
        // Step 6: Apply transformation and re-slice
        edit3mfSettings(temp3mf, config.infill, config.brim, config.supportType,
                       transform[0], transform[1], transform[2]);
        slice3mfToGcode(temp3mf, tempGcode);
        
        // Step 7: Extract final info
        SliceResult result = new SliceResult(temp3mf, tempGcode);
        result.bounds = bounds;
        result.printTimeFormatted = extractPrintTime(tempGcode);
        result.printTimeSeconds = extractPrintTimeSeconds(tempGcode);
        
        return result;
    }

    // ============================================================
    // HIGH-LEVEL: Multi-object workflow
    // ============================================================

    /**
     * Processes multiple objects and combines them into a single 3MF/G-code
     */
    public SliceResult sliceMultipleObjects(List<ObjectConfig> objects, 
                                           Path output3mf, Path outputGcode) throws Exception {
        Path tempDir = workingDirectory.resolve("temp");
        Files.createDirectories(tempDir);
        Files.createDirectories(output3mf.getParent());
        
        List<ProcessedObject> processedObjects = new ArrayList<>();
        
        // Process each object individually
        List<String> failedObjectIds = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            ObjectConfig obj = objects.get(i);
            obj.objectIndex = i + 1;

            Path temp3mf = tempDir.resolve(obj.objectId + "_temp.3mf");

            try {
                // If the stored path is already a 3MF (set by saveJobFiles), copy it directly.
                // Calling exportStlTo3mf on a 3MF file causes PrusaSlicer to re-export it,
                // which may shift the item transform and invalidate storedMinX/Y.
                if (obj.stlPath.toString().toLowerCase().endsWith(".3mf")) {
                    Files.copy(obj.stlPath, temp3mf, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    exportStlTo3mf(obj.stlPath, temp3mf);
                }
                edit3mfSettings(temp3mf, obj.infill, obj.brim, obj.supportType, 0, 0, 0);

                // Always read bounds from the (normalized) temp 3MF — storedMinX/Y is
                // stale now that exportStlTo3mf normalizes vertices to origin.
                double[] meshBounds = extractObjectBoundsFrom3mf(temp3mf);
                double effectiveMinX = meshBounds[0];
                double effectiveMinY = meshBounds[2];

                double[] transform = calculateTransform(effectiveMinX, effectiveMinY,
                        obj.targetX, obj.targetY, obj.targetZ);

                System.out.printf("  📐 %-30s meshMin=(%.2f,%.2f) target=(%.1f,%.1f) → actual=(%.1f,%.1f)%n",
                        obj.objectId, effectiveMinX, effectiveMinY,
                        obj.targetX, obj.targetY, transform[0], transform[1]);

                obj.actualX = transform[0];
                obj.actualY = transform[1];
                obj.actualZ = transform[2];

                ProcessedObject processed = extractObjectData(temp3mf, obj);
                processedObjects.add(processed);
            } catch (Exception e) {
                System.err.println("❌ Failed to process object '" + obj.objectId + "': " + e.getMessage());
                failedObjectIds.add(obj.objectId);
            }
        }

        if (processedObjects.isEmpty()) {
            throw new RuntimeException("All objects failed to process: " + failedObjectIds);
        }
        
        // Combine into single 3MF
        buildCombined3mf(processedObjects, output3mf, tempDir);
        
        // Final slice
        slice3mfToGcode(output3mf, outputGcode);
        
        SliceResult result = new SliceResult(output3mf, outputGcode);
        result.printTimeFormatted = extractPrintTime(outputGcode);
        result.printTimeSeconds = extractPrintTimeSeconds(outputGcode);
        result.failedObjectIds = failedObjectIds;

        return result;
    }

    // ============================================================
    // Internal helper methods
    // ============================================================

    private ProcessedObject extractObjectData(Path threeMf, ObjectConfig config) throws Exception {
        ProcessedObject result = new ProcessedObject(config);
        
        Map<String, String> env = new HashMap<>();
        env.put("create", "false");
        
        try (FileSystem zipfs = FileSystems.newFileSystem(threeMf, env)) {
            Path modelFile = zipfs.getPath("/3D/3dmodel.model");
            String modelContent = Files.readString(modelFile, StandardCharsets.UTF_8);
            
            Pattern meshPattern = Pattern.compile("<mesh>(.*?)</mesh>", Pattern.DOTALL);
            Matcher meshMatcher = meshPattern.matcher(modelContent);
            if (meshMatcher.find()) {
                result.meshXml = meshMatcher.group(0);
            } else {
                throw new RuntimeException("No <mesh> found in 3MF for " + config.objectId);
            }
            
            Pattern vertexPattern = Pattern.compile("<vertex");
            Matcher vertexMatcher = vertexPattern.matcher(result.meshXml);
            int vertexCount = 0;
            while (vertexMatcher.find()) vertexCount++;
            result.vertexCount = vertexCount;
            
            Path configFile = zipfs.getPath("/Metadata/Slic3r_PE_model.config");
            String configContent = Files.readString(configFile, StandardCharsets.UTF_8);
            
            Pattern metadataPattern = Pattern.compile("<object[^>]*>(.*?)</object>", Pattern.DOTALL);
            Matcher metadataMatcher = metadataPattern.matcher(configContent);
            if (metadataMatcher.find()) {
                result.metadataXml = metadataMatcher.group(1).trim();
            }
        }
        
        return result;
    }

    private void buildCombined3mf(List<ProcessedObject> objects, Path output, Path tempDir) throws Exception {
        Path template = tempDir.resolve(objects.get(0).config.objectId + "_temp.3mf");
        Files.copy(template, output, StandardCopyOption.REPLACE_EXISTING);
        
        Map<String, String> env = new HashMap<>();
        env.put("create", "false");
        
        try (FileSystem zipfs = FileSystems.newFileSystem(output, env)) {
            StringBuilder modelXml = new StringBuilder();
            modelXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            modelXml.append("<model unit=\"millimeter\" xml:lang=\"en-US\" xmlns=\"http://schemas.microsoft.com/3dmanufacturing/core/2015/02\" xmlns:slic3rpe=\"http://schemas.slic3r.org/3mf/2017/06\">\n");
            modelXml.append("  <resources>\n");
            
            for (int i = 0; i < objects.size(); i++) {
                ProcessedObject obj = objects.get(i);
                int objectId = i + 1;
                
                modelXml.append("    <object id=\"").append(objectId).append("\" type=\"model\">\n");
                modelXml.append("      ").append(obj.meshXml).append("\n");
                modelXml.append("    </object>\n");
            }
            
            modelXml.append("  </resources>\n");
            modelXml.append("  <build>\n");
            
            for (int i = 0; i < objects.size(); i++) {
                ProcessedObject obj = objects.get(i);
                int objectId = i + 1;
                String transform = String.format("1 0 0 0 1 0 0 0 1 %.6f %.6f %.1f",
                    obj.config.actualX, obj.config.actualY, obj.config.actualZ);
                modelXml.append("    <item objectid=\"").append(objectId)
                    .append("\" transform=\"").append(transform).append("\" />\n");
            }
            
            modelXml.append("  </build>\n");
            modelXml.append("</model>\n");
            
            Path modelFile = zipfs.getPath("/3D/3dmodel.model");
            Files.writeString(modelFile, modelXml.toString(), StandardCharsets.UTF_8);
            
            StringBuilder configXml = new StringBuilder();
            configXml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            configXml.append("<config>\n");
            
            for (int i = 0; i < objects.size(); i++) {
                ProcessedObject obj = objects.get(i);
                int objectId = i + 1;
                
                configXml.append("  <object id=\"").append(objectId).append("\">\n");
                String[] metadataLines = obj.metadataXml.split("\n");
                for (String line : metadataLines) {
                    if (!line.trim().isEmpty()) {
                        configXml.append("    ").append(line.trim()).append("\n");
                    }
                }
                configXml.append("  </object>\n");
            }
            
            configXml.append("</config>\n");
            
            Path configFile = zipfs.getPath("/Metadata/Slic3r_PE_model.config");
            Files.writeString(configFile, configXml.toString(), StandardCharsets.UTF_8);
        }
    }

    private void normalizeVerticesInPlace(Path threeMfPath) throws Exception {
        Map<String, String> env = new HashMap<>();
        env.put("create", "false");

        try (FileSystem zipfs = FileSystems.newFileSystem(threeMfPath, env)) {
            Path modelFile = zipfs.getPath("/3D/3dmodel.model");
            if (!Files.exists(modelFile)) return;

            String content = Files.readString(modelFile, StandardCharsets.UTF_8);

            // Find bounding box across all vertices
            Matcher vm = Pattern.compile("<vertex x=\"([^\"]+)\" y=\"([^\"]+)\" z=\"([^\"]+)\"").matcher(content);
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
            while (vm.find()) {
                double vx = Double.parseDouble(vm.group(1));
                double vy = Double.parseDouble(vm.group(2));
                double vz = Double.parseDouble(vm.group(3));
                if (vx < minX) minX = vx;
                if (vy < minY) minY = vy;
                if (vz < minZ) minZ = vz;
            }

            if (minX == Double.MAX_VALUE) return;

            final double dx = minX, dy = minY, dz = minZ;

            // Shift all vertices so bounding box starts at (0,0,0)
            StringBuffer sb = new StringBuffer();
            Matcher replacer = Pattern.compile("<vertex x=\"([^\"]+)\" y=\"([^\"]+)\" z=\"([^\"]+)\"").matcher(content);
            while (replacer.find()) {
                double vx = Double.parseDouble(replacer.group(1)) - dx;
                double vy = Double.parseDouble(replacer.group(2)) - dy;
                double vz = Double.parseDouble(replacer.group(3)) - dz;
                replacer.appendReplacement(sb, String.format(Locale.US, "<vertex x=\"%.6f\" y=\"%.6f\" z=\"%.6f\"", vx, vy, vz));
            }
            replacer.appendTail(sb);
            String normalized = sb.toString();

            // Reset item transforms to identity — position is applied later via buildCombined3mf
            normalized = normalized.replaceAll("transform=\"[^\"]*\"", "transform=\"1 0 0 0 1 0 0 0 1 0 0 0\"");

            Files.writeString(modelFile, normalized, StandardCharsets.UTF_8);
        }
    }

    private void edit3DModelCoordinates(Path modelFile, double x, double y, double z) throws IOException {
        List<String> lines = Files.readAllLines(modelFile, StandardCharsets.UTF_8);
        String content = String.join("\n", lines);

        // Compute bounding box from mesh vertices to center piece at (x, y)
        Matcher vm = Pattern.compile("<vertex x=\"([^\"]+)\" y=\"([^\"]+)\" z=\"([^\"]+)\"").matcher(content);
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        while (vm.find()) {
            double vx = Double.parseDouble(vm.group(1));
            double vy = Double.parseDouble(vm.group(2));
            double vz = Double.parseDouble(vm.group(3));
            if (vx < minX) minX = vx; if (vx > maxX) maxX = vx;
            if (vy < minY) minY = vy; if (vy > maxY) maxY = vy;
            if (vz < minZ) minZ = vz;
        }

        double tx = (minX != Double.MAX_VALUE) ? x - (minX + maxX) / 2.0 : x;
        double ty = (minY != Double.MAX_VALUE) ? y - (minY + maxY) / 2.0 : y;
        double tz = (minZ != Double.MAX_VALUE) ? z - minZ : z;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("<item") && line.contains("transform=")) {
                String newTransform = String.format(Locale.US, "1 0 0 0 1 0 0 0 1 %.6f %.6f %.6f", tx, ty, tz);
                line = line.replaceAll("transform=\"[^\"]*\"", "transform=\"" + newTransform + "\"");
                lines.set(i, line);
            }
        }

        writeLinesToFile(modelFile, lines);
    }

    private void editModelConfigTextBased(Path modelConfig, int infill, String brim, 
                                         String supportType) throws IOException {
        List<String> lines = Files.readAllLines(modelConfig, StandardCharsets.UTF_8);

        String infillType = infill >= 60 ? "rectilinear" : "gyroid";
        int brimValue = brim.equalsIgnoreCase("no") ? 0 : 4;

        // Determine support settings based on supportType
        int supportValue = 0;
        int supportBuildplate = 0;
        String finalSupportType = "grid"; // default
        
        if (supportType != null && !supportType.equalsIgnoreCase("off")) {
            supportValue = 1;
            supportBuildplate = 0; // always use buildplate only
            finalSupportType = supportType.toLowerCase(); // "snug" or "organic"
        }

        List<String> insert = List.of(
            "  <metadata type=\"object\" key=\"fill_density\" value=\"" + infill + "%\">" + infill + "%</metadata>",
            "  <metadata type=\"object\" key=\"fill_pattern\" value=\"" + infillType + "\">" + infillType + "</metadata>",
            "  <metadata type=\"object\" key=\"brim_width\" value=\"" + brimValue + "\" />",
            "  <metadata type=\"object\" key=\"support_material\" value=\"" + supportValue + "\" />",
            "  <metadata type=\"object\" key=\"support_material_buildplate_only\" value=\"" + supportBuildplate + "\" />",
            "  <metadata type=\"object\" key=\"support_material_style\" value=\"" + finalSupportType + "\">" + finalSupportType + "</metadata>"
        );

        int insertIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("<volume")) {
                insertIndex = i;
                break;
            }
        }

        if (insertIndex == -1) {
            throw new IllegalStateException("No <volume> tag found");
        }

        lines.addAll(insertIndex, insert);
        writeLinesToFile(modelConfig, lines);
    }

    private void writeLinesToFile(Path file, List<String> lines) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(
                file, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
            writer.flush();
        }
    }

    private void run(List<String> cmd, String error) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        Process p = pb.start();
        String output;

        try (InputStream is = p.getInputStream()) {
            output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        int exitCode = p.waitFor();

        if (exitCode != 0) {
            // Print full PrusaSlicer output for debugging
            System.err.println("════════════════════════════════════════");
            System.err.println("PrusaSlicer command failed!");
            System.err.println("Exit code: " + exitCode);
            System.err.println("Command: " + String.join(" ", cmd));
            System.err.println("Output:");
            System.err.println(output);
            System.err.println("════════════════════════════════════════");

            throw new RuntimeException(error + "\n\n" + output);
        }
    }

    private void requireExists(Path p) {
        if (!Files.exists(p)) {
            throw new IllegalArgumentException("File not found: " + p);
        }
    }
}