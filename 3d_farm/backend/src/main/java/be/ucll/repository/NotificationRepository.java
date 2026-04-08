package be.ucll.repository;

import be.ucll.model.Notification;
import be.ucll.model.Notification.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // Find all active (not dismissed) notifications
    @Query("SELECT n FROM Notification n WHERE n.dismissedAt IS NULL ORDER BY n.createdAt DESC")
    List<Notification> findAllActive();

    // Find active notification for specific printer and type
    @Query("SELECT n FROM Notification n WHERE n.type = ?1 AND n.printerName = ?2 AND n.dismissedAt IS NULL")
    Optional<Notification> findActiveByTypeAndPrinter(NotificationType type, String printerName);

    // Find all active notifications for a specific printer
    @Query("SELECT n FROM Notification n WHERE n.printerName = ?1 AND n.dismissedAt IS NULL")
    List<Notification> findActiveByPrinter(String printerName);

    // Find active color confirmation notifications by IP address
    @Query("SELECT n FROM Notification n WHERE n.type = 'COLOR_CONFIRMATION' AND n.printerIp = ?1 AND n.dismissedAt IS NULL")
    List<Notification> findActiveColorConfirmationsByIp(String printerIp);
}