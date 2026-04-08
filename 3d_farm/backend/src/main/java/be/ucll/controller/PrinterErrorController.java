package be.ucll.controller;

import be.ucll.model.PrinterError;
import be.ucll.repository.PrinterErrorRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/errors")
public class PrinterErrorController {

    private final PrinterErrorRepository printerErrorRepository;

    public PrinterErrorController(PrinterErrorRepository printerErrorRepository) {
        this.printerErrorRepository = printerErrorRepository;
    }

    @GetMapping
    public List<Map<String, Object>> getAllErrors() {
        return printerErrorRepository.findAllByOrderByOccurredAtDesc()
                .stream()
                .map(this::toMap)
                .collect(Collectors.toList());
    }

    @GetMapping("/printer/{printerId}")
    public List<Map<String, Object>> getErrorsByPrinter(@PathVariable Long printerId) {
        return printerErrorRepository.findByPrinterIdOrderByOccurredAtDesc(printerId)
                .stream()
                .map(this::toMap)
                .collect(Collectors.toList());
    }

    private Map<String, Object> toMap(PrinterError e) {
        return Map.of(
                "id", e.getId(),
                "printerId", e.getPrinter().getId(),
                "printerName", e.getPrinter().getName(),
                "occurredAt", e.getOccurredAt(),
                "message", e.getMessage() != null ? e.getMessage() : ""
        );
    }
}
