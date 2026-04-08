package be.ucll.dto;

public class PrinterDTO {
    private Long id;
    private String name;
    private String model;
    private String status;
    private String color;
    private String material;
    private Integer filamentOnSpool;
    private Boolean enoughFilament;
    private Double weightOfCurrentPrint;
    private GcodeFileDTO currentFile; // ✅ linked file info (nullable)
    private Integer successCount;
    private Integer failCount;

    public PrinterDTO(String color, GcodeFileDTO currentFile, Boolean enoughFilament, Integer filamentOnSpool, Long id, String material, String model, String name, String status, Double weightOfCurrentPrint) {
        this.color = color;
        this.currentFile = currentFile;
        this.enoughFilament = enoughFilament;
        this.filamentOnSpool = filamentOnSpool;
        this.id = id;
        this.material = material;
        this.model = model;
        this.name = name;
        this.status = status;
        this.weightOfCurrentPrint = weightOfCurrentPrint;
    }

    public PrinterDTO() {
    }



    // getters and setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public Integer getFilamentOnSpool() {
        return filamentOnSpool;
    }

    public void setFilamentOnSpool(Integer filamentOnSpool) {
        this.filamentOnSpool = filamentOnSpool;
    }

    public Boolean getEnoughFilament() {
        return enoughFilament;
    }

    public void setEnoughFilament(Boolean enoughFilament) {
        this.enoughFilament = enoughFilament;
    }

    public Double getWeightOfCurrentPrint() {
        return weightOfCurrentPrint;
    }

    public void setWeightOfCurrentPrint(Double weightOfCurrentPrint) {
        this.weightOfCurrentPrint = weightOfCurrentPrint;
    }

    public GcodeFileDTO getCurrentFile() {
        return currentFile;
    }

    public void setCurrentFile(GcodeFileDTO currentFile) {
        this.currentFile = currentFile;
    }

    public Integer getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(Integer successCount) {
        this.successCount = successCount;
    }

    public Integer getFailCount() {
        return failCount;
    }

    public void setFailCount(Integer failCount) {
        this.failCount = failCount;
    }
}
