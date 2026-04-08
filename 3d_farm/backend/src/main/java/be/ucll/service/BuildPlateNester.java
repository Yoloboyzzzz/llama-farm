package be.ucll.service;

import be.ucll.model.STLFile;
import be.ucll.model.Printer;

import java.util.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Duration;
import java.util.stream.Collectors;

/**
 * Build plate nesting & scheduling with Z-height validation.
 *
 * Scheduling rules:
 *  1. Sort all pieces longest → shortest print time.
 *  2. PHASE 1 – Long prints (>14 h): each gets its own dedicated plate.
 *  3. PHASE 2 – Deadline run:
 *       • If NOW < 16:00  → fill printers with pieces that finish by 16:00 today.
 *       • If NOW >= 16:00 → fill printers with pieces that finish by 08:00 tomorrow.
 *  4. PHASE 3 – Remainder plates (no overlap with deadline printers):
 *       • If NOW < 16:00  → plates capped at 14 h  (covers a full working day).
 *       • If NOW >= 16:00 → plates capped at  8 h  (covers an overnight / shift).
 *  5. After all plates are built, every plate is re-centered so objects sit as
 *     far from edges as possible (centroid of bounding-box moved to plate centre).
 *  6. Z-height is validated for every object - objects taller than printer height are rejected.
 */
public class BuildPlateNester {

    // Minimum gap from plate edges (mm)
    static final int EDGE_MARGIN = 2;
    // Minimum gap between pieces (mm)
    static final int PIECE_GAP   = 2;

    // ------------------------------------------------------------------ DTOs

    public static class PlacedObject {
        public STLFile stlFile;
        public int instanceNumber;
        public double x, y, width, height;

        public PlacedObject(STLFile stlFile, int instanceNumber, double x, double y) {
            this.stlFile = stlFile;
            this.instanceNumber = instanceNumber;
            this.x = x;
            this.y = y;
            this.width  = stlFile.getWidthMm();
            this.height = stlFile.getDepthMm();
        }
    }

    public static class BuildPlate {
        public List<PlacedObject> objects = new ArrayList<>();
        public double plateWidth, plateHeight;
        public int estimatedTimeSeconds;
        public Printer printer;

        /* package */ final int[][] surface;  // accessed by renestCentered helpers

        public BuildPlate(Printer printer) {
            this.printer     = printer;
            this.plateWidth  = printer.getBuildPlateWidth();
            this.plateHeight = printer.getBuildPlateDepth();
            this.surface     = new int[(int) plateWidth][(int) plateHeight];
        }

        /** Try to place at the first free (x,y) slot. Returns placed object or null. */
        public PlacedObject tryPlace(STLFile stl, int instanceNum) {
            int w = (int) Math.ceil(stl.getWidthMm());
            int h = (int) Math.ceil(stl.getDepthMm());
            
            // ✅ Check Z-height first - if object is too tall, reject immediately
            if (stl.getHeightMm() > printer.getBuildPlateHeight()) {
                log("    ❌ Z-height check failed: " + stl.getName() 
                    + " height=" + stl.getHeightMm() + "mm exceeds printer limit " 
                    + printer.getBuildPlateHeight() + "mm on " + printer.getName());
                return null;
            }

            for (int y = EDGE_MARGIN; y <= (int) plateHeight - h - EDGE_MARGIN; y++) {
                for (int x = EDGE_MARGIN; x <= (int) plateWidth - w - EDGE_MARGIN; x++) {
                    if (canPlace(x, y, w, h)) {
                        occupy(x, y, w, h);
                        PlacedObject po = new PlacedObject(stl, instanceNum, x, y);
                        objects.add(po);
                        recalcTime();
                        return po;
                    }
                }
            }
            return null;
        }

        /** Place at an explicit position (used for long prints at 0,0). */
        public PlacedObject placeAt(STLFile stl, int instanceNum, int px, int py) {
            int w = (int) Math.ceil(stl.getWidthMm());
            int h = (int) Math.ceil(stl.getDepthMm());
            
            // ✅ Check Z-height first
            if (stl.getHeightMm() > printer.getBuildPlateHeight()) {
                log("    ❌ Z-height check failed: " + stl.getName() 
                    + " height=" + stl.getHeightMm() + "mm exceeds printer limit " 
                    + printer.getBuildPlateHeight() + "mm on " + printer.getName());
                return null;
            }
            
            if (!canPlace(px, py, w, h)) return null;
            occupy(px, py, w, h);
            PlacedObject po = new PlacedObject(stl, instanceNum, px, py);
            objects.add(po);
            recalcTime();
            return po;
        }

        private boolean canPlace(int x, int y, int w, int h) {
            if (x + w > plateWidth || y + h > plateHeight) return false;
            for (int dy = 0; dy < h; dy++)
                for (int dx = 0; dx < w; dx++)
                    if (surface[x + dx][y + dy] != 0) return false;
            return true;
        }

        private void occupy(int x, int y, int w, int h) {
            int xEnd = Math.min(x + w + PIECE_GAP, (int) plateWidth);
            int yEnd = Math.min(y + h + PIECE_GAP, (int) plateHeight);
            for (int iy = y; iy < yEnd; iy++)
                for (int ix = x; ix < xEnd; ix++)
                    surface[ix][iy] = 1;
        }

        private void recalcTime() {
            estimatedTimeSeconds = objects.stream()
                    .mapToInt(o -> o.stlFile.getPrintTimeSeconds() != null
                            ? o.stlFile.getPrintTimeSeconds() : 0)
                    .max().orElse(0);
        }

        public boolean isEmpty() { return objects.isEmpty(); }
    }

    public static class ScheduledPlate {
        public BuildPlate plate;
        public Printer printer;
        public LocalDateTime startTime, endTime;
        public boolean isLongRunning;

        public ScheduledPlate(BuildPlate plate, LocalDateTime startTime) {
            this.plate        = plate;
            this.printer      = plate.printer;
            this.startTime    = startTime;
            this.endTime      = startTime.plusSeconds(plate.estimatedTimeSeconds);
            this.isLongRunning = plate.estimatedTimeSeconds > (14 * 3600);
        }
    }

    public static class NestingConfig {
        public List<Printer> availablePrinters;  // all matching printers (Phase 1 & 3)
        public List<Printer> idlePrinters;       // idle-only matching printers (Phase 2)
        public LocalDateTime currentTime;
        public LocalDateTime firstDeadline;   // typically today 16:00
        public LocalDateTime secondDeadline;  // typically tomorrow 08:00

        public NestingConfig(List<Printer> printers, List<Printer> idlePrinters,
                             LocalDateTime now,
                             LocalDateTime deadline1, LocalDateTime deadline2) {
            this.availablePrinters = printers;
            this.idlePrinters     = idlePrinters;
            this.currentTime      = now;
            this.firstDeadline    = deadline1;
            this.secondDeadline   = deadline2;
        }
    }

    public static class NestingResult {
        public List<ScheduledPlate> scheduledPlates    = new ArrayList<>();
        public List<ObjectInstance> unscheduledObjects = new ArrayList<>();
        public int objectsMeetingFirstDeadline  = 0;
        public int objectsMeetingSecondDeadline = 0;
        public int longRunningObjects           = 0;
        public int totalObjects                 = 0;

        public void addScheduled(ScheduledPlate sp, boolean meetsFirst) {
            scheduledPlates.add(sp);
            int count = sp.plate.objects.size();
            totalObjects += count;
            if (sp.isLongRunning)        longRunningObjects           += count;
            else if (meetsFirst)         objectsMeetingFirstDeadline  += count;
            else                         objectsMeetingSecondDeadline += count;
        }

        public void addUnscheduled(ObjectInstance obj) {
            unscheduledObjects.add(obj);
            totalObjects++;
        }
    }

    public static class ObjectInstance {
        public STLFile stlFile;
        public int instanceNumber;

        public ObjectInstance(STLFile stl, int instanceNum) {
            this.stlFile = stl;
            this.instanceNumber = instanceNum;
        }
    }

