package be.ucll.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;

@Entity
@Table(name = "printers")
public class Printer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    private String ip;
    private String apiKey;
    private String model;
    private String material;
    private String color;
    
    @Column(name = "connection_type")
    private String connectionType;  // e.g., "octoprint", "prusalink", "klipper", "direct"

    private Integer filamentOnSpool;         // grams remaining
    private Boolean enoughFilament;          // true if enough for next print

    @OneToOne
    @JoinColumn(name = "current_file_id")
    @JsonManagedReference
    @JsonIgnore
    private GcodeFile currentFile;
    
    private boolean enabled = true;
    private boolean inUse = true;
    private LocalDateTime availableUntil;

    @Column(name = "weight_of_current_print", nullable = false)
    private Double weightOfCurrentPrint;    // grams
    
    private String status;                   // idle, printing, offline

    private Integer successCount = 0;
    private Integer failCount = 0;

    public Printer() {}

    public Printer(String name, String ip, String apiKey, String model, String material, String color,
                   Integer filamentOnSpool, Boolean enoughFilament, Double weightOfCurrentPrint, String status) {
        this.name = name;
        this.ip = ip;
        this.apiKey = apiKey;
        this.model = model;
        this.material = material;
        this.color = color;
        this.filamentOnSpool = filamentOnSpool;
        this.enoughFilament = enoughFilament;
        this.weightOfCurrentPrint = weightOfCurrentPrint;
        this.status = status;
    }

    // ============================================================
    // Build Plate Dimension Methods (for BuildPlateNester)
    // ============================================================

    /**
     * Gets build plate width from associated PrinterProfile
     * Returns default if profile not loaded
     */
    public double getBuildPlateWidth() {
        return getDefaultBuildPlateWidth();
    }

    /**
     * Gets build plate depth from associated PrinterProfile
     * Returns default if profile not loaded
     */
    public double getBuildPlateDepth() {
        return getDefaultBuildPlateDepth();
    }

    /**
     * Gets build plate height from associated PrinterProfile
     * Returns default if profile not loaded
     */
    public double getBuildPlateHeight() {
        return getDefaultBuildPlateHeight();
    }

    /**
     * Default build plate width based on printer model
     */
    private double getDefaultBuildPlateWidth() {
        if (model == null) return 250.0;
        
        return switch (model.toUpperCase()) {
            case "MK4", "MK3S", "MK3" -> 250.0;
            case "MINI" -> 180.0;
            case "XL" -> 360.0;
            default -> 250.0; // Default to MK4 size
        };
    }

    /**
     * Default build plate depth based on printer model
     */
    private double getDefaultBuildPlateDepth() {
        if (model == null) return 210.0;
        
        return switch (model.toUpperCase()) {
            case "MK4", "MK3S", "MK3", "MK4S" -> 210.0;
            case "MINI" -> 180.0;
            case "XL" -> 360.0;
            default -> 210.0; // Default to MK4 size
        };
    }

    /**
     * Default build plate height based on printer model
     */
    private double getDefaultBuildPlateHeight() {
        if (model == null) return 220.0;
        
        return switch (model.toUpperCase()) {
            case "MK4" -> 220.0;
            case "MK3S", "MK3" -> 210.0;
            case "MINI" -> 180.0;
            case "XL" -> 360.0;
            default -> 220.0; // Default to MK4 size
        };
    }
    
    // ============================================================
    // Getters & Setters
    // ============================================================
    
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getConnectionType() { return connectionType; }
    public void setConnectionType(String connectionType) { this.connectionType = connectionType; }

    public Integer getFilamentOnSpool() { return filamentOnSpool; }
    public void setFilamentOnSpool(Integer filamentOnSpool) { this.filamentOnSpool = filamentOnSpool; }

    public Boolean getEnoughFilament() { return enoughFilament; }
    public void setEnoughFilament(Boolean enoughFilament) { this.enoughFilament = enoughFilament; }

    public Double getWeightOfCurrentPrint() { return weightOfCurrentPrint; }
    public void setWeightOfCurrentPrint(Double weightOfCurrentPrint) { this.weightOfCurrentPrint = weightOfCurrentPrint; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getSuccessCount() { return successCount; }
    public void setSuccessCount(Integer successCount) { this.successCount = successCount; }

    public Integer getFailCount() { return failCount; }
    public void setFailCount(Integer failCount) { this.failCount = failCount; }

    public GcodeFile getCurrentFile() {
        return currentFile;
    }

    public void setCurrentFile(GcodeFile currentFile) {
        this.currentFile = currentFile;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isInUse() {
        return inUse;
    }

    public void setInUse(boolean inUse) {
        this.inUse = inUse;
    }

    public LocalDateTime getAvailableUntil() {
        return availableUntil;
    }

    public void setAvailableUntil(LocalDateTime availableUntil) {
        this.availableUntil = availableUntil;
    }
}