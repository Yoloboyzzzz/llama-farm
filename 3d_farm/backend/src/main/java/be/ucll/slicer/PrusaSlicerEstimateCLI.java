package be.ucll.slicer;

import be.ucll.util.GcodeMetadataReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class PrusaSlicerEstimateCLI {

    private final String executablePath;
    private final String defaultConfigPath;
    private final String outputRoot;

    public PrusaSlicerEstimateCLI(
            @Value("${slicer.prusa.executable}") String executablePath,
            @Value("${slicer.prusa.defaultConfig}") String defaultConfigPath,
            @Value("${slicer.output.root}") String outputRoot
    ) {
        this.executablePath = executablePath;
        this.defaultConfigPath = defaultConfigPath;
        this.outputRoot = outputRoot;

        // Ensure output root exists
        File root = new File(outputRoot);
        if (!root.exists()) {
            root.mkdirs();
        }
    }

    public GcodeMetadataReader.GcodeInfo sliceAndReadMetadata(File stlFile, int infill)
            throws IOException, InterruptedException {

        String jobId = UUID.randomUUID().toString();
        String outputDir = outputRoot + "/" + jobId;

        File dir = new File(outputDir);
        dir.mkdirs();

        String outputPath = outputDir + "/output.gcode";

        // FIXED: correct fill-density: "20%"
        String fillValue = infill + "%";

        ProcessBuilder builder = new ProcessBuilder(
                executablePath,
                "--load", defaultConfigPath,
                "--fill-density", fillValue,
                "--export-gcode",
                "--output", outputPath,
                stlFile.getAbsolutePath()
        );

        builder.redirectErrorStream(true);
        Process process = builder.start();

        // Capture slicer output
        StringBuilder slicerLog = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                slicerLog.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();

        System.out.println("=== PrusaSlicer OUTPUT ===");
        System.out.println(slicerLog);
        System.out.println("==========================");

        if (exitCode != 0) {
            throw new RuntimeException(
                    "PrusaSlicer exited with code " + exitCode + "\n" + slicerLog
            );
        }

        File gcode = new File(outputPath);
        if (!gcode.exists()) {
            throw new RuntimeException(
                    "G-code was not created: " + outputPath + "\n\nLOG:\n" + slicerLog
            );
        }

        return GcodeMetadataReader.extractInfo(outputPath);
    }
}