    // ============================================================
    // MAIN ALGORITHM
    // ============================================================

    public static NestingResult nestAndSchedule(
            List<STLFile> stlFiles,
            NestingConfig config) {

        // ── Expand instances & sort longest → shortest ──────────────────────
        List<ObjectInstance> allObjects = new ArrayList<>();
        for (STLFile stl : stlFiles)
            for (int i = 1; i <= stl.getInstances(); i++)
                allObjects.add(new ObjectInstance(stl, i));

        allObjects.sort((a, b) -> Integer.compare(
                safeTime(b.stlFile), safeTime(a.stlFile)));

        // ── Timing constants ────────────────────────────────────────────────
        final int LONG_THRESHOLD   = 14 * 3600;   // 14 h in seconds
        final LocalTime FOUR_PM    = LocalTime.of(16, 0);
        boolean isPast4pm = config.currentTime.toLocalTime().isAfter(FOUR_PM)
                         || config.currentTime.toLocalTime().equals(FOUR_PM);

        // Deadline window available from now
        int secondsToDeadline = (int) Duration.between(
                config.currentTime,
                isPast4pm ? config.secondDeadline : config.firstDeadline)
                .getSeconds();

        // Cap for remainder plates
        int remainderCap = isPast4pm ? (8 * 3600) : (14 * 3600);

        log("⏰ Current time : " + config.currentTime);
        log("🏁 Deadline     : " + (isPast4pm ? "08:00 tomorrow" : "16:00 today")
                + "  (" + fmt(secondsToDeadline) + " away)");
        log("📋 Remainder cap: " + fmt(remainderCap));

        // ── Filter printers by material + colour ────────────────────────────
        List<Printer> printers     = filterPrinters(config.availablePrinters, stlFiles);
        List<Printer> idlePrinters = filterPrinters(config.idlePrinters, stlFiles);
        log("🖨️  Matching printers: " + printers.size()
                + " (idle: " + idlePrinters.size() + ")\n");

        NestingResult result = new NestingResult();
        List<ObjectInstance> remaining = new ArrayList<>(allObjects);
        List<BuildPlate> allPlates = new ArrayList<>();

        // ════════════════════════════════════════════════════════════════════
        // PHASE 1 – Long prints (>14 h) → dedicated plate, smallest first
        // ════════════════════════════════════════════════════════════════════
        log("━━━━━━━━━ PHASE 1: LONG PRINTS (>14 h) ━━━━━━━━━");
        List<ObjectInstance> longPrints = new ArrayList<>();
        for (ObjectInstance obj : remaining)
            if (safeTime(obj.stlFile) > LONG_THRESHOLD)
                longPrints.add(obj);

        log("Found " + longPrints.size() + " long print(s)");

        // Sort printers smallest build plate area first so large prints use the tightest fit
        List<Printer> printersSmallFirst = new ArrayList<>(printers);
        printersSmallFirst.sort((a, b) -> Double.compare(
                a.getBuildPlateWidth() * a.getBuildPlateDepth(),
                b.getBuildPlateWidth() * b.getBuildPlateDepth()));

        for (ObjectInstance obj : longPrints) {
            boolean placed = false;
            for (Printer p : printersSmallFirst) {
                BuildPlate plate = new BuildPlate(p);
                if (plate.placeAt(obj.stlFile, obj.instanceNumber, 0, 0) != null) {
                    allPlates.add(plate);
                    remaining.remove(obj);
                    placed = true;
                    log("  ✅ " + obj.stlFile.getName() + " → dedicated plate on " + p.getName()
                            + " (" + (int) p.getBuildPlateWidth() + "×" + (int) p.getBuildPlateDepth() 
                            + "×" + (int) p.getBuildPlateHeight() + " mm)");
                    break;
                }
            }
            if (!placed)
                log("  ⚠️  " + obj.stlFile.getName() + " doesn't fit any printer!");
        }

        // ════════════════════════════════════════════════════════════════════
        // PHASE 2 – Deadline run (idle printers only, before 16:00 or 08:00)
        // ════════════════════════════════════════════════════════════════════
        String deadlineLabel = isPast4pm ? "08:00 tomorrow" : "16:00 today";
        log("\n━━━━━━━━━ PHASE 2: DEADLINE RUN (" + deadlineLabel + ", idle printers only) ━━━━━━━━━");

        List<BuildPlate> deadlinePlates = new ArrayList<>();
        for (Printer p : idlePrinters)
            deadlinePlates.add(new BuildPlate(p));

        List<ObjectInstance> packedDeadline =
                packObjects(remaining, deadlinePlates, secondsToDeadline, "deadline");
        allPlates.addAll(deadlinePlates);
        remaining.removeAll(packedDeadline);

        // ════════════════════════════════════════════════════════════════════
        // PHASE 3 – Remainder plates (capped at remainderCap per plate)
        // ════════════════════════════════════════════════════════════════════
        log("\n━━━━━━━━━ PHASE 3: REMAINDER PLATES (max " + fmt(remainderCap) + "/plate) ━━━━━━━━━");

        while (!remaining.isEmpty()) {
            List<BuildPlate> batch = new ArrayList<>();
            for (Printer p : printers)
                batch.add(new BuildPlate(p));

            int before = remaining.size();
            List<ObjectInstance> packed = packObjects(remaining, batch, remainderCap, "remainder");
            allPlates.addAll(batch);
            remaining.removeAll(packed);

            if (remaining.size() == before) {
                log("⚠️  Some objects don't physically fit on any build plate.");
                break;
            }
        }

        // ── Mark any still-unplaced objects ─────────────────────────────────
        for (ObjectInstance obj : remaining)
            result.addUnscheduled(obj);

        // ════════════════════════════════════════════════════════════════════
        // POST-PROCESS – Center every non-empty plate, then schedule
        // ════════════════════════════════════════════════════════════════════
        log("\n━━━━━━━━━ CENTERING & SCHEDULING ━━━━━━━━━");
        LocalDateTime cursor = config.currentTime;

        for (BuildPlate plate : allPlates) {
            if (plate.isEmpty()) continue;

            centerCluster(plate);

            ScheduledPlate sp = new ScheduledPlate(plate, cursor);
            boolean meetsFirst = sp.endTime.isBefore(config.firstDeadline)
                              || sp.endTime.equals(config.firstDeadline);
            result.addScheduled(sp, meetsFirst);
            cursor = sp.endTime;
        }

        return result;
    }

    // ============================================================
    // PACKING HELPER
    // ============================================================

    /**
     * Greedy bin-pack: tries to add each object to an existing plate first,
     * then to any plate (including empty ones), as long as adding it keeps
     * the plate within maxDurationSeconds.
     *
     * Returns the list of objects that were successfully placed.
     */
    private static List<ObjectInstance> packObjects(
            List<ObjectInstance> objects,
            List<BuildPlate> plates,
            int maxDurationSeconds,
            String phase) {

        List<ObjectInstance> placed = new ArrayList<>();

        for (ObjectInstance obj : objects) {
            int time = safeTime(obj.stlFile);
            boolean wasPlaced = false;

            log("  🔍 " + obj.stlFile.getName()
                    + " (" + fmt(time) + ", Z=" + obj.stlFile.getHeightMm() + "mm)");

            // 1st pass: prefer a non-empty plate (better bin utilisation)
            for (int i = plates.size() - 1; i >= 0 && !wasPlaced; i--) {
                BuildPlate plate = plates.get(i);
                if (!plate.isEmpty()
                        && plate.estimatedTimeSeconds + time <= maxDurationSeconds) {
                    if (plate.tryPlace(obj.stlFile, obj.instanceNumber) != null) {
                        wasPlaced = true;
                        log("    ✅ Added to existing plate #" + i
                                + " [" + phase + "] total=" + fmt(plate.estimatedTimeSeconds));
                    }
                }
            }

            // 2nd pass: any plate (including empty) with enough time budget
            for (int i = plates.size() - 1; i >= 0 && !wasPlaced; i--) {
                BuildPlate plate = plates.get(i);
                if (plate.estimatedTimeSeconds + time <= maxDurationSeconds) {
                    if (plate.tryPlace(obj.stlFile, obj.instanceNumber) != null) {
                        wasPlaced = true;
                        log("    ✅ Placed on plate #" + i
                                + " [" + phase + "] total=" + fmt(plate.estimatedTimeSeconds));
                    }
                }
            }

            if (wasPlaced) placed.add(obj);
            else           log("    ❌ Doesn't fit in " + phase + " plates");
        }

        return placed;
    }

    // ============================================================
    // CLUSTER CENTERING
    //
    // After all objects are assigned to a plate, re-pack them with a
    // compact top-left greedy scan (largest area first), then translate
    // the entire cluster so its bounding-box centre aligns with the
    // plate centre.  Objects stay close together while sitting as far
    // from every edge as possible.
    // ============================================================

    /**
     * Re-nests all objects on a plate compactly (top-left greedy, largest first),
     * then translates the entire cluster so its centre aligns with the plate centre.
     * Objects stay close together while being placed as far from edges as possible.
     */
    private static void centerCluster(BuildPlate plate) {
        if (plate.objects.isEmpty()) return;

        // Step 1: Re-pack compactly using top-left greedy scan (largest area first)
        List<PlacedObject> snapshot = new ArrayList<>(plate.objects);
        snapshot.sort((a, b) -> Double.compare(b.width * b.height, a.width * a.height));

        clearSurface(plate);
        plate.objects.clear();

        for (PlacedObject po : snapshot) {
            int w = (int) Math.ceil(po.width);
            int h = (int) Math.ceil(po.height);
            boolean placed = false;
            outer:
            for (int y = EDGE_MARGIN; y <= (int) plate.plateHeight - h - EDGE_MARGIN; y++) {
                for (int x = EDGE_MARGIN; x <= (int) plate.plateWidth - w - EDGE_MARGIN; x++) {
                    if (canPlaceOn(plate, x, y, w, h)) {
                        occupyOn(plate, x, y, w, h);
                        po.x = x;
                        po.y = y;
                        plate.objects.add(po);
                        placed = true;
                        break outer;
                    }
                }
            }
            if (!placed) {
                log("  ⚠️  centerCluster: could not place " + po.stlFile.getName());
                plate.objects.add(po);
            }
        }

        // Step 2: Single uniform X+Y offset for entire cluster (FR-005)
        // Per-row independent X shifts are prohibited — they destroy PIECE_GAP between rows.
        double clusterMinX = plate.objects.stream().mapToDouble(po -> po.x).min().orElse(0);
        double clusterMaxX = plate.objects.stream().mapToDouble(po -> po.x + po.width).max().orElse(0);
        double clusterMinY = plate.objects.stream().mapToDouble(po -> po.y).min().orElse(0);
        double clusterMaxY = plate.objects.stream().mapToDouble(po -> po.y + po.height).max().orElse(0);

        double clusterW = clusterMaxX - clusterMinX;
        double clusterH = clusterMaxY - clusterMinY;

        log(String.format("  🔍 centerCluster: plate=%.0fx%.0f | cluster=(%.1f,%.1f)→(%.1f,%.1f) size=%.0fx%.0f",
                plate.plateWidth, plate.plateHeight,
                clusterMinX, clusterMinY, clusterMaxX, clusterMaxY, clusterW, clusterH));

        double xOffset = (plate.plateWidth  - clusterW) / 2.0 - clusterMinX;
        double yOffset = (plate.plateHeight - clusterH) / 2.0 - clusterMinY;

        for (PlacedObject po : plate.objects) {
            po.x += xOffset;
            po.y += yOffset;
        }

        log(String.format("  🎯 Cluster centred %-15s | %d object(s) | xOff=%.1f yOff=%.1f",
                plate.printer.getName(), plate.objects.size(), xOffset, yOffset));
    }

