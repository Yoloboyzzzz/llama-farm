package be.ucll.slicer.nesting;

import be.ucll.slicer.core.model.BuildPlatePlan;
import be.ucll.slicer.core.model.ModelToSlice;
import be.ucll.slicer.core.model.SliceParameters;

import java.util.*;

public class NestingEngine {

    public List<BuildPlatePlan> nest(List<ModelToSlice> models, SliceParameters params) {
        double plateArea = params.getPlateWidthMm() * params.getPlateDepthMm();
        double maxUsableArea = plateArea * 0.80;

        List<ModelToSlice> flattened = new ArrayList<>();
        for (ModelToSlice model : models) {
            for (int i = 0; i < model.getInstances(); i++) {
                flattened.add(model);
            }
        }

        flattened.sort(Comparator.comparingDouble(ModelToSlice::getFootprintArea).reversed());

        List<BuildPlatePlan> plates = new ArrayList<>();
        if (flattened.isEmpty()) return plates;

        int plateIndex = 1;
        BuildPlatePlan currentPlate = new BuildPlatePlan(plateIndex++);
        double currentArea = 0.0;
        Map<ModelToSlice, Integer> counts = new LinkedHashMap<>();

        for (ModelToSlice instance : flattened) {
            double area = instance.getFootprintArea();

            boolean overflowArea = currentArea + area > maxUsableArea;
            boolean overflowCount = currentPlate.getTotalItemCount() + 1 > params.getMaxItemsPerPlate();

            if (overflowArea || overflowCount) {
                for (var e : counts.entrySet()) {
                    currentPlate.addItem(e.getKey(), e.getValue());
                }
                plates.add(currentPlate);

                currentPlate = new BuildPlatePlan(plateIndex++);
                currentArea = 0.0;
                counts.clear();
            }

            currentArea += area;
            counts.merge(instance, 1, Integer::sum);
        }

        if (!counts.isEmpty()) {
            for (var e : counts.entrySet()) {
                currentPlate.addItem(e.getKey(), e.getValue());
            }
            plates.add(currentPlate);
        }

        return plates;
    }
}
