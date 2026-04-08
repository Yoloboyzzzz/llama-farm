package be.ucll.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Column(name = "printer_name", nullable = false)
    private String printerName;

    @Column(name = "printer_ip", length = 50)
    private String printerIp;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String metadata;  // Store as JSON string

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "dismissed_at")
    private LocalDateTime dismissedAt;

    @Column(name = "auto_dismissed")
    private Boolean autoDismissed = false;

    // Enum for notification types
    public enum NotificationType {
        NOZZLE_CLEANING,
        COLOR_CONFIRMATION,
        PRINTER_ERROR,
        PRINTER_BUSY
    }

    // Constructors
    public Notification() {}

    public Notification(NotificationType type, String printerName, String printerIp, String message) {
        this.type = type;
        this.printerName = printerName;
        this.printerIp = printerIp;
        this.message = message;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(Long notificationId) {
        this.notificationId = notificationId;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public String getPrinterName() {
        return printerName;
    }

    public void setPrinterName(String printerName) {
        this.printerName = printerName;
    }

    public String getPrinterIp() {
        return printerIp;
    }

    public void setPrinterIp(String printerIp) {
        this.printerIp = printerIp;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getDismissedAt() {
        return dismissedAt;
    }

    public void setDismissedAt(LocalDateTime dismissedAt) {
        this.dismissedAt = dismissedAt;
    }

    public Boolean getAutoDismissed() {
        return autoDismissed;
    }

    public void setAutoDismissed(Boolean autoDismissed) {
        this.autoDismissed = autoDismissed;
    }

    public boolean isActive() {
        return dismissedAt == null;
    }
}