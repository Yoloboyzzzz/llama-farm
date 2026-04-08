package be.ucll.slicer.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BuildPlatePlan {

    public static class PlateItem {
        private final ModelToSlice model;
        private final int quantity;

        public PlateItem(ModelToSlice model, int quantity) {
            this.model = model;
            this.quantity = quantity;
        }

        public ModelToSlice getModel() { return model; }
        public int getQuantity() { return quantity; }
    }

    private final int plateIndex;
    private final List<PlateItem> items = new ArrayList<>();
    private int estimatedSeconds;

    public BuildPlatePlan(int plateIndex) {
        this.plateIndex = plateIndex;
    }

    public int getPlateIndex() { return plateIndex; }

    public List<PlateItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public void addItem(ModelToSlice model, int quantity) {
        this.items.add(new PlateItem(model, quantity));
    }

    public int getEstimatedSeconds() { return estimatedSeconds; }
    public void setEstimatedSeconds(int estimatedSeconds) { this.estimatedSeconds = estimatedSeconds; }

    public int getTotalItemCount() {
        return items.stream().mapToInt(PlateItem::getQuantity).sum();
    }
}
