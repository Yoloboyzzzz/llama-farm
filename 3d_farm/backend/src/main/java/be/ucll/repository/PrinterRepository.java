package be.ucll.repository;

import java.util.List;

import be.ucll.model.Printer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface PrinterRepository extends JpaRepository<Printer, Long> {
    Optional<Printer> findByName(String name);

    @Modifying
    @Transactional
    @Query("UPDATE Printer p SET p.status = :status WHERE p.id = :id")
    void updatePrinterStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * Optional convenience method: mark a printer offline by ID.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Printer p SET p.status = 'offline' WHERE p.id = :id")
    void markOffline(@Param("id") Long id);

    @Query("""
           SELECT p
           FROM Printer p
           WHERE p.status = 'idle'
             AND (p.weightOfCurrentPrint IS NULL OR p.weightOfCurrentPrint = 0)
           """)
    List<Printer> findIdlePrinters();

    List<Printer> findByEnabledTrue();
}
