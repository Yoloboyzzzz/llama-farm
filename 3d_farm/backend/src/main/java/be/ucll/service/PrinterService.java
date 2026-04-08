package be.ucll.service;

import be.ucll.model.Printer;
import be.ucll.repository.PrinterRepository;
import be.ucll.service.PrinterErrorService;
import be.ucll.util.OctoPrintClient;
import be.ucll.util.PrusaLinkClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.NoSuchElementException;

import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

import be.ucll.dto.PrinterDTO;
import be.ucll.dto.GcodeFileDTO;
import be.ucll.model.GcodeFile;
import be.ucll.repository.GcodeFileRepository;
import be.ucll.repository.PrinterRepository;
import java.util.List;

import com.fasterxml.jackson.annotation.ObjectIdGenerators;

@Service
public class PrinterService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final OctoPrintClient octoClient;
    private final PrusaLinkClient prusaClient;

    private final PrinterErrorService printerErrorService;

    public PrinterService(OctoPrintClient octoClient, GcodeFileRepository gcodeFileRepository, PrinterRepository printerRepository, PrusaLinkClient prusaClient, PrinterErrorService printerErrorService) {
        this.octoClient = octoClient;
        this.prusaClient = prusaClient;
        this.printerRepository = printerRepository;
        this.gcodeFileRepository = gcodeFileRepository;
        this.printerErrorService = printerErrorService;
    }
    @Autowired
    private PrinterRepository printerRepository;
    private GcodeFileRepository gcodeFileRepository;

    public List<Printer> getAllPrinters() {
        return printerRepository.findAll();
    }

    public Printer getPrinter(Long id) {
        return printerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Printer not found: " + id));
    }

    public Printer addPrinter(Printer printer) {
        return printerRepository.save(printer);
    }

    public Printer updatePrinter(Long id, Printer updatedPrinter) {
        System.out.println("Updating printer " + id + " -> new weight: " + updatedPrinter.getWeightOfCurrentPrint());

        Printer existing = printerRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Printer not found"));
        
        existing.setName(updatedPrinter.getName());
        existing.setIp(updatedPrinter.getIp());
        existing.setApiKey(updatedPrinter.getApiKey());
        existing.setModel(updatedPrinter.getModel());
        existing.setMaterial(updatedPrinter.getMaterial());
        existing.setColor(updatedPrinter.getColor());
        existing.setFilamentOnSpool(updatedPrinter.getFilamentOnSpool());
        existing.setWeightOfCurrentPrint(updatedPrinter.getWeightOfCurrentPrint());
        existing.setStatus(updatedPrinter.getStatus());
        existing.setConnectionType(updatedPrinter.getConnectionType());
        
        return printerRepository.save(existing);
    }
    public Printer setInUse(Long id, boolean inUse) {
        Printer printer = printerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Printer not found: " + id));
        printer.setInUse(inUse);
        return printerRepository.save(printer);
    }

    public boolean abortPrint(String printerName) {

        Printer printer = printerRepository.findByName(printerName)
                .orElseThrow(() -> new RuntimeException("Printer not found: " + printerName));

        return octoClient.abortPrint(printer);
    }


    public Printer updateStatus(String name, String status, Boolean success) {
        Printer printer = printerRepository.findByName(name)
                .orElseThrow(() -> new RuntimeException("Printer not found: " + name));
        printer.setStatus(status);

        if ("idle".equals(status) && success != null) {
            if (success) printer.setSuccessCount(printer.getSuccessCount() + 1);
            else printer.setFailCount(printer.getFailCount() + 1);
        }

        return printerRepository.save(printer);
    }

    public void deletePrinter(Long id) {
        printerRepository.deleteById(id);
    }


    public boolean setPrinterOnline(String printerName) {
        Printer printer = printerRepository.findByName(printerName)
                .orElseThrow(() -> new RuntimeException("Printer not found: " + printerName));

        String url = printer.getIp() + "/api/connection";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Api-Key", printer.getApiKey());

        // Body for OctoPrint connection command
        String body = """
        {
          "command": "connect",
          "autoconnect": true
        }
        """;

        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            System.err.println("Error connecting printer: " + e.getMessage());
            return false;
        }
    }


    
    @Transactional
    public void setPrinterIdle(Long id) {
        Printer printer = printerRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Printer not found: " + id));

        printer.setFilamentOnSpool((int)(printer.getFilamentOnSpool() - printer.getWeightOfCurrentPrint()));
        printer.setWeightOfCurrentPrint(0.0);
        printer.setStatus("idle");
        printer.setSuccessCount(printer.getSuccessCount() + 1);
        GcodeFile file = printer.getCurrentFile();
        file.setStatus("completed");
        printer.setCurrentFile(null);
        printerRepository.save(printer);
    }

    @Transactional
    public void reportPrintFail(String name, String reason) {
        Printer printer = printerRepository.findByName(name)
                .orElseThrow(() -> new NoSuchElementException("Printer not found: " + name));

        GcodeFile file = printer.getCurrentFile();
        if (file != null) {
            file.setStatus("failed");
            gcodeFileRepository.save(file);
        }

        printer.setWeightOfCurrentPrint(0.0);
        printer.setStatus("idle");
        printer.setFailCount(printer.getFailCount() + 1);
        printer.setCurrentFile(null);
        printerRepository.save(printer);

        printerErrorService.logError(printer, reason);

        System.out.println("❌ Print failed on " + name + ": " + reason +
                (file != null ? " (file: " + file.getName() + ")" : ""));
    }

    public List<PrinterDTO> getCompatiblePrinters(Long gcodeId) {
        GcodeFile file = gcodeFileRepository.findById(gcodeId)
                .orElseThrow(() -> new RuntimeException("Gcode file not found: " + gcodeId));
        return printerRepository.findAll().stream()
                .filter(p -> p.getStatus().equalsIgnoreCase("idle"))
                .filter(p -> p.getWeightOfCurrentPrint() == 0)
                .filter(p -> p.getModel().equalsIgnoreCase(file.getModel()))
                .filter(p -> p.getMaterial().equalsIgnoreCase(file.getMaterial()))
                .filter(p -> p.getColor().equalsIgnoreCase(file.getColor()))
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public boolean startNow(Long gcodeId, Long printerId) {

        GcodeFile file = gcodeFileRepository.findById(gcodeId)
                .orElseThrow(() -> new RuntimeException("Gcode file not found: " + gcodeId));

        Printer chosen;
        if (printerId != null) {
            chosen = printerRepository.findById(printerId)
                    .orElseThrow(() -> new RuntimeException("Printer not found: " + printerId));
            if (!chosen.getStatus().equalsIgnoreCase("idle")
                    || !chosen.getModel().equalsIgnoreCase(file.getModel())
                    || !chosen.getMaterial().equalsIgnoreCase(file.getMaterial())
                    || !chosen.getColor().equalsIgnoreCase(file.getColor())) {
                throw new RuntimeException("Chosen printer is not compatible or not available.");
            }
        } else {
            // Find suitable printers
            List<Printer> suitable = printerRepository.findAll().stream()
                    .filter(p -> p.getStatus().equalsIgnoreCase("idle"))
                    .filter(p -> p.getWeightOfCurrentPrint() == 0)
                    .filter(p -> p.getModel().equalsIgnoreCase(file.getModel()))
                    .filter(p -> p.getMaterial().equalsIgnoreCase(file.getMaterial()))
                    .filter(p -> p.getColor().equalsIgnoreCase(file.getColor()))
                    .toList();

            if (suitable.isEmpty()) {
                throw new RuntimeException("No suitable printer available right now.");
            }

            chosen = suitable.get((int) (Math.random() * suitable.size()));
        }

        // Mark as "sending" before upload so the scheduler and UI know it's in progress
        file.setStatus("sending");
        gcodeFileRepository.save(file);

        // Upload + start print
        boolean ok;
        if ("octoprint".equalsIgnoreCase(chosen.getConnectionType())) {
            ok = octoClient.uploadToOctoPrintAndPrint(chosen, file);
        } else if ("prusalink".equalsIgnoreCase(chosen.getConnectionType())) {
            ok = prusaClient.uploadToPrusaLinkAndPrint(chosen, file);
        } else {
            ok = false;
        }

        if (!ok) {
            // Revert to waiting so it can be retried
            file.setStatus("waiting");
            gcodeFileRepository.save(file);
            return false;
        }

        // Update DB state
        file.setStatus("printing");
        file.setStartedAt(java.time.LocalDateTime.now());
        file.setQueuePosition(null);
        file.setPrinter(chosen);
        gcodeFileRepository.save(file);

        chosen.setStatus("printing");
        chosen.setWeightOfCurrentPrint((double) file.getWeight());
        chosen.setCurrentFile(file);
        printerRepository.save(chosen);

        return true;
    }

    public List<PrinterDTO> getAllPrinters2() {
        return printerRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private PrinterDTO convertToDTO(Printer printer) {
        PrinterDTO dto = new PrinterDTO();
        dto.setId(printer.getId());
        dto.setName(printer.getName());
        dto.setModel(printer.getModel());
        dto.setStatus(printer.getStatus());
        dto.setColor(printer.getColor());
        dto.setMaterial(printer.getMaterial());
        dto.setFilamentOnSpool(printer.getFilamentOnSpool());
        dto.setEnoughFilament(printer.getEnoughFilament());
        dto.setWeightOfCurrentPrint(printer.getWeightOfCurrentPrint());
        dto.setSuccessCount(printer.getSuccessCount());
        dto.setFailCount(printer.getFailCount());

        // ✅ include current file if printing
        GcodeFile file = printer.getCurrentFile();
        if (file != null) {
            GcodeFileDTO fileDTO = new GcodeFileDTO();
            fileDTO.setId(file.getId());
            fileDTO.setFilename(file.getName());
            fileDTO.setStatus(file.getStatus());
            fileDTO.setDurationSeconds(file.getDuration());
            fileDTO.setStartedAt(file.getStartedAt());
            fileDTO.setRemainingTimeSeconds(file.getRemainingTimeSeconds());
            dto.setCurrentFile(fileDTO);
        }

        return dto;
    }

}