    /** Clears the surface grid of a BuildPlate via reflection-free direct access. */
    private static void clearSurface(BuildPlate plate) {
        int pw = (int) plate.plateWidth;
        int ph = (int) plate.plateHeight;
        for (int x = 0; x < pw; x++)
            Arrays.fill(plate.surface[x], 0);
    }

    private static boolean canPlaceOn(BuildPlate plate, int x, int y, int w, int h) {
        for (int dy = 0; dy < h; dy++)
            for (int dx = 0; dx < w; dx++)
                if (plate.surface[x + dx][y + dy] != 0) return false;
        return true;
    }

    private static void occupyOn(BuildPlate plate, int x, int y, int w, int h) {
        int xEnd = Math.min(x + w + PIECE_GAP, (int) plate.plateWidth);
        int yEnd = Math.min(y + h + PIECE_GAP, (int) plate.plateHeight);
        for (int iy = y; iy < yEnd; iy++)
            for (int ix = x; ix < xEnd; ix++)
                plate.surface[ix][iy] = 1;
    }

    // ============================================================
    // UTILITIES
    // ============================================================

    private static List<Printer> filterPrinters(List<Printer> all, List<STLFile> stlFiles) {
        if (stlFiles.isEmpty()) return new ArrayList<>();
        STLFile ref = stlFiles.get(0);
        List<Printer> result = new ArrayList<>();
        for (Printer p : all)
            if (matchesMaterialAndColor(p, ref)) result.add(p);
        return result;
    }

    private static boolean matchesMaterialAndColor(Printer p, STLFile stl) {
        return p.getMaterial() != null && stl.getMaterial() != null
            && p.getMaterial().equalsIgnoreCase(stl.getMaterial())
            && p.getColor()    != null && stl.getColor()    != null
            && p.getColor().equalsIgnoreCase(stl.getColor());
    }

    private static int safeTime(STLFile stl) {
        Integer t = stl.getPrintTimeSeconds();
        return t != null ? t : 0;
    }

    /** Format seconds as "Xh Ym" */
    private static String fmt(int seconds) {
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
    }

    private static void log(String msg) {
        System.out.println(msg);
    }

    // ============================================================
    // REPORT
    // ============================================================

    public static String generateReport(NestingResult result, NestingConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 NESTING & SCHEDULING REPORT\n");
        sb.append("═══════════════════════════════════════\n\n");

        sb.append(String.format("Total objects  : %d%n", result.totalObjects));
        sb.append(String.format("  ⏰ Long-running (>14 h)        : %d%n", result.longRunningObjects));
        sb.append(String.format("  ✅ Meeting first deadline (%s): %d%n",
                config.firstDeadline.toLocalTime(), result.objectsMeetingFirstDeadline));
        sb.append(String.format("  ⚠️  Meeting second deadline (%s): %d%n",
                config.secondDeadline.toLocalTime(), result.objectsMeetingSecondDeadline));
        sb.append(String.format("  ❌ Unscheduled               : %d%n%n",
                result.unscheduledObjects.size()));

        sb.append(String.format("Total plates   : %d%n%n", result.scheduledPlates.size()));

        for (int i = 0; i < result.scheduledPlates.size(); i++) {
            ScheduledPlate sp = result.scheduledPlates.get(i);
            sb.append(String.format("  Plate %d | %s | %s → %s | %d object(s)%n",
                    i + 1,
                    sp.printer.getName(),
                    sp.startTime.toLocalTime(),
                    sp.endTime.toLocalTime(),
                    sp.plate.objects.size()));
        }

        if (!result.unscheduledObjects.isEmpty()) {
            sb.append("\n⚠️  UNSCHEDULED OBJECTS:\n");
            for (ObjectInstance obj : result.unscheduledObjects)
                sb.append(String.format("  - %s (copy %d) [Z=%.1fmm]%n",
                        obj.stlFile.getName(), obj.instanceNumber, obj.stlFile.getHeightMm()));
        }

        return sb.toString();
    }
}