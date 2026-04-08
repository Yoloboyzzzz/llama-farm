package be.ucll.service;

import be.ucll.dto.GcodeFileDTO;
import be.ucll.dto.PrinterDTO;
import be.ucll.model.GcodeFile;
import be.ucll.model.Printer;
import be.ucll.repository.GcodeFileRepository;
import be.ucll.repository.PrinterRepository;
import be.ucll.util.OctoPrintClient;
import be.ucll.util.PrusaLinkClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrinterServiceTest {

    @Mock
    private PrinterRepository printerRepository;

    @Mock
    private GcodeFileRepository gcodeFileRepository;

    @Mock
    private OctoPrintClient octoClient;

    @Mock
    private PrusaLinkClient prusaClient;

    @InjectMocks
    private PrinterService printerService;

    private Printer testPrinter;
    private GcodeFile testGcodeFile;

    @BeforeEach
    void setUp() {
        testPrinter = new Printer();
        testPrinter.setId(1L);
        testPrinter.setName("Prusa MK4 #1");
        testPrinter.setIp("http://192.168.1.100");
        testPrinter.setApiKey("test-api-key");
        testPrinter.setModel("Prusa MK4");
        testPrinter.setMaterial("PLA");
        testPrinter.setColor("Red");
        testPrinter.setFilamentOnSpool(500);
        testPrinter.setWeightOfCurrentPrint(0.0);
        testPrinter.setStatus("idle");
        testPrinter.setSuccessCount(10);
        testPrinter.setFailCount(2);
        testPrinter.setConnectionType("octoprint");

        testGcodeFile = new GcodeFile();
        testGcodeFile.setId(1L);
        testGcodeFile.setName("test_print.gcode");
        testGcodeFile.setModel("Prusa MK4");
        testGcodeFile.setMaterial("PLA");
        testGcodeFile.setColor("Red");
        testGcodeFile.setWeight(50);
        testGcodeFile.setDuration(3600);
        testGcodeFile.setStatus("waiting");
        testGcodeFile.setQueuePostion(1);
    }

    // ============================================================
    // getAllPrinters Tests
    // ============================================================

    @Test
    void getAllPrinters_ShouldReturnAllPrinters() {
        List<Printer> printers = Arrays.asList(testPrinter, createSecondPrinter());
        when(printerRepository.findAll()).thenReturn(printers);

        List<Printer> result = printerService.getAllPrinters();

        assertEquals(2, result.size());
        verify(printerRepository, times(1)).findAll();
    }

    @Test
    void getAllPrinters_WhenEmpty_ShouldReturnEmptyList() {
        when(printerRepository.findAll()).thenReturn(Collections.emptyList());

        List<Printer> result = printerService.getAllPrinters();

        assertTrue(result.isEmpty());
        verify(printerRepository, times(1)).findAll();
    }

    // ============================================================
    // getPrinter Tests
    // ============================================================

    @Test
    void getPrinter_WhenExists_ShouldReturnPrinter() {
        when(printerRepository.findById(1L)).thenReturn(Optional.of(testPrinter));

        Printer result = printerService.getPrinter(1L);

        assertNotNull(result);
        assertEquals("Prusa MK4 #1", result.getName());
    }

    @Test
    void getPrinter_WhenNotExists_ShouldThrowException() {
        when(printerRepository.findById(999L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> printerService.getPrinter(999L));
        assertTrue(ex.getMessage().contains("Printer not found"));
    }

    // ============================================================
    // addPrinter Tests
    // ============================================================

    @Test
    void addPrinter_ShouldSaveAndReturnPrinter() {
        when(printerRepository.save(any(Printer.class))).thenReturn(testPrinter);

        Printer result = printerService.addPrinter(testPrinter);

        assertNotNull(result);
        assertEquals("Prusa MK4 #1", result.getName());
        verify(printerRepository, times(1)).save(testPrinter);
    }

    // ============================================================
    // updatePrinter Tests
    // ============================================================

    @Test
    void updatePrinter_WhenExists_ShouldUpdateAllFields() {
        Printer updatedData = new Printer();
        updatedData.setName("Updated Name");
        updatedData.setIp("http://192.168.1.200");
        updatedData.setApiKey("new-api-key");
        updatedData.setModel("Prusa MK3S");
        updatedData.setMaterial("PETG");
        updatedData.setColor("Blue");
        updatedData.setFilamentOnSpool(750);
        updatedData.setWeightOfCurrentPrint(25.0);
        updatedData.setStatus("printing");

        when(printerRepository.findById(1L)).thenReturn(Optional.of(testPrinter));
        when(printerRepository.save(any(Printer.class))).thenReturn(testPrinter);

        printerService.updatePrinter(1L, updatedData);

        assertEquals("Updated Name", testPrinter.getName());
        assertEquals("http://192.168.1.200", testPrinter.getIp());
        assertEquals("new-api-key", testPrinter.getApiKey());
        assertEquals("Prusa MK3S", testPrinter.getModel());
        assertEquals("PETG", testPrinter.getMaterial());
        assertEquals("Blue", testPrinter.getColor());
        assertEquals(750, testPrinter.getFilamentOnSpool());
        assertEquals(25.0, testPrinter.getWeightOfCurrentPrint());
        assertEquals("printing", testPrinter.getStatus());
        verify(printerRepository, times(1)).save(testPrinter);
    }

    @Test
    void updatePrinter_WhenNotExists_ShouldThrowException() {
        when(printerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> printerService.updatePrinter(999L, testPrinter));

        verify(printerRepository, never()).save(any());
    }

    // ============================================================
    // updateStatus Tests
    // ============================================================

    @Test
    void updateStatus_ToPrinting_ShouldOnlyUpdateStatus() {
        when(printerRepository.findByName("Prusa MK4 #1")).thenReturn(Optional.of(testPrinter));
        when(printerRepository.save(any(Printer.class))).thenReturn(testPrinter);

        Printer result = printerService.updateStatus("Prusa MK4 #1", "printing", null);

        assertEquals("printing", result.getStatus());
        assertEquals(10, result.getSuccessCount());
        assertEquals(2, result.getFailCount());
    }

    @Test
    void updateStatus_ToIdleWithSuccess_ShouldIncrementSuccessCount() {
        when(printerRepository.findByName("Prusa MK4 #1")).thenReturn(Optional.of(testPrinter));
        when(printerRepository.save(any(Printer.class))).thenReturn(testPrinter);

        Printer result = printerService.updateStatus("Prusa MK4 #1", "idle", true);

        assertEquals("idle", result.getStatus());
        assertEquals(11, result.getSuccessCount());
        assertEquals(2, result.getFailCount());
    }

    @Test
    void updateStatus_ToIdleWithFailure_ShouldIncrementFailCount() {
        when(printerRepository.findByName("Prusa MK4 #1")).thenReturn(Optional.of(testPrinter));
        when(printerRepository.save(any(Printer.class))).thenReturn(testPrinter);

        Printer result = printerService.updateStatus("Prusa MK4 #1", "idle", false);

        assertEquals("idle", result.getStatus());
        assertEquals(10, result.getSuccessCount());
        assertEquals(3, result.getFailCount());
    }

    @Test
    void updateStatus_ToIdleWithNullSuccess_ShouldNotChangeCounters() {
        when(printerRepository.findByName("Prusa MK4 #1")).thenReturn(Optional.of(testPrinter));
        when(printerRepository.save(any(Printer.class))).thenReturn(testPrinter);

        printerService.updateStatus("Prusa MK4 #1", "idle", null);

        // null success flag means counters are untouched
        assertEquals(10, testPrinter.getSuccessCount());
        assertEquals(2, testPrinter.getFailCount());
    }

    @Test
    void updateStatus_WhenPrinterNotFound_ShouldThrowException() {
        when(printerRepository.findByName("NonExistent")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> printerService.updateStatus("NonExistent", "idle", true));
    }

    // ============================================================
    // deletePrinter Tests
    // ============================================================

    @Test
    void deletePrinter_ShouldCallRepositoryDeleteById() {
        doNothing().when(printerRepository).deleteById(1L);

        printerService.deletePrinter(1L);

        verify(printerRepository, times(1)).deleteById(1L);
    }

    // ============================================================
    // abortPrint Tests
    // ============================================================

    @Test
    void abortPrint_WhenClientSucceeds_ShouldReturnTrue() {
        when(printerRepository.findByName("Prusa MK4 #1")).thenReturn(Optional.of(testPrinter));
        when(octoClient.abortPrint(testPrinter)).thenReturn(true);

        boolean result = printerService.abortPrint("Prusa MK4 #1");

        assertTrue(result);
        verify(octoClient, times(1)).abortPrint(testPrinter);
    }

    @Test
    void abortPrint_WhenClientFails_ShouldReturnFalse() {
        when(printerRepository.findByName("Prusa MK4 #1")).thenReturn(Optional.of(testPrinter));
        when(octoClient.abortPrint(testPrinter)).thenReturn(false);

        boolean result = printerService.abortPrint("Prusa MK4 #1");

        assertFalse(result);
    }

    @Test
    void abortPrint_WhenPrinterNotFound_ShouldThrowException() {
        when(printerRepository.findByName("NonExistent")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> printerService.abortPrint("NonExistent"));
        verify(octoClient, never()).abortPrint(any());
    }

    // ============================================================
    // setPrinterIdle Tests
    // ============================================================

    @Test
    void setPrinterIdle_ShouldDeductFilamentAndCompleteCurrentFile() {
        testPrinter.setStatus("printing");
        testPrinter.setFilamentOnSpool(500);
        testPrinter.setWeightOfCurrentPrint(50.0);
        testPrinter.setCurrentFile(testGcodeFile);
        testGcodeFile.setStatus("printing");

        when(printerRepository.findById(1L)).thenReturn(Optional.of(testPrinter));
        when(printerRepository.save(any(Printer.class))).thenReturn(testPrinter);

        printerService.setPrinterIdle(1L);

        assertEquals(450, testPrinter.getFilamentOnSpool()); // 500 - 50
        assertEquals(0.0, testPrinter.getWeightOfCurrentPrint());
        assertEquals("idle", testPrinter.getStatus());
        assertNull(testPrinter.getCurrentFile());
        assertEquals("completed", testGcodeFile.getStatus());
        verify(printerRepository, times(1)).save(testPrinter);
    }

    @Test
    void setPrinterIdle_WhenPrinterNotFound_ShouldThrowNoSuchElementException() {
        when(printerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> printerService.setPrinterIdle(99L));
        verify(printerRepository, never()).save(any());
    }

    // ============================================================
    // startNow Tests
    // ============================================================

    @Test
    void startNow_WhenSuitablePrinterExists_ShouldUpdateFileAndPrinterState() {
        when(gcodeFileRepository.findById(1L)).thenReturn(Optional.of(testGcodeFile));
        when(printerRepository.findAll()).thenReturn(Arrays.asList(testPrinter));
        when(octoClient.uploadToOctoPrintAndPrint(testPrinter, testGcodeFile)).thenReturn(true);
        when(gcodeFileRepository.save(any(GcodeFile.class))).thenReturn(testGcodeFile);
        when(printerRepository.save(any(Printer.class))).thenReturn(testPrinter);

        boolean result = printerService.startNow(1L, null);

        assertTrue(result);
        assertEquals("printing", testGcodeFile.getStatus());
        assertNotNull(testGcodeFile.getStartedAt());
        assertNull(testGcodeFile.getQueuePosition());
        assertEquals("printing", testPrinter.getStatus());
        assertEquals(50.0, testPrinter.getWeightOfCurrentPrint());
        assertEquals(testGcodeFile, testPrinter.getCurrentFile());
        verify(octoClient, times(1)).uploadToOctoPrintAndPrint(testPrinter, testGcodeFile);
    }

    @Test
    void startNow_WhenNoPrinterIsIdle_ShouldThrowException() {
        testPrinter.setStatus("printing");

        when(gcodeFileRepository.findById(1L)).thenReturn(Optional.of(testGcodeFile));
        when(printerRepository.findAll()).thenReturn(Arrays.asList(testPrinter));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> printerService.startNow(1L, null));
        assertTrue(ex.getMessage().contains("No suitable printer available"));
        verify(octoClient, never()).uploadToOctoPrintAndPrint(any(), any());
    }

    @Test
    void startNow_WhenMaterialMismatch_ShouldThrowException() {
        testPrinter.setMaterial("PETG");
        testGcodeFile.setMaterial("PLA");

        when(gcodeFileRepository.findById(1L)).thenReturn(Optional.of(testGcodeFile));
        when(printerRepository.findAll()).thenReturn(Arrays.asList(testPrinter));

        assertThrows(RuntimeException.class, () -> printerService.startNow(1L, null));
    }

    @Test
    void startNow_WhenColorMismatch_ShouldThrowException() {
        testPrinter.setColor("Blue");
        testGcodeFile.setColor("Red");

        when(gcodeFileRepository.findById(1L)).thenReturn(Optional.of(testGcodeFile));
        when(printerRepository.findAll()).thenReturn(Arrays.asList(testPrinter));

        assertThrows(RuntimeException.class, () -> printerService.startNow(1L, null));
    }

    @Test
    void startNow_WhenModelMismatch_ShouldThrowException() {
        testPrinter.setModel("Prusa Mini");
        testGcodeFile.setModel("Prusa MK4");

        when(gcodeFileRepository.findById(1L)).thenReturn(Optional.of(testGcodeFile));
        when(printerRepository.findAll()).thenReturn(Arrays.asList(testPrinter));

        assertThrows(RuntimeException.class, () -> printerService.startNow(1L, null));
    }

    @Test
    void startNow_WhenPrinterStillHasWeight_ShouldThrowException() {
        // Printer shows weight > 0, meaning it hasn't finished its current print
        testPrinter.setStatus("idle");
        testPrinter.setWeightOfCurrentPrint(15.0);

        when(gcodeFileRepository.findById(1L)).thenReturn(Optional.of(testGcodeFile));
        when(printerRepository.findAll()).thenReturn(Arrays.asList(testPrinter));

        assertThrows(RuntimeException.class, () -> printerService.startNow(1L, null));
    }

    @Test
    void startNow_WhenGcodeFileNotFound_ShouldThrowException() {
        when(gcodeFileRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> printerService.startNow(999L, null));
        verify(printerRepository, never()).findAll();
    }

    @Test
    void startNow_WithPrusaLinkPrinter_ShouldUsePrusaClient() {
        testPrinter.setConnectionType("prusalink");

        when(gcodeFileRepository.findById(1L)).thenReturn(Optional.of(testGcodeFile));
        when(printerRepository.findAll()).thenReturn(Arrays.asList(testPrinter));
        when(prusaClient.uploadToPrusaLinkAndPrint(testPrinter, testGcodeFile)).thenReturn(true);
        when(gcodeFileRepository.save(any(GcodeFile.class))).thenReturn(testGcodeFile);
        when(printerRepository.save(any(Printer.class))).thenReturn(testPrinter);

        boolean result = printerService.startNow(1L, null);

        assertTrue(result);
        verify(prusaClient, times(1)).uploadToPrusaLinkAndPrint(testPrinter, testGcodeFile);
        verify(octoClient, never()).uploadToOctoPrintAndPrint(any(), any());
    }

    @Test
    void startNow_WithMultipleSuitablePrinters_ShouldPrintOnExactlyOne() {
        Printer printer2 = createSecondPrinter();

        when(gcodeFileRepository.findById(1L)).thenReturn(Optional.of(testGcodeFile));
        when(printerRepository.findAll()).thenReturn(Arrays.asList(testPrinter, printer2));
        when(octoClient.uploadToOctoPrintAndPrint(any(Printer.class), eq(testGcodeFile))).thenReturn(true);
        when(gcodeFileRepository.save(any(GcodeFile.class))).thenReturn(testGcodeFile);
        when(printerRepository.save(any(Printer.class))).thenAnswer(i -> i.getArguments()[0]);

        boolean result = printerService.startNow(1L, null);

        assertTrue(result);
        // Only one printer should have been chosen – the client is called exactly once
        verify(octoClient, times(1)).uploadToOctoPrintAndPrint(any(Printer.class), eq(testGcodeFile));
    }

    // ============================================================
    // getAllPrinters2 (DTO conversion) Tests
    // ============================================================

    @Test
    void getAllPrinters2_ShouldMapAllFieldsToDTO() {
        when(printerRepository.findAll()).thenReturn(Arrays.asList(testPrinter));

        List<PrinterDTO> result = printerService.getAllPrinters2();

        assertEquals(1, result.size());
        PrinterDTO dto = result.get(0);
        assertEquals(testPrinter.getId(), dto.getId());
        assertEquals(testPrinter.getName(), dto.getName());
        assertEquals(testPrinter.getModel(), dto.getModel());
        assertEquals(testPrinter.getStatus(), dto.getStatus());
        assertEquals(testPrinter.getColor(), dto.getColor());
        assertEquals(testPrinter.getMaterial(), dto.getMaterial());
        assertEquals(testPrinter.getFilamentOnSpool(), dto.getFilamentOnSpool());
        assertEquals(testPrinter.getWeightOfCurrentPrint(), dto.getWeightOfCurrentPrint());
    }

    @Test
    void getAllPrinters2_WhenPrinterHasCurrentFile_ShouldIncludeFileDTOWithAllFields() {
        testGcodeFile.setStartedAt(LocalDateTime.now());
        testPrinter.setCurrentFile(testGcodeFile);
        testPrinter.setStatus("printing");

        when(printerRepository.findAll()).thenReturn(Arrays.asList(testPrinter));

        List<PrinterDTO> result = printerService.getAllPrinters2();

        assertNotNull(result.get(0).getCurrentFile());
        GcodeFileDTO fileDTO = result.get(0).getCurrentFile();
        assertEquals(testGcodeFile.getId(), fileDTO.getId());
        assertEquals(testGcodeFile.getName(), fileDTO.getFilename());
        assertEquals(testGcodeFile.getStatus(), fileDTO.getStatus());
        assertEquals(testGcodeFile.getDuration(), fileDTO.getDurationSeconds());
        assertEquals(testGcodeFile.getStartedAt(), fileDTO.getStartedAt());
    }

    @Test
    void getAllPrinters2_WhenPrinterHasNoCurrentFile_ShouldHaveNullFileDTO() {
        testPrinter.setCurrentFile(null);
        when(printerRepository.findAll()).thenReturn(Arrays.asList(testPrinter));

        List<PrinterDTO> result = printerService.getAllPrinters2();

        assertNull(result.get(0).getCurrentFile());
    }

    @Test
    void getAllPrinters2_WhenNoPrinters_ShouldReturnEmptyList() {
        when(printerRepository.findAll()).thenReturn(Collections.emptyList());

        assertTrue(printerService.getAllPrinters2().isEmpty());
    }

    // ============================================================
    // Helper Methods
    // ============================================================

    private Printer createSecondPrinter() {
        Printer printer = new Printer();
        printer.setId(2L);
        printer.setName("Prusa MK4 #2");
        printer.setIp("http://192.168.1.101");
        printer.setApiKey("test-api-key-2");
        printer.setModel("Prusa MK4");
        printer.setMaterial("PLA");
        printer.setColor("Red");
        printer.setFilamentOnSpool(600);
        printer.setWeightOfCurrentPrint(0.0);
        printer.setStatus("idle");
        printer.setConnectionType("octoprint");
        return printer;
    }
}
