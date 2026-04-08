package be.ucll.service;

import be.ucll.model.Notification;
import be.ucll.model.Notification.NotificationType;
import be.ucll.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Create a nozzle cleaning notification (only if one doesn't already exist for this printer)
     */
    @Transactional
    public Notification createNozzleCleaningNotification(String printerName, String printerIp) {
        // Check if notification already exists
        Optional<Notification> existing = notificationRepository
                .findActiveByTypeAndPrinter(NotificationType.NOZZLE_CLEANING, printerName);
        
        if (existing.isPresent()) {
            return existing.get();  // Don't create duplicate
        }

        Notification notification = new Notification(
            NotificationType.NOZZLE_CLEANING,
            printerName,
            printerIp,
            String.format("Please clean the nozzle of printer %s", printerName)
        );

        return notificationRepository.save(notification);
    }

    /**
     * Create a color confirmation notification (only if one doesn't already exist)
     */
    @Transactional
    public Notification createColorConfirmationNotification(
            String printerName, 
            String printerIp, 
            String color,
            String filePath,
            Long jobId
    ) {
        // Check if notification already exists
        Optional<Notification> existing = notificationRepository
                .findActiveByTypeAndPrinter(NotificationType.COLOR_CONFIRMATION, printerName);
        
        if (existing.isPresent()) {
            return existing.get();  // Don't create duplicate
        }

        String message = String.format(
            "Is %s filament loaded on printer %s?", 
            color, 
            printerName
        );

        Notification notification = new Notification(
            NotificationType.COLOR_CONFIRMATION,
            printerName,
            printerIp,
            message
        );

        // Store metadata as JSON
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("color", color);
            metadata.put("filePath", filePath);
            metadata.put("jobId", jobId);
            notification.setMetadata(objectMapper.writeValueAsString(metadata));
        } catch (Exception e) {
            // Fallback if JSON serialization fails
            notification.setMetadata(String.format("{\"color\":\"%s\",\"filePath\":\"%s\"}", color, filePath));
        }

        return notificationRepository.save(notification);
    }

    /**
     * Create a printer error notification (only if one doesn't already exist for this printer)
     */
    @Transactional
    public Notification createPrinterErrorNotification(String printerName, String printerIp, String errorMessage) {
        Optional<Notification> existing = notificationRepository
                .findActiveByTypeAndPrinter(NotificationType.PRINTER_ERROR, printerName);

        if (existing.isPresent()) {
            return existing.get();  // Don't create duplicate
        }

        Notification notification = new Notification(
            NotificationType.PRINTER_ERROR,
            printerName,
            printerIp,
            String.format("Printer %s has an error: %s", printerName, errorMessage)
        );

        return notificationRepository.save(notification);
    }

    /**
     * Create or UPDATE a printer error notification with a specific message.
     * Used when we have a detailed error (e.g. PrusaLink ERROR/ATTENTION) to
     * overwrite a previous generic "unreachable" message on the same notification.
     */
    @Transactional
    public Notification createOrUpdatePrinterErrorNotification(String printerName, String printerIp, String errorMessage) {
        String fullMessage = String.format("Printer %s has an error: %s", printerName, errorMessage);

        Optional<Notification> existing = notificationRepository
                .findActiveByTypeAndPrinter(NotificationType.PRINTER_ERROR, printerName);

        if (existing.isPresent()) {
            Notification n = existing.get();
            n.setMessage(fullMessage);
            return notificationRepository.save(n);
        }

        return notificationRepository.save(new Notification(
            NotificationType.PRINTER_ERROR, printerName, printerIp, fullMessage
        ));
    }

    /**
     * Auto-dismiss printer error notification when printer recovers
     */
    @Transactional
    public void autoDismissPrinterErrorNotification(String printerName) {
        Optional<Notification> notification = notificationRepository
                .findActiveByTypeAndPrinter(NotificationType.PRINTER_ERROR, printerName);

        if (notification.isPresent()) {
            Notification n = notification.get();
            n.setDismissedAt(LocalDateTime.now());
            n.setAutoDismissed(true);
            notificationRepository.save(n);
        }
    }

    /**
     * Create a printer busy notification (filament change/load in progress)
     */
    @Transactional
    public Notification createPrinterBusyNotification(String printerName, String printerIp) {
        Optional<Notification> existing = notificationRepository
                .findActiveByTypeAndPrinter(NotificationType.PRINTER_BUSY, printerName);

        if (existing.isPresent()) {
            return existing.get();
        }

        Notification notification = new Notification(
            NotificationType.PRINTER_BUSY,
            printerName,
            printerIp,
            String.format("Printer %s is busy: filament operation in progress", printerName)
        );

        return notificationRepository.save(notification);
    }

    /**
     * Auto-dismiss printer busy notification when printer is no longer busy
     */
    @Transactional
    public void autoDismissPrinterBusyNotification(String printerName) {
        Optional<Notification> notification = notificationRepository
                .findActiveByTypeAndPrinter(NotificationType.PRINTER_BUSY, printerName);

        if (notification.isPresent()) {
            Notification n = notification.get();
            n.setDismissedAt(LocalDateTime.now());
            n.setAutoDismissed(true);
            notificationRepository.save(n);
        }
    }

    /**
     * Auto-dismiss nozzle cleaning notification when printer is no longer paused
     */
    @Transactional
    public void autoDismissNozzleCleaningNotification(String printerName) {
        Optional<Notification> notification = notificationRepository
                .findActiveByTypeAndPrinter(NotificationType.NOZZLE_CLEANING, printerName);
        
        if (notification.isPresent()) {
            Notification n = notification.get();
            n.setDismissedAt(LocalDateTime.now());
            n.setAutoDismissed(true);
            notificationRepository.save(n);
        }
    }

    /**
     * Manually dismiss notification (user clicked button)
     */
    @Transactional
    public void dismissNotification(Long notificationId) {
        Optional<Notification> notification = notificationRepository.findById(notificationId);
        
        if (notification.isPresent()) {
            Notification n = notification.get();
            n.setDismissedAt(LocalDateTime.now());
            n.setAutoDismissed(false);  // Manually dismissed
            notificationRepository.save(n);
        }
    }

    /**
     * Get all active notifications
     */
    public List<Notification> getActiveNotifications() {
        return notificationRepository.findAllActive();
    }

    /**
     * Get active notifications for a specific printer
     */
    public List<Notification> getActiveNotificationsForPrinter(String printerName) {
        return notificationRepository.findActiveByPrinter(printerName);
    }

    /**
     * Check if duplicate IP exists (2+ printers with same IP)
     */
    public boolean hasDuplicateIp(String printerIp) {
        List<Notification> notifications = notificationRepository.findActiveColorConfirmationsByIp(printerIp);
        return notifications.size() >= 2;
    }

    /**
     * Parse metadata from JSON string
     */
    public Map<String, Object> parseMetadata(String metadataJson) {
        try {
            return objectMapper.readValue(metadataJson, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}