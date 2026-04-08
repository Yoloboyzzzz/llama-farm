package be.ucll.slicer.core;

import be.ucll.slicer.core.model.BuildPlatePlan;
import be.ucll.slicer.core.model.ModelToSlice;
import be.ucll.slicer.core.model.PrintJobPlan;
import be.ucll.slicer.core.model.SliceParameters;
import be.ucll.slicer.nesting.NestingEngine;

import java.util.List;

public class SlicingPlanner {

    private final NestingEngine nestingEngine;

    public SlicingPlanner(NestingEngine nestingEngine) {
        this.nestingEngine = nestingEngine;
    }

    public PrintJobPlan planJob(String jobIdentifier,
                                List<ModelToSlice> models,
                                SliceParameters params) {

        PrintJobPlan jobPlan = new PrintJobPlan(jobIdentifier);

        List<BuildPlatePlan> plates = nestingEngine.nest(models, params);
        for (BuildPlatePlan plate : plates) {
            int estimated = estimatePlateDurationSeconds(plate);
            plate.setEstimatedSeconds(estimated);
            jobPlan.addPlate(plate);
        }

        return jobPlan;
    }

    private int estimatePlateDurationSeconds(BuildPlatePlan plate) {
        int totalItems = plate.getTotalItemCount();
        boolean sharedPlate = totalItems > 1;

        int maxEffective = 0;

        for (BuildPlatePlan.PlateItem item : plate.getItems()) {
            ModelToSlice model = item.getModel();
            int base = model.getEstimatedSecondsSingle();

            int effectivePerPiece;
            if (sharedPlate && base < 3600) {
                effectivePerPiece = (int) Math.round(base * 0.7);
            } else {
                effectivePerPiece = base;
            }

            if (effectivePerPiece > maxEffective) {
                maxEffective = effectivePerPiece;
            }
        }

        return maxEffective;
    }
}
