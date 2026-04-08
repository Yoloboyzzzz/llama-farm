package be.ucll.repository;

import be.ucll.model.PrinterError;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PrinterErrorRepository extends JpaRepository<PrinterError, Long> {
    List<PrinterError> findByPrinterIdOrderByOccurredAtDesc(Long printerId);
    List<PrinterError> findAllByOrderByOccurredAtDesc();
}
