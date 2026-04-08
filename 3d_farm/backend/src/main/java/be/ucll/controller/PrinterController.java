package be.ucll.controller;

import be.ucll.model.Printer;
import be.ucll.service.PrinterService;
import be.ucll.dto.PrinterDTO;
import be.ucll.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/printers")
public class PrinterController {

    private final PrinterService printerService;

    public PrinterController(PrinterService printerService) {
        this.printerService = printerService;
    }





    @GetMapping("/status")
    public List<PrinterDTO> getAllPrinters() {
        return printerService.getAllPrinters2();
    }


    @GetMapping
    public List<Object> getPrinters(@RequestHeader(value = "Role", required = false) String role) {
        List<Printer> allPrinters = printerService.getAllPrinters(); // now a List
        List<Object> response = new ArrayList<>();

        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);

        for (Printer p : allPrinters) {
            if (isAdmin) {
                // Admins get full printer details
                response.add(p);
            } else {
                // Normal users get only safe/public fields
                Map<String, Object> publicData = Map.of(
                    "model", p.getModel(),
                    "material", p.getMaterial(),
                    "color", p.getColor(),
                    "filament_on_spool", p.getFilamentOnSpool(),
                    "weight_of_current_print", p.getWeightOfCurrentPrint(),
                    "status", p.getStatus()
                );
                response.add(publicData);
            }
        }

        return response;
    }

    @PostMapping
    public Printer addPrinter(@RequestBody Printer printer) {
        return printerService.addPrinter(printer);
    }
    @PutMapping("/{id}")
    public Printer updatePrinter(@PathVariable Long id, @RequestBody Printer updatedPrinter) {
        return printerService.updatePrinter(id, updatedPrinter);
    }
    @DeleteMapping("/{id}")
    public void deletePrinter(@PathVariable Long id) {
        printerService.deletePrinter(id);
    }

    @PostMapping("/{name}/report-fail")
    public ResponseEntity<String> reportPrintFail(
            @PathVariable String name,
            @RequestParam(defaultValue = "other") String reason) {
        try {
            printerService.reportPrintFail(name, reason);
            return ResponseEntity.ok("Print failure recorded for " + name);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Printer not found");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to record print failure: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/set-idle")
    public ResponseEntity<String> setPrinterIdle(@PathVariable Long id) {
        try {
            printerService.setPrinterIdle(id);
            return ResponseEntity.ok("Printer set to idle and weight reset to 0");
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Printer not found");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to set printer idle: " + e.getMessage());
        }
    }


    @PostMapping("/{printerName}/set-online")
    public ResponseEntity<String> setPrinterOnline(@PathVariable String printerName) {
        try {
            boolean success = printerService.setPrinterOnline(printerName);
            if (success) {
                return ResponseEntity.ok("Printer " + printerName + " is now online.");
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Failed to set printer online. Check printer configuration or OctoPrint API response.");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error setting printer online: " + e.getMessage());
        }
    }

    @PutMapping("/{id}/in-use")
    public ResponseEntity<?> setInUse(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        try {
            boolean inUse = Boolean.TRUE.equals(body.get("inUse"));
            return ResponseEntity.ok(printerService.setInUse(id, inUse));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PostMapping("/{name}/abort")
    public ResponseEntity<?> abortPrint(@PathVariable String name) {

        boolean ok = printerService.abortPrint(name);

        if (ok) {
            return ResponseEntity.ok("Print aborted on " + name);
        } else {
            return ResponseEntity.status(500).body("Failed to abort print on " + name);
        }
    }

    @GetMapping("/compatible/{gcodeId}")
    public ResponseEntity<?> getCompatiblePrinters(@PathVariable Long gcodeId) {
        try {
            return ResponseEntity.ok(printerService.getCompatiblePrinters(gcodeId));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @PostMapping("/start-now/{gcodeId}")
    public ResponseEntity<?> startNow(
            @PathVariable Long gcodeId,
            @RequestParam(required = false) Long printerId) {
        try {
            boolean ok = printerService.startNow(gcodeId, printerId);

            if (ok) {
                return ResponseEntity.ok("Started print for GcodeFile " + gcodeId);
            } else {
                return ResponseEntity.status(500).body("Failed to start print");
            }

        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }


}
