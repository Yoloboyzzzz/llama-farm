package be.ucll.controller;

import be.ucll.model.Printer;
import be.ucll.repository.PrinterRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User-scoped endpoints — authenticated users only, no admin required.
 * These endpoints intentionally return minimal data: no filenames,
 * no job durations, no IP addresses, no API keys.
 */
@RestController
@RequestMapping("/api/user")
public class UserViewController {

    private final PrinterRepository printerRepository;

    public UserViewController(PrinterRepository printerRepository) {
        this.printerRepository = printerRepository;
    }

    /**
     * GET /api/user/printers/summary
     *
     * Returns only status counts — printing / idle / done / offline.
     * No printer names, no filenames, no job details.
     * Safe to expose to regular users.
     */
    @GetMapping("/printers/summary")
    @PreAuthorize("isAuthenticated()")  // any logged-in user, not just ADMIN
    public ResponseEntity<Map<String, Integer>> getPrinterSummary() {
        List<Printer> printers = printerRepository.findAll();

        int printing = 0, idle = 0, done = 0, offline = 0;

        for (Printer p : printers) {
            String status = p.getStatus() == null ? "offline" : p.getStatus().toLowerCase();
            switch (status) {
                case "printing"     -> printing++;
                case "idle",
                     "operational"  -> idle++;
                case "done",
                     "finished"     -> done++;
                default             -> offline++;
            }
        }

        Map<String, Integer> result = new HashMap<>();
        result.put("printing", printing);
        result.put("idle",     idle);
        result.put("done",     done);
        result.put("offline",  offline);

        return ResponseEntity.ok(result);
    }
}