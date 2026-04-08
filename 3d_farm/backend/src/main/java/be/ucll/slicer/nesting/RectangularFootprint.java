package be.ucll.slicer.nesting;

/**
 * Simple rectangular footprint helper.
 */
public class RectangularFootprint {

    private final double widthMm;
    private final double depthMm;

    public RectangularFootprint(double widthMm, double depthMm) {
        this.widthMm = widthMm;
        this.depthMm = depthMm;
    }

    public double getWidthMm() {
        return widthMm;
    }

    public double getDepthMm() {
        return depthMm;
    }

    public double area() {
        return widthMm * depthMm;
    }
}
