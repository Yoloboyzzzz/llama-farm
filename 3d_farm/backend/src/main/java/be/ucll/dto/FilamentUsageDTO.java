package be.ucll.dto;
import java.time.LocalDateTime;

public class FilamentUsageDTO {
    public LocalDateTime completedAt;
    public int filamentUsedGrams;

    public FilamentUsageDTO(LocalDateTime completedAt, int filamentUsedGrams) {
        this.completedAt = completedAt;
        this.filamentUsedGrams = filamentUsedGrams;
    }
}
