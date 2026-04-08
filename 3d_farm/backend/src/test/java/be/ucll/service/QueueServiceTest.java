package be.ucll.service;

import be.ucll.dto.QueueItemDTO;
import be.ucll.model.GcodeFile;
import be.ucll.repository.GcodeFileRepository;

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
class QueueServiceTest {

    @Mock
    private GcodeFileRepository repo;

    @InjectMocks
    private QueueService queueService;

    private GcodeFile waitingFile1;
    private GcodeFile waitingFile2;

    @BeforeEach
    void setUp() {
        waitingFile1 = new GcodeFile();
        waitingFile1.setId(1L);
        waitingFile1.setName("file1.gcode");
        waitingFile1.setStatus("waiting");
        waitingFile1.setModel("MK4");
        waitingFile1.setQueuePostion(1);

        waitingFile2 = new GcodeFile();
        waitingFile2.setId(2L);
        waitingFile2.setName("file2.gcode");
        waitingFile2.setStatus("waiting");
        waitingFile2.setModel("MINI");
        waitingFile2.setQueuePostion(2);
    }

    // ============================================================
    // getQueue Tests
    // ============================================================

    @Test
    void getQueue_ShouldReturnWaitingFilesAsDTOs() {
        when(repo.findAllByStatusOrderByQueuePositionAsc("waiting"))
                .thenReturn(Arrays.asList(waitingFile1, waitingFile2));

        List<QueueItemDTO> result = queueService.getQueue();

        assertEquals(2, result.size());
    }

    @Test
    void getQueue_ShouldCorrectlyMapIdFilenameStatusModelAndPosition() {
        when(repo.findAllByStatusOrderByQueuePositionAsc("waiting"))
                .thenReturn(Collections.singletonList(waitingFile1));

        QueueItemDTO dto = queueService.getQueue().get(0);

        assertEquals(1L, dto.getId());
        assertEquals("file1.gcode", dto.getFilename());
        assertEquals("waiting", dto.getStatus());
        assertEquals("MK4", dto.getPrinterModel());
        assertEquals(1, dto.getPosition());
    }

    @Test
    void getQueue_WhenEmpty_ShouldReturnEmptyList() {
        when(repo.findAllByStatusOrderByQueuePositionAsc("waiting"))
                .thenReturn(Collections.emptyList());

        List<QueueItemDTO> result = queueService.getQueue();

        assertTrue(result.isEmpty());
    }

    @Test
    void getQueue_ShouldOnlyQueryForWaitingStatus() {
        when(repo.findAllByStatusOrderByQueuePositionAsc("waiting"))
                .thenReturn(Collections.emptyList());

        queueService.getQueue();

        // Must never query for "printing", "done", etc.
        verify(repo, times(1)).findAllByStatusOrderByQueuePositionAsc("waiting");
        verify(repo, never()).findAllByStatusOrderByQueuePositionAsc(argThat(s -> !s.equals("waiting")));
    }

    // ============================================================
    // reorderQueue Tests
    // ============================================================

    @Test
    void reorderQueue_ShouldAssignPositionsStartingAtOne() {
        List<Long> orderedIds = Arrays.asList(3L, 1L, 2L);

        queueService.reorderQueue(orderedIds);

        verify(repo, times(1)).updateQueuePosition(3L, 1);
        verify(repo, times(1)).updateQueuePosition(1L, 2);
        verify(repo, times(1)).updateQueuePosition(2L, 3);
    }

    @Test
    void reorderQueue_ShouldCallRepositoryOncePerItem() {
        List<Long> orderedIds = Arrays.asList(10L, 20L, 30L, 40L);

        queueService.reorderQueue(orderedIds);

        verify(repo, times(4)).updateQueuePosition(anyLong(), anyInt());
    }

    @Test
    void reorderQueue_WithSingleItem_ShouldAssignPositionOne() {
        queueService.reorderQueue(Collections.singletonList(5L));

        verify(repo, times(1)).updateQueuePosition(5L, 1);
    }

    @Test
    void reorderQueue_WithEmptyList_ShouldNotCallRepository() {
        queueService.reorderQueue(Collections.emptyList());

        verify(repo, never()).updateQueuePosition(anyLong(), anyInt());
    }

    // ============================================================
    // abort Tests
    // ============================================================

    @Test
    void abort_WhenFileIsWaiting_ShouldSetAbortedAndReturnTrue() {
        when(repo.findById(1L)).thenReturn(Optional.of(waitingFile1));
        when(repo.save(any(GcodeFile.class))).thenReturn(waitingFile1);

        boolean result = queueService.abort(1L);

        assertTrue(result);
        assertEquals("aborted", waitingFile1.getStatus());
    }

    @Test
    void abort_WhenFileIsWaiting_ShouldClearQueuePositionAndPrinterAndModel() {
        when(repo.findById(1L)).thenReturn(Optional.of(waitingFile1));
        when(repo.save(any(GcodeFile.class))).thenReturn(waitingFile1);

        queueService.abort(1L);

        assertNull(waitingFile1.getQueuePosition());
        assertNull(waitingFile1.getPrinter());
        assertNull(waitingFile1.getModel());
    }

    @Test
    void abort_WhenFileIsPrinting_ShouldSetAbortedAndReturnTrue() {
        waitingFile1.setStatus("printing");
        when(repo.findById(1L)).thenReturn(Optional.of(waitingFile1));
        when(repo.save(any(GcodeFile.class))).thenReturn(waitingFile1);

        boolean result = queueService.abort(1L);

        assertTrue(result);
        assertEquals("aborted", waitingFile1.getStatus());
    }

    @Test
    void abort_WhenFileIsAlreadyCompleted_ShouldReturnFalse() {
        waitingFile1.setStatus("completed");
        when(repo.findById(1L)).thenReturn(Optional.of(waitingFile1));

        boolean result = queueService.abort(1L);

        assertFalse(result);
        verify(repo, never()).save(any());
    }

    @Test
    void abort_WhenFileIsAlreadyAborted_ShouldReturnFalse() {
        waitingFile1.setStatus("aborted");
        when(repo.findById(1L)).thenReturn(Optional.of(waitingFile1));

        boolean result = queueService.abort(1L);

        assertFalse(result);
        verify(repo, never()).save(any());
    }

    @Test
    void abort_WhenFileIsDone_ShouldReturnFalse() {
        waitingFile1.setStatus("done");
        when(repo.findById(1L)).thenReturn(Optional.of(waitingFile1));

        boolean result = queueService.abort(1L);

        assertFalse(result);
        verify(repo, never()).save(any());
    }

    @Test
    void abort_WhenFileIsFailed_ShouldReturnFalse() {
        waitingFile1.setStatus("failed");
        when(repo.findById(1L)).thenReturn(Optional.of(waitingFile1));

        boolean result = queueService.abort(1L);

        assertFalse(result);
        verify(repo, never()).save(any());
    }

    @Test
    void abort_WhenFileNotFound_ShouldReturnFalse() {
        when(repo.findById(999L)).thenReturn(Optional.empty());

        boolean result = queueService.abort(999L);

        assertFalse(result);
        verify(repo, never()).save(any());
    }

    @Test
    void abort_WhenFileIsWaiting_ShouldSaveOnce() {
        when(repo.findById(1L)).thenReturn(Optional.of(waitingFile1));
        when(repo.save(any(GcodeFile.class))).thenReturn(waitingFile1);

        queueService.abort(1L);

        verify(repo, times(1)).save(waitingFile1);
    }
}
