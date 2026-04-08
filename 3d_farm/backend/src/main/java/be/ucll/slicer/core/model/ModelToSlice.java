package be.ucll.slicer.core.model;

import java.nio.file.Path;

public class ModelToSlice {

    private final String id;
    private final Path stlPath;
    private int instances;

    private final double footprintWidthMm;
    private final double footprintDepthMm;

    private final int estimatedSecondsSingle;

    private final double layerHeightMm;
    private final int infillPercent;
    private final boolean supports;
    private final boolean brim;

    private final String material;
    private final String color;

    public ModelToSlice(
            String id,
            Path stlPath,
            int instances,
            double footprintWidthMm,
            double footprintDepthMm,
            int estimatedSecondsSingle,
            double layerHeightMm,
            int infillPercent,
            boolean supports,
            boolean brim,
            String material,
            String color
    ) {
        this.id = id;
        this.stlPath = stlPath;
        this.instances = instances;
        this.footprintWidthMm = footprintWidthMm;
        this.footprintDepthMm = footprintDepthMm;
        this.estimatedSecondsSingle = estimatedSecondsSingle;
        this.layerHeightMm = layerHeightMm;
        this.infillPercent = infillPercent;
        this.supports = supports;
        this.brim = brim;
        this.material = material;
        this.color = color;
    }

    public String getId() { return id; }
    public Path getStlPath() { return stlPath; }

    public int getInstances() { return instances; }
    public void setInstances(int instances) { this.instances = instances; }

    public double getFootprintWidthMm() { return footprintWidthMm; }
    public double getFootprintDepthMm() { return footprintDepthMm; }

    public int getEstimatedSecondsSingle() { return estimatedSecondsSingle; }

    public double getLayerHeightMm() { return layerHeightMm; }
    public int getInfillPercent() { return infillPercent; }
    public boolean isSupports() { return supports; }
    public boolean isBrim() { return brim; }
    public String getMaterial() { return material; }
    public String getColor() { return color; }

    public double getFootprintArea() {
        return footprintWidthMm * footprintDepthMm;
    }
}
