package be.ucll.service;

import be.ucll.model.Printer;
import be.ucll.model.PrinterError;
import be.ucll.repository.PrinterErrorRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class PrinterErrorService {

    private final PrinterErrorRepository printerErrorRepository;

    public PrinterErrorService(PrinterErrorRepository printerErrorRepository) {
        this.printerErrorRepository = printerErrorRepository;
    }

    public void logError(Printer printer, String message) {
        PrinterError error = new PrinterError(printer, LocalDateTime.now(), message);
        printerErrorRepository.save(error);
        System.out.println("🚨 Error logged for " + printer.getName() + ": " + message);
    }
}
