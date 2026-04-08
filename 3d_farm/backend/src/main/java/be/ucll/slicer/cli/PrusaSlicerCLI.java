package be.ucll.slicer.cli;

import be.ucll.slicer.core.model.BuildPlatePlan;
import be.ucll.slicer.core.model.ModelToSlice;
import be.ucll.slicer.core.model.SliceParameters;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class PrusaSlicerCLI {

    private final String executablePath;
    private final String defaultConfig;
    private final Path outputRoot;

    private final ThreeMFModifier threeMFModifier = new ThreeMFModifier();

    public PrusaSlicerCLI(
            @Value("${slicer.prusa.executable}") String executablePath,
            @Value("${slicer.prusa.defaultConfig}") String defaultConfig,
            @Value("${slicer.output.root}") String outputRoot
    ) {
        this.executablePath = executablePath;
        this.defaultConfig = defaultConfig;
        this.outputRoot = Paths.get(outputRoot);
        try {
            Files.createDirectories(this.outputRoot);
        } catch (Exception e) {
            throw new PrusaSlicerException("Cannot create output root " + outputRoot, e);
        }
    }

    public Path slicePlate(String jobId, BuildPlatePlan plate, SliceParameters params) {
        String fileName = "job-" + jobId + "-plate-" + plate.getPlateIndex() + "-" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                + ".gcode";

        return slicePlateInternal(jobId, plate, params, outputRoot.resolve(fileName));
    }
    public Path slicePlateToDirectory(String jobIdentifier,
                                  BuildPlatePlan platePlan,
                                  SliceParameters params,
                                  Path directory) {

    try {
        Files.createDirectories(directory);
    } catch (Exception e) {
        throw new PrusaSlicerException("Failed to create directory: " + directory, e);
    }

    String fileName = String.format(
            "job-%s-plate-%02d-%s.gcode",
            jobIdentifier,
            platePlan.getPlateIndex(),
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    );

    Path outputFile = directory.resolve(fileName);
    return slicePlateInternal(jobIdentifier, platePlan, params, outputFile);
}

    private Path slicePlateInternal(
            String jobId,
            BuildPlatePlan platePlan,
            SliceParameters params,
            Path outputFile
    ) {

        // 1. Generate one 3MF per distinct STL
        Map<Path, Path> stlTo3mf = new HashMap<>();

        Path threeMfDir;
        try {
            threeMfDir = Files.createTempDirectory(outputRoot, "3mf-" + jobId + "-p" + platePlan.getPlateIndex() + "-");
        } catch (IOException e) {
            throw new PrusaSlicerException("Cannot create temp 3MF dir", e);
        }

        for (BuildPlatePlan.PlateItem item : platePlan.getItems()) {
            ModelToSlice model = item.getModel();
            Path stl = model.getStlPath();

            if (!stlTo3mf.containsKey(stl)) {
                Path base3mf = export3mf(threeMfDir, stl, model, params);

                // Modify metadata
                Path final3mf = threeMFModifier.modify3MF(
                        base3mf,
                        model.getInfillPercent(),
                        model.isBrim() ? 5 : 0,
                        model.isSupports(),
                        false
                );

                stlTo3mf.put(stl, final3mf);
            }
        }

        // 2. Build FINAL slicing command using 3MFs
        List<String> cmd = new ArrayList<>();
        cmd.add(executablePath);

        cmd.add("--export-gcode");
        cmd.add("--load");
        cmd.add(params.getPrusaConfigPath() != null ? params.getPrusaConfigPath() : defaultConfig);
        cmd.add("--output");
        cmd.add(outputFile.toString());

        // Python behavior
        cmd.add("--duplicate-distance");
        cmd.add("10");
        cmd.add("--ensure-on-bed");
        cmd.add("--merge");
        cmd.add("--slice");
        cmd.add("--duplicate");
        cmd.add("1");

        // Add 3MFs multiple times for quantity
        for (BuildPlatePlan.PlateItem item : platePlan.getItems()) {
            Path threeMf = stlTo3mf.get(item.getModel().getStlPath());
            for (int i = 0; i < item.getQuantity(); i++) {
                cmd.add(threeMf.toString());
            }
        }

        runProcess(cmd, "G-code export");

        return outputFile;
    }

    private Path export3mf(Path dir, Path stl, ModelToSlice model, SliceParameters params) {
        String base = stl.getFileName().toString().replace(".stl", "");
        Path threeMf = dir.resolve(base + ".3mf");

        List<String> cmd = new ArrayList<>();
        cmd.add(executablePath);
        cmd.add("--export-3mf");
        cmd.add("--load");
        cmd.add(params.getPrusaConfigPath() != null ? params.getPrusaConfigPath() : defaultConfig);
        cmd.add("--layer-height");
        cmd.add(String.valueOf(model.getLayerHeightMm()));
        cmd.add("--fill-density");
        cmd.add(model.getInfillPercent() + "%");
        cmd.add("--brim-width");
        cmd.add(model.isBrim() ? "5" : "0");
        if (model.isSupports()) cmd.add("--support-material");
        else cmd.add("--no-support-material");
        cmd.add("--output");
        cmd.add(threeMf.toString());
        cmd.add(stl.toString());

        runProcess(cmd, "3MF export");

        return threeMf;
    }

    private void runProcess(List<String> cmd, String step) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            StringBuilder out = new StringBuilder();

            while ((line = br.readLine()) != null) {
                out.append(line).append("\n");
            }

            int exit = p.waitFor();
            if (exit != 0) throw new PrusaSlicerException(step + " failed:\n" + out);

        } catch (Exception e) {
            throw new PrusaSlicerException("Process failed (" + step + "): " + e.getMessage(), e);
        }
    }
}
