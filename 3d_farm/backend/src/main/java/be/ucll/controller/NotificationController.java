package be.ucll.controller;

import be.ucll.model.GcodeFile;
import be.ucll.model.Notification;
import be.ucll.model.Printer;
import be.ucll.repository.GcodeFileRepository;
import be.ucll.repository.PrinterRepository;
import be.ucll.service.NotificationService;
import be.ucll.util.OctoPrintClient;
import be.ucll.util.PrusaLinkClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/notifications")
@PreAuthorize("hasRole('ADMIN')")
public class NotificationController {

    private final NotificationService notificationService;
    private final PrinterRepository printerRepository;
    private final GcodeFileRepository gcodeFileRepository;
    private final OctoPrintClient octoPrintClient;
    private final PrusaLinkClient prusaLinkClient;

    public NotificationController(
            NotificationService notificationService,
            PrinterRepository printerRepository,
            GcodeFileRepository gcodeFileRepository,
            OctoPrintClient octoPrintClient,
            PrusaLinkClient prusaLinkClient
    ) {
        this.notificationService = notificationService;
        this.printerRepository = printerRepository;
        this.gcodeFileRepository = gcodeFileRepository;
        this.octoPrintClient = octoPrintClient;
        this.prusaLinkClient = prusaLinkClient;
    }

    @GetMapping
    public ResponseEntity<List<NotificationDTO>> getActiveNotifications() {
        List<Notification> notifications = notificationService.getActiveNotifications();
        List<NotificationDTO> dtos = notifications.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/{id}/dismiss")
    public ResponseEntity<?> dismissNotification(@PathVariable Long id) {
        notificationService.dismissNotification(id);
        return ResponseEntity.ok(Map.of("message", "Notification dismissed"));
    }

    @PostMapping("/{id}/confirm-color")
    @Transactional
    public ResponseEntity<?> confirmColorAndStart(@PathVariable Long id) {
        Notification notification = notificationService.getActiveNotifications().stream()
                .filter(n -> n.getNotificationId().equals(id))
                .findFirst()
                .orElse(null);
        
        if (notification == null) {
            return ResponseEntity.notFound().build();
        }

        if (notification.getType() != Notification.NotificationType.COLOR_CONFIRMATION) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "This action is only for color confirmation notifications"));
        }

        Map<String, Object> metadata = notificationService.parseMetadata(notification.getMetadata());
        String filePath = (String) metadata.get("filePath");

        Printer printer = printerRepository.findAll().stream()
                .filter(p -> p.getName().equals(notification.getPrinterName()))
                .findFirst()
                .orElse(null);
        
        if (printer == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Printer not found: " + notification.getPrinterName()));
        }

        GcodeFile gcodeFile = gcodeFileRepository.findAll().stream()
                .filter(f -> filePath.equals(f.getPath()))
                .findFirst()
                .orElse(null);
        
        if (gcodeFile == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "G-code file not found: " + filePath));
        }

        try {
            if ("octoprint".equalsIgnoreCase(printer.getConnectionType())) {
                octoPrintClient.uploadToOctoPrintAndPrint(printer, gcodeFile);
            } else if ("prusalink".equalsIgnoreCase(printer.getConnectionType())) {
                prusaLinkClient.uploadToPrusaLinkAndPrint(printer, gcodeFile);
            } else {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Unknown printer connection type: " + printer.getConnectionType()));
            }

            gcodeFile.setStatus("printing");
            gcodeFile.setStartedAt(LocalDateTime.now());
            gcodeFile.setPrinter(printer);
            gcodeFile.setQueuePosition(null);
            
            printer.setCurrentFile(gcodeFile);
            printer.setWeightOfCurrentPrint((double) gcodeFile.getWeight());
            printer.setStatus("printing");
            
            gcodeFileRepository.save(gcodeFile);
            printerRepository.save(printer);

            notificationService.dismissNotification(id);

            return ResponseEntity.ok(Map.of(
                "message", "Print confirmed and started",
                "printerName", printer.getName(),
                "fileName", gcodeFile.getName(),
                "color", metadata.get("color")
            ));

        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to start print: " + e.getMessage()));
        }
    }

    private NotificationDTO toDTO(Notification notification) {
        Map<String, Object> metadata = notificationService.parseMetadata(notification.getMetadata());
        return new NotificationDTO(
            notification.getNotificationId(),
            notification.getType().name(),
            notification.getPrinterName(),
            notification.getPrinterIp(),
            notification.getMessage(),
            metadata,
            notification.getCreatedAt().toString()
        );
    }

    public record NotificationDTO(
        Long id,
        String type,
        String printerName,
        String printerIp,
        String message,
        Map<String, Object> metadata,
        String createdAt
    ) {}
}