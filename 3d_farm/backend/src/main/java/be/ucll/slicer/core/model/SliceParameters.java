package be.ucll.slicer.core.model;

public class SliceParameters {

    private double plateWidthMm;
    private double plateDepthMm;
    private String printerProfileName;
    private String prusaConfigPath;
    private int maxItemsPerPlate = 200;

    public double getPlateWidthMm() { return plateWidthMm; }
    public void setPlateWidthMm(double plateWidthMm) { this.plateWidthMm = plateWidthMm; }

    public double getPlateDepthMm() { return plateDepthMm; }
    public void setPlateDepthMm(double plateDepthMm) { this.plateDepthMm = plateDepthMm; }

    public String getPrinterProfileName() { return printerProfileName; }
    public void setPrinterProfileName(String printerProfileName) { this.printerProfileName = printerProfileName; }

    public String getPrusaConfigPath() { return prusaConfigPath; }
    public void setPrusaConfigPath(String prusaConfigPath) { this.prusaConfigPath = prusaConfigPath; }

    public int getMaxItemsPerPlate() { return maxItemsPerPlate; }
    public void setMaxItemsPerPlate(int maxItemsPerPlate) { this.maxItemsPerPlate = maxItemsPerPlate; }
}
