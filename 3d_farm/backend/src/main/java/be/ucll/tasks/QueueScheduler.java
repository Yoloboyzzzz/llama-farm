package be.ucll.tasks;

import be.ucll.model.GcodeFile;
import be.ucll.model.Job;
import be.ucll.model.Printer;
import be.ucll.repository.GcodeFileRepository;
import be.ucll.repository.PrinterRepository;
import be.ucll.service.NotificationService;  // ✅ Add this
import jakarta.transaction.Transactional;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import be.ucll.util.OctoPrintClient;
import be.ucll.util.PrusaLinkClient;

import java.time.*;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;


@Service
public class QueueScheduler {

    private final GcodeFileRepository gcodeFileRepository;
    private final PrinterRepository printerRepository;
    private final OctoPrintClient octoPrintClient;
    private final PrusaLinkClient prusaLinkClient;
    private final NotificationService notificationService;  // ✅ Add this
    private final Random random = new Random();

    public QueueScheduler(
            GcodeFileRepository gcodeFileRepository,
            PrinterRepository printerRepository,
            OctoPrintClient octoPrintClient,
            PrusaLinkClient prusaLinkClient,
            NotificationService notificationService  // ✅ Add this
    ) {
        this.gcodeFileRepository = gcodeFileRepository;
        this.printerRepository = printerRepository;
        this.octoPrintClient = octoPrintClient;
        this.prusaLinkClient = prusaLinkClient;
        this.notificationService = notificationService;  // ✅ Add this
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void dispatchQueuedPrints() {
        try {
            dispatchQueuedPrintsInternal();
        } catch (Exception e) {
            System.err.println("❌ QueueScheduler crashed — will retry next cycle: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void dispatchQueuedPrintsInternal() {
        ZoneId zone = ZoneId.of("Europe/Brussels");
        LocalDateTime nowLocal = LocalDateTime.now(zone);
        Instant now = Instant.now();
        
        System.out.println("Running Queue at " + nowLocal.toLocalTime());
        
        LocalDateTime firstDeadline;
        LocalDateTime secondDeadline;
        
        LocalDate today = nowLocal.toLocalDate();
        LocalTime currentTime = nowLocal.toLocalTime();
        
        if (currentTime.isBefore(LocalTime.of(16, 0))) {
            firstDeadline = LocalDateTime.of(today, LocalTime.of(16, 0));
        } else {
            firstDeadline = LocalDateTime.of(today.plusDays(1), LocalTime.of(16, 0));
        }
        
        secondDeadline = LocalDateTime.of(today.plusDays(1), LocalTime.of(8, 0));
        
        Instant firstDeadlineInstant = firstDeadline.atZone(zone).toInstant();
        Instant secondDeadlineInstant = secondDeadline.atZone(zone).toInstant();
        
        Instant activeDeadline;
        String deadlineType;
        
        if (currentTime.isBefore(LocalTime.of(16, 0))) {
            activeDeadline = firstDeadlineInstant;
            deadlineType = "4pm";
            System.out.println("📅 Working towards 4pm deadline: " + firstDeadline.toLocalTime());
        } else if (currentTime.isBefore(LocalTime.of(23, 59))) {
            activeDeadline = secondDeadlineInstant;
            deadlineType = "8am";
            System.out.println("📅 Working towards 8am deadline: " + secondDeadline.toLocalTime() + " (next day)");
        } else {
            activeDeadline = secondDeadlineInstant;
            deadlineType = "8am";
            System.out.println("📅 Working towards 8am deadline: " + secondDeadline.toLocalTime());
        }

        List<GcodeFile> queue = gcodeFileRepository.findQueuedFiles();
        if (queue.isEmpty()) {
            System.out.println("   ℹ️  Queue is empty");
            return;
        }

        List<Printer> allIdlePrinters = printerRepository.findIdlePrinters().stream()
                .filter(Printer::isInUse)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        if (allIdlePrinters.isEmpty()) {
            System.out.println("   ℹ️  No idle printers available");
            return;
        }

        // Track IPs for which a notification was already created this run
        Set<String> notifiedIpsThisRun = new HashSet<>();

        System.out.println("   📋 Queue size: " + queue.size());
        System.out.println("   🖨️  Idle printers: " + allIdlePrinters.size());
        System.out.println();

        for (GcodeFile file : queue) {
            System.out.println("   Evaluating: " + file.getName());
            
            Instant endTime = now.plusSeconds(file.getDuration());
            
            boolean isLongPrint = file.getDuration() > (14 * 3600);
            boolean finishesBeforeDeadline = endTime.isBefore(activeDeadline) || endTime.equals(activeDeadline);
            
            if (!isLongPrint && !finishesBeforeDeadline) {
                System.out.println("      ⏭️  Skipped: Would finish at " + 
                    LocalDateTime.ofInstant(endTime, zone).toLocalTime() + 
                    " (after " + deadlineType + " deadline)");
                continue;
            }
            
            if (isLongPrint) {
                System.out.println("      ⚡ Long print (>14h) - starting immediately");
            } else {
                System.out.println("      ✓ Fits before " + deadlineType + " deadline (finishes at " + 
                    LocalDateTime.ofInstant(endTime, zone).toLocalTime() + ")");
            }

            List<Printer> candidates = allIdlePrinters.stream()
                .filter(p -> isCompatible(p, file))
                .toList();

            if (candidates.isEmpty()) {
                System.out.println("      ❌ No compatible printer available (need: " + 
                    file.getModel() + " / " + file.getMaterial() + " / " + file.getColor() + ")");
                continue;
            }

            Printer chosen = candidates.get(random.nextInt(candidates.size()));
            System.out.println("      🎯 Selected printer: " + chosen.getName());

            // ✅ CHECK FOR DUPLICATE IPs (multi-color setup)
            String chosenIp = chosen.getIp();
            boolean hasDuplicateIp = hasMultiplePrintersWithIp(chosenIp);

            if (hasDuplicateIp) {
                // Only notify when ALL printers sharing this IP are idle
                if (!areAllPrintersWithIpIdle(chosenIp)) {
                    System.out.println("      ⏳ Duplicate IP " + chosenIp + " - not all printers idle yet, skipping");
                    System.out.println();
                    continue;
                }

                // Only create one notification per IP group per scheduler run
                if (notifiedIpsThisRun.contains(chosenIp)) {
                    System.out.println("      ⏭️  Already notified for IP " + chosenIp + " this run - skipping");
                    System.out.println();
                    continue;
                }

                notifiedIpsThisRun.add(chosenIp);
                System.out.println("      ⚠️  All printers with IP " + chosenIp + " are idle - creating color confirmation notification");

                Job job = file.getJob();
                Long jobId = (job != null) ? job.getId() : null;

                notificationService.createColorConfirmationNotification(
                    chosen.getName(),
                    chosenIp,
                    file.getColor() != null ? file.getColor() : "Unknown",
                    file.getPath(),
                    jobId
                );

                System.out.println("      🔔 Notification created - waiting for admin confirmation");
                System.out.println();
                continue;
            }

            // ✅ NO duplicate IP - start print
            if ("octoprint".equalsIgnoreCase(chosen.getConnectionType())) {
                System.out.println("      ▶️  Starting print on: " + chosen.getName() + " (OctoPrint)");
                octoPrintClient.uploadToOctoPrintAndPrint(chosen, file);
            } else if ("prusalink".equalsIgnoreCase(chosen.getConnectionType())) {
                // PrusaLink: retry on other candidates if upload fails
                List<Printer> remaining = new java.util.ArrayList<>(candidates);
                boolean uploaded = false;

                while (!remaining.isEmpty() && !uploaded) {
                    System.out.println("      ▶️  Trying upload on: " + chosen.getName() + " (PrusaLink)");
                    boolean ok = prusaLinkClient.uploadToPrusaLinkAndPrint(chosen, file);
                    if (ok) {
                        uploaded = true;
                    } else {
                        System.out.println("      ⚠️  Upload failed for " + chosen.getName() + " — skipping to next candidate");
                        remaining.remove(chosen);
                        allIdlePrinters.remove(chosen);
                        if (!remaining.isEmpty()) {
                            chosen = remaining.get(random.nextInt(remaining.size()));
                        }
                    }
                }

                if (!uploaded) {
                    System.out.println("      ❌ All PrusaLink candidates failed for: " + file.getName() + " — leaving in queue");
                    System.out.println();
                    continue;
                }
            } else {
                continue;
            }

            // Update DB
            file.setStatus("printing");
            file.setStartedAt(LocalDateTime.now());
            file.setPrinter(chosen);
            file.setQueuePosition(null);
            chosen.setCurrentFile(file);
            chosen.setWeightOfCurrentPrint((double) file.getWeight());
            chosen.setStatus("printing");

            allIdlePrinters.remove(chosen);

            System.out.println("      ✅ Print started successfully on " + chosen.getName());
            System.out.println();

            if (allIdlePrinters.isEmpty()) {
                System.out.println("   ℹ️  All idle printers now assigned");
                break;
            }
        }
    }

    private boolean hasMultiplePrintersWithIp(String ip) {
        return printerRepository.findAll().stream()
            .filter(p -> ip.equals(p.getIp()))
            .count() >= 2;
    }

    private boolean areAllPrintersWithIpIdle(String ip) {
        List<Printer> printersWithIp = printerRepository.findAll().stream()
            .filter(p -> ip.equals(p.getIp()))
            .toList();
        return printersWithIp.stream().allMatch(p ->
            "idle".equalsIgnoreCase(p.getStatus()) &&
            (p.getWeightOfCurrentPrint() == null || p.getWeightOfCurrentPrint() == 0)
        );
    }

    private boolean isCompatible(Printer printer, GcodeFile file) {
        boolean modelOk = file.getModel() == null || file.getModel().equalsIgnoreCase(printer.getModel());
        boolean materialOk = file.getMaterial() == null || file.getMaterial().equalsIgnoreCase(printer.getMaterial());
        boolean colorOk = file.getColor() == null || file.getColor().equalsIgnoreCase(printer.getColor());

        return modelOk && materialOk && colorOk;
    }
}