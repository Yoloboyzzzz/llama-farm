package be.ucll.model;

import jakarta.persistence.*;

@Entity
@Table(name = "printer_profiles")
public class PrinterProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String printerModel;   // MK4, MK3S, MINI
    private String material;       // PLA, PETG, ...
    private String configPath;     // full path to .ini

    private Double plateWidthMm;
    private Double plateDepthMm;
    private Double plateHeightMm;  // NEW HEIGHT FIELD

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPrinterModel() { return printerModel; }
    public void setPrinterModel(String printerModel) { this.printerModel = printerModel; }

    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }

    public String getConfigPath() { return configPath; }
    public void setConfigPath(String configPath) { this.configPath = configPath; }

    public Double getPlateWidthMm() { return plateWidthMm; }
    public void setPlateWidthMm(Double plateWidthMm) { this.plateWidthMm = plateWidthMm; }

    public Double getPlateDepthMm() { return plateDepthMm; }
    public void setPlateDepthMm(Double plateDepthMm) { this.plateDepthMm = plateDepthMm; }

    public Double getPlateHeightMm() { return plateHeightMm; }
    public void setPlateHeightMm(Double plateHeightMm) { this.plateHeightMm = plateHeightMm; }
}
