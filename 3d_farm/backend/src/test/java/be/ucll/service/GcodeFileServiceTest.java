package be.ucll.service;

import be.ucll.model.GcodeFile;
import be.ucll.repository.GcodeFileRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GcodeFileServiceTest {

    @Mock
    private GcodeFileRepository gcodeFileRepository;

    @InjectMocks
    private GcodeFileService gcodeFileService;

    private GcodeFile testFile;

    @BeforeEach
    void setUp() {
        testFile = new GcodeFile();
        testFile.setId(1L);
        testFile.setName("test.gcode");
        testFile.setPath("/gcode/test.gcode");
        testFile.setStatus("done");
        testFile.setStartedAt(LocalDateTime.now().minusHours(2));
        testFile.setQueuePostion(3);
    }

    // ============================================================
    // requeueFile Tests
    // ============================================================

    @Test
    void requeueFile_WhenFound_ShouldMoveToEndOfQueue() {
        when(gcodeFileRepository.findById(1L)).thenReturn(Optional.of(testFile));
        when(gcodeFileRepository.findMaxQueuePosition()).thenReturn(5);
        when(gcodeFileRepository.save(any(GcodeFile.class))).thenReturn(testFile);

        gcodeFileService.requeueFile(1L);

        assertEquals(6, testFile.getQueuePosition()); // max + 1
    }

    @Test
    void requeueFile_WhenFound_ShouldResetStatusToWaiting() {
        when(gcodeFileRepository.findById(1L)).thenReturn(Optional.of(testFile));
        when(gcodeFileRepository.findMaxQueuePosition()).thenReturn(3);
        when(gcodeFileRepository.save(any(GcodeFile.class))).thenReturn(testFile);

        gcodeFileService.requeueFile(1L);

        assertEquals("waiting", testFile.getStatus());
    }

    @Test
    void requeueFile_WhenFound_ShouldClearStartedAt() {
        testFile.setStartedAt(LocalDateTime.now().minusHours(1));
        when(gcodeFileRepository.findById(1L)).thenReturn(Optional.of(testFile));
        when(gcodeFileRepository.findMaxQueuePosition()).thenReturn(2);
        when(gcodeFileRepository.save(any(GcodeFile.class))).thenReturn(testFile);

        gcodeFileService.requeueFile(1L);

        assertNull(testFile.getStartedAt());
    }

    @Test
    void requeueFile_WhenQueueIsEmpty_ShouldPlaceAtPositionOne() {
        when(gcodeFileRepository.findById(1L)).thenReturn(Optional.of(testFile));
        when(gcodeFileRepository.findMaxQueuePosition()).thenReturn(null); // empty queue
        when(gcodeFileRepository.save(any(GcodeFile.class))).thenReturn(testFile);

        gcodeFileService.requeueFile(1L);

        assertEquals(1, testFile.getQueuePosition()); // 0 + 1
    }

    @Test
    void requeueFile_ShouldCallSaveExactlyOnce() {
        when(gcodeFileRepository.findById(1L)).thenReturn(Optional.of(testFile));
        when(gcodeFileRepository.findMaxQueuePosition()).thenReturn(4);
        when(gcodeFileRepository.save(any(GcodeFile.class))).thenReturn(testFile);

        gcodeFileService.requeueFile(1L);

        verify(gcodeFileRepository, times(1)).save(testFile);
    }

    @Test
    void requeueFile_WhenFileNotFound_ShouldThrowRuntimeException() {
        when(gcodeFileRepository.findById(999L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> gcodeFileService.requeueFile(999L));

        assertTrue(ex.getMessage().contains("999"));
        verify(gcodeFileRepository, never()).save(any());
    }

    @Test
    void requeueFile_WhenFileNotFound_ShouldNeverQueryMaxPosition() {
        when(gcodeFileRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> gcodeFileService.requeueFile(999L));

        verify(gcodeFileRepository, never()).findMaxQueuePosition();
    }

    // ============================================================
    // deleteFile Tests
    // ============================================================

    @Test
    void deleteFile_WhenFound_ShouldDeleteRecord() {
        when(gcodeFileRepository.findById(1L)).thenReturn(Optional.of(testFile));
        doNothing().when(gcodeFileRepository).delete(testFile);

        gcodeFileService.deleteFile(1L);

        verify(gcodeFileRepository, times(1)).delete(testFile);
    }

    @Test
    void deleteFile_ShouldDeleteExactlyTheFoundFile() {
        when(gcodeFileRepository.findById(1L)).thenReturn(Optional.of(testFile));
        doNothing().when(gcodeFileRepository).delete(testFile);

        gcodeFileService.deleteFile(1L);

        // Verify that delete was called with the exact object that was found
        verify(gcodeFileRepository, times(1)).delete(testFile);
        verify(gcodeFileRepository, never()).delete(argThat(f -> f != testFile));
    }

    @Test
    void deleteFile_WhenNotFound_ShouldThrowRuntimeException() {
        when(gcodeFileRepository.findById(999L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> gcodeFileService.deleteFile(999L));

        assertTrue(ex.getMessage().contains("999"));
        verify(gcodeFileRepository, never()).delete(any());
    }

    @Test
    void deleteFile_WhenNotFound_ShouldThrowBeforeAttemptingDelete() {
        when(gcodeFileRepository.findById(42L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> gcodeFileService.deleteFile(42L));

        verify(gcodeFileRepository, never()).delete(any(GcodeFile.class));
    }
}
