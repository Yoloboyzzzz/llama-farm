package be.ucll.repository;

import be.ucll.model.PrinterProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import be.ucll.model.Job;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

@Repository
public interface PrinterProfileRepository extends JpaRepository<PrinterProfile, Long> {

    Optional<PrinterProfile> findByPrinterModelAndMaterial(String printerModel, String material);

}
