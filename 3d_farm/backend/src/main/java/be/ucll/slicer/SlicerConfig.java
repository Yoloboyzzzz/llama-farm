package be.ucll.slicer;

import be.ucll.slicer.core.SlicingPlanner;
import be.ucll.slicer.nesting.NestingEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SlicerConfig {

    @Bean
    public NestingEngine nestingEngine() {
        return new NestingEngine();
    }

    @Bean
    public SlicingPlanner slicingPlanner(NestingEngine nestingEngine) {
        return new SlicingPlanner(nestingEngine);
    }
}
