package be.ucll.slicer.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PrintJobPlan {

    private final String jobIdentifier;
    private final List<BuildPlatePlan> plates = new ArrayList<>();

    public PrintJobPlan(String jobIdentifier) {
        this.jobIdentifier = jobIdentifier;
    }

    public String getJobIdentifier() { return jobIdentifier; }

    public List<BuildPlatePlan> getPlates() {
        return Collections.unmodifiableList(plates);
    }

    public void addPlate(BuildPlatePlan platePlan) {
        this.plates.add(platePlan);
    }
}
