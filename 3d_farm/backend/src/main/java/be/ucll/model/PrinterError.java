package be.ucll.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "printer_errors")
public class PrinterError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "printer_id", nullable = false)
    private Printer printer;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(columnDefinition = "TEXT")
    private String message;

    public PrinterError() {}

    public PrinterError(Printer printer, LocalDateTime occurredAt, String message) {
        this.printer = printer;
        this.occurredAt = occurredAt;
        this.message = message;
    }

    public Long getId() { return id; }
    public Printer getPrinter() { return printer; }
    public void setPrinter(Printer printer) { this.printer = printer; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
