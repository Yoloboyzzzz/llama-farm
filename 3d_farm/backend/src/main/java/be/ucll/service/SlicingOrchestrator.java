package be.ucll.service;

import be.ucll.model.Job;
import be.ucll.model.STLFile;
import be.ucll.model.Printer;
import be.ucll.model.PrinterProfile;
import be.ucll.repository.PrinterProfileRepository;
import be.ucll.slicer.cli.PrusaSlicerCLI;
import be.ucll.slicer.core.SlicingPlanner;
import be.ucll.slicer.core.model.BuildPlatePlan;
import be.ucll.slicer.core.model.ModelToSlice;
import be.ucll.slicer.core.model.PrintJobPlan;
import be.ucll.slicer.core.model.SliceParameters;
import be.ucll.util.GcodeMetadataReader;

import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.time.*;
import java.util.*;

@Service
public class SlicingOrchestrator {

    private final SlicingPlanner slicingPlanner;
    private final PrusaSlicerCLI prusaSlicerCLI;
    private final PrinterProfileRepository profileRepository;

    public SlicingOrchestrator(SlicingPlanner slicingPlanner,
                               PrusaSlicerCLI prusaSlicerCLI,
                               PrinterProfileRepository profileRepository) {
        this.slicingPlanner = slicingPlanner;
        this.prusaSlicerCLI = prusaSlicerCLI;
        this.profileRepository = profileRepository;
    }

    // Build SliceParameters based on DB profile
    public SliceParameters buildSliceParameters(String printerModel, String material) {
        PrinterProfile profile = profileRepository
                .findByPrinterModelAndMaterial(printerModel, material)
                .orElseThrow(() ->
                        new RuntimeException("No printer profile for " + printerModel + " + " + material));

        SliceParameters params = new SliceParameters();
        params.setPrusaConfigPath(profile.getConfigPath());
        params.setPlateWidthMm(profile.getPlateWidthMm() != null ? profile.getPlateWidthMm() : 250.0);
        params.setPlateDepthMm(profile.getPlateDepthMm() != null ? profile.getPlateDepthMm() : 210.0);
        params.setPrinterProfileName(printerModel);
        return params;
    }

    // Phase 1: characterize each STL using a reference printer model (e.g. MK4)
    public Map<STLFile, GcodeMetadataReader.GcodeInfo> characterizeEachStl(
            Job job,
            List<STLFile> stls,
            String referencePrinterModel,
            Path tempDir
    ) {
        Map<STLFile, GcodeMetadataReader.GcodeInfo> result = new HashMap<>();

        try {
            Files.createDirectories(tempDir);
        } catch (Exception ignored) {}

        String jobId = job.getId().toString();

        for (STLFile stl : stls) {
            try {
                SliceParameters params = buildSliceParameters(referencePrinterModel, stl.getMaterial());

                BuildPlatePlan plate = new BuildPlatePlan(1);
                ModelToSlice m = new ModelToSlice(
                        stl.getName(),
                        Paths.get(stl.getPath()),
                        1,
                        stl.getWidthMm(),
                        stl.getDepthMm(),
                        0,
                        0.2,
                        stl.getInfill(),
                        true,
                        stl.getBrim(),
                        stl.getMaterial(),
                        stl.getColor()
                );
                plate.addItem(m, 1);

                Path tmpGcode = prusaSlicerCLI.slicePlateToDirectory(jobId, plate, params, tempDir);

                GcodeMetadataReader.GcodeInfo info = GcodeMetadataReader.extractInfo(tmpGcode.toString());
                result.put(stl, info);

                try {
                    Files.deleteIfExists(tmpGcode);
                } catch (Exception ignored) {}

            } catch (Exception e) {
                System.err.println("⚠️ Could not characterise STL: " + stl.getName() + " – " + e.getMessage());
            }
        }

        return result;
    }

    // Phase 2: build plates with real timing and footprint
    public PrintJobPlan planPlatesFromTiming(
            Job job,
            List<STLFile> stls,
            Map<STLFile, GcodeMetadataReader.GcodeInfo> timing,
            SliceParameters planningParams
    ) {
        List<ModelToSlice> models = new ArrayList<>();

        for (STLFile stl : stls) {
            GcodeMetadataReader.GcodeInfo info = timing.get(stl);
            int estTime = (info != null ? info.durationSeconds : 3600);

            ModelToSlice m = new ModelToSlice(
                    stl.getName(),
                    Paths.get(stl.getPath()),
                    stl.getInstances(),
                    stl.getWidthMm(),
                    stl.getDepthMm(),
                    estTime,
                    0.2,
                    stl.getInfill(),
                    true,
                    stl.getBrim(),
                    stl.getMaterial(),
                    stl.getColor()
            );
            models.add(m);
        }

        return slicingPlanner.planJob(
                job.getId().toString(),
                models,
                planningParams
        );
    }

    // Phase 3: schedule plates among given printers until deadline
    public Map<String, List<BuildPlatePlan>> scheduleUntilDeadline(
            PrintJobPlan plan,
            List<Printer> printers,
            LocalDateTime now,
            LocalTime deadlineTime
    ) {
        Map<String, List<BuildPlatePlan>> schedule = new LinkedHashMap<>();
        Map<String, Long> capacity = new LinkedHashMap<>();

        LocalDateTime deadline = LocalDateTime.of(now.toLocalDate(), deadlineTime);
        long maxDaySeconds = Duration.between(now, deadline).toSeconds();

        for (Printer p : printers) {
            LocalDateTime available =
                    (p.getAvailableUntil() != null) ? p.getAvailableUntil() : deadline;

            long availableSec = Duration.between(now, available).toSeconds();
            if (availableSec > 0) {
                long cap = Math.min(availableSec, maxDaySeconds);
                capacity.put(p.getName(), cap);
                schedule.put(p.getName(), new ArrayList<>());
            }
        }

        List<BuildPlatePlan> plates = new ArrayList<>(plan.getPlates());
        plates.sort(Comparator.comparingInt(BuildPlatePlan::getEstimatedSeconds));

        for (BuildPlatePlan plate : plates) {
            int t = plate.getEstimatedSeconds();
            String chosen = null;

            for (var e : capacity.entrySet()) {
                if (e.getValue() >= t) {
                    chosen = e.getKey();
                    break;
                }
            }

            if (chosen == null) {
                System.out.println("⏱ Plate " + plate.getPlateIndex() + " does not fit before deadline");
                continue;
            }

            capacity.put(chosen, capacity.get(chosen) - t);
            schedule.get(chosen).add(plate);
        }

        schedule.entrySet().removeIf(e -> e.getValue().isEmpty());
        return schedule;
    }

    // Phase 4: slice plates according to schedule and profiles
    public List<Path> sliceScheduledPlates(
            Job job,
            Map<Printer, List<BuildPlatePlan>> schedule,
            String materialForPlates
    ) {
        List<Path> result = new ArrayList<>();

        for (var entry : schedule.entrySet()) {
            Printer printer = entry.getKey();
            List<BuildPlatePlan> plates = entry.getValue();

            SliceParameters params = buildSliceParameters(printer.getModel(), materialForPlates);

            for (BuildPlatePlan plate : plates) {
                Path gcode = prusaSlicerCLI.slicePlate(job.getId().toString(), plate, params);
                result.add(gcode);
            }
        }

        return result;
    }
}
