package be.ucll.service;

import be.ucll.model.Notification;
import be.ucll.model.Notification.NotificationType;
import be.ucll.repository.NotificationRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    private Notification nozzleNotification;
    private Notification colorNotification;

    @BeforeEach
    void setUp() {
        nozzleNotification = new Notification(
                NotificationType.NOZZLE_CLEANING,
                "MK4 #1",
                "192.168.1.10",
                "Please clean the nozzle of printer MK4 #1"
        );
        nozzleNotification.setNotificationId(1L);

        colorNotification = new Notification(
                NotificationType.COLOR_CONFIRMATION,
                "MK4 #2",
                "192.168.1.11",
                "Is Red filament loaded on printer MK4 #2?"
        );
        colorNotification.setNotificationId(2L);
    }

    // ============================================================
    // createNozzleCleaningNotification Tests
    // ============================================================

    @Test
    void createNozzleCleaningNotification_WhenNoneExists_ShouldSaveAndReturnNew() {
        when(notificationRepository.findActiveByTypeAndPrinter(NotificationType.NOZZLE_CLEANING, "MK4 #1"))
                .thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenReturn(nozzleNotification);

        Notification result = notificationService.createNozzleCleaningNotification("MK4 #1", "192.168.1.10");

        assertNotNull(result);
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    void createNozzleCleaningNotification_MessageShouldContainPrinterName() {
        when(notificationRepository.findActiveByTypeAndPrinter(any(), anyString()))
                .thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        Notification result = notificationService.createNozzleCleaningNotification("MK4 #1", "192.168.1.10");

        assertTrue(result.getMessage().contains("MK4 #1"));
    }

    @Test
    void createNozzleCleaningNotification_WhenAlreadyExists_ShouldReturnExistingWithoutCreatingNew() {
        when(notificationRepository.findActiveByTypeAndPrinter(NotificationType.NOZZLE_CLEANING, "MK4 #1"))
                .thenReturn(Optional.of(nozzleNotification));

        Notification result = notificationService.createNozzleCleaningNotification("MK4 #1", "192.168.1.10");

        assertEquals(nozzleNotification.getNotificationId(), result.getNotificationId());
        verify(notificationRepository, never()).save(any());
    }

    // ============================================================
    // createColorConfirmationNotification Tests
    // ============================================================

    @Test
    void createColorConfirmationNotification_WhenNoneExists_ShouldSaveAndReturn() {
        when(notificationRepository.findActiveByTypeAndPrinter(NotificationType.COLOR_CONFIRMATION, "MK4 #2"))
                .thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenReturn(colorNotification);

        Notification result = notificationService.createColorConfirmationNotification(
                "MK4 #2", "192.168.1.11", "Red", "/gcode/file.gcode", 42L);

        assertNotNull(result);
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    void createColorConfirmationNotification_ShouldEmbedColorAndPrinterInMessage() {
        when(notificationRepository.findActiveByTypeAndPrinter(any(), anyString()))
                .thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        Notification result = notificationService.createColorConfirmationNotification(
                "MK4 #2", "192.168.1.11", "Blue", "/path/file.gcode", 5L);

        assertTrue(result.getMessage().contains("Blue"));
        assertTrue(result.getMessage().contains("MK4 #2"));
    }

    @Test
    void createColorConfirmationNotification_ShouldStoreMetadataWithColorAndFilePath() {
        when(notificationRepository.findActiveByTypeAndPrinter(any(), anyString()))
                .thenReturn(Optional.empty());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        Notification result = notificationService.createColorConfirmationNotification(
                "MK4 #2", "192.168.1.11", "Red", "/gcode/file.gcode", 42L);

        assertNotNull(result.getMetadata());
        assertTrue(result.getMetadata().contains("Red"));
        assertTrue(result.getMetadata().contains("/gcode/file.gcode"));
    }

    @Test
    void createColorConfirmationNotification_WhenAlreadyExists_ShouldReturnExistingWithoutCreatingNew() {
        when(notificationRepository.findActiveByTypeAndPrinter(NotificationType.COLOR_CONFIRMATION, "MK4 #2"))
                .thenReturn(Optional.of(colorNotification));

        Notification result = notificationService.createColorConfirmationNotification(
                "MK4 #2", "192.168.1.11", "Red", "/gcode/file.gcode", 42L);

        assertEquals(colorNotification.getNotificationId(), result.getNotificationId());
        verify(notificationRepository, never()).save(any());
    }

    // ============================================================
    // autoDismissNozzleCleaningNotification Tests
    // ============================================================

    @Test
    void autoDismissNozzleCleaningNotification_WhenExists_ShouldSetDismissedAtAndMarkAsAutoDismissed() {
        when(notificationRepository.findActiveByTypeAndPrinter(NotificationType.NOZZLE_CLEANING, "MK4 #1"))
                .thenReturn(Optional.of(nozzleNotification));
        when(notificationRepository.save(any(Notification.class))).thenReturn(nozzleNotification);

        notificationService.autoDismissNozzleCleaningNotification("MK4 #1");

        assertNotNull(nozzleNotification.getDismissedAt());
        assertTrue(nozzleNotification.getAutoDismissed());
        verify(notificationRepository, times(1)).save(nozzleNotification);
    }

    @Test
    void autoDismissNozzleCleaningNotification_WhenNotExists_ShouldDoNothing() {
        when(notificationRepository.findActiveByTypeAndPrinter(NotificationType.NOZZLE_CLEANING, "UnknownPrinter"))
                .thenReturn(Optional.empty());

        notificationService.autoDismissNozzleCleaningNotification("UnknownPrinter");

        verify(notificationRepository, never()).save(any());
    }

    // ============================================================
    // dismissNotification Tests
    // ============================================================

    @Test
    void dismissNotification_WhenExists_ShouldSetDismissedAtAndMarkAsManualDismiss() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(nozzleNotification));
        when(notificationRepository.save(any(Notification.class))).thenReturn(nozzleNotification);

        notificationService.dismissNotification(1L);

        assertNotNull(nozzleNotification.getDismissedAt());
        assertFalse(nozzleNotification.getAutoDismissed()); // manually dismissed → false
        verify(notificationRepository, times(1)).save(nozzleNotification);
    }

    @Test
    void dismissNotification_WhenNotExists_ShouldDoNothing() {
        when(notificationRepository.findById(999L)).thenReturn(Optional.empty());

        notificationService.dismissNotification(999L);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void autoDismiss_VsManualDismiss_ShouldSetAutoDismissedFlagDifferently() {
        // Two separate notifications – one auto-dismissed, one manually dismissed
        Notification auto = new Notification(NotificationType.NOZZLE_CLEANING, "P1", "1.1.1.1", "msg");
        Notification manual = new Notification(NotificationType.NOZZLE_CLEANING, "P2", "1.1.1.2", "msg");
        manual.setNotificationId(10L);

        when(notificationRepository.findActiveByTypeAndPrinter(NotificationType.NOZZLE_CLEANING, "P1"))
                .thenReturn(Optional.of(auto));
        when(notificationRepository.findById(10L)).thenReturn(Optional.of(manual));
        when(notificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        notificationService.autoDismissNozzleCleaningNotification("P1");
        notificationService.dismissNotification(10L);

        assertTrue(auto.getAutoDismissed());
        assertFalse(manual.getAutoDismissed());
    }

    // ============================================================
    // getActiveNotifications Tests
    // ============================================================

    @Test
    void getActiveNotifications_ShouldReturnAllActiveNotifications() {
        when(notificationRepository.findAllActive())
                .thenReturn(Arrays.asList(nozzleNotification, colorNotification));

        List<Notification> result = notificationService.getActiveNotifications();

        assertEquals(2, result.size());
        verify(notificationRepository, times(1)).findAllActive();
    }

    @Test
    void getActiveNotifications_WhenNone_ShouldReturnEmptyList() {
        when(notificationRepository.findAllActive()).thenReturn(Collections.emptyList());

        List<Notification> result = notificationService.getActiveNotifications();

        assertTrue(result.isEmpty());
    }

    // ============================================================
    // getActiveNotificationsForPrinter Tests
    // ============================================================

    @Test
    void getActiveNotificationsForPrinter_ShouldReturnOnlyThatPrintersNotifications() {
        when(notificationRepository.findActiveByPrinter("MK4 #1"))
                .thenReturn(Collections.singletonList(nozzleNotification));

        List<Notification> result = notificationService.getActiveNotificationsForPrinter("MK4 #1");

        assertEquals(1, result.size());
        assertEquals("MK4 #1", result.get(0).getPrinterName());
    }

    @Test
    void getActiveNotificationsForPrinter_WhenNone_ShouldReturnEmptyList() {
        when(notificationRepository.findActiveByPrinter("UnknownPrinter"))
                .thenReturn(Collections.emptyList());

        List<Notification> result = notificationService.getActiveNotificationsForPrinter("UnknownPrinter");

        assertTrue(result.isEmpty());
    }

    // ============================================================
    // hasDuplicateIp Tests
    // ============================================================

    @Test
    void hasDuplicateIp_WithTwoOrMoreNotifications_ShouldReturnTrue() {
        when(notificationRepository.findActiveColorConfirmationsByIp("192.168.1.10"))
                .thenReturn(Arrays.asList(colorNotification, colorNotification));

        assertTrue(notificationService.hasDuplicateIp("192.168.1.10"));
    }

    @Test
    void hasDuplicateIp_WithExactlyOneNotification_ShouldReturnFalse() {
        when(notificationRepository.findActiveColorConfirmationsByIp("192.168.1.10"))
                .thenReturn(Collections.singletonList(colorNotification));

        assertFalse(notificationService.hasDuplicateIp("192.168.1.10"));
    }

    @Test
    void hasDuplicateIp_WithNoNotifications_ShouldReturnFalse() {
        when(notificationRepository.findActiveColorConfirmationsByIp("192.168.1.99"))
                .thenReturn(Collections.emptyList());

        assertFalse(notificationService.hasDuplicateIp("192.168.1.99"));
    }

    // ============================================================
    // parseMetadata Tests
    // ============================================================

    @Test
    void parseMetadata_WithValidJson_ShouldReturnPopulatedMap() {
        String json = "{\"color\":\"Red\",\"filePath\":\"/path/file.gcode\",\"jobId\":42}";

        Map<String, Object> result = notificationService.parseMetadata(json);

        assertEquals("Red", result.get("color"));
        assertEquals("/path/file.gcode", result.get("filePath"));
    }

    @Test
    void parseMetadata_WithInvalidJson_ShouldReturnEmptyMap() {
        Map<String, Object> result = notificationService.parseMetadata("not { valid } json {{");

        assertTrue(result.isEmpty());
    }

    @Test
    void parseMetadata_WithEmptyString_ShouldReturnEmptyMap() {
        Map<String, Object> result = notificationService.parseMetadata("");

        assertTrue(result.isEmpty());
    }

    @Test
    void parseMetadata_WithEmptyJsonObject_ShouldReturnEmptyMap() {
        Map<String, Object> result = notificationService.parseMetadata("{}");

        assertTrue(result.isEmpty());
    }
}
