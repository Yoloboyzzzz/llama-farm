package be.ucll.service;

import be.ucll.model.Printer;
import be.ucll.dto.FilamentUsageDTO;
import be.ucll.model.GcodeFile;
import be.ucll.repository.PrinterRepository;
import be.ucll.repository.GcodeFileRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MaterialService {

    private final PrinterRepository printerRepository;
    private final GcodeFileRepository gcodeFileRepository;

    public MaterialService(PrinterRepository printerRepository, GcodeFileRepository gcodeFileRepository) {
        this.printerRepository = printerRepository;
        this.gcodeFileRepository = gcodeFileRepository;
    }

    public Map<String, List<String>> getMaterialColorMap() {
        List<Printer> printers = printerRepository.findAll();

        Map<String, Set<String>> map = new HashMap<>();

        for (Printer printer : printers) {
            String material = printer.getMaterial();
            String color = printer.getColor();

            // skip invalid entries
            if (material == null || color == null) continue;

            map.putIfAbsent(material, new HashSet<>());
            map.get(material).add(color);
        }

        // Convert Set<String> → sorted List<String>
        Map<String, List<String>> result = new HashMap<>();
        for (var entry : map.entrySet()) {
            List<String> sortedColors = new ArrayList<>(entry.getValue());
            sortedColors.sort(String::compareToIgnoreCase); // <- ✨ sort alphabetically
            result.put(entry.getKey(), sortedColors);
        }

        return result;
    }

    public List<FilamentUsageDTO> getCompletedUsage() {
    List<GcodeFile> files = gcodeFileRepository.findByStatus("completed");

    return files.stream()
            .map(f -> new FilamentUsageDTO(
                f.getStartedAt(),
                f.getWeight()
            ))
            .toList();
}
}

