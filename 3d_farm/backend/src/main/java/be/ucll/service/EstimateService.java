package be.ucll.service;

import be.ucll.slicer.PrusaSlicerEstimateCLI;
import be.ucll.util.GcodeMetadataReader;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

@Service
public class EstimateService {

    private final PrusaSlicerEstimateCLI slicer;

    public EstimateService(PrusaSlicerEstimateCLI slicer) {
        this.slicer = slicer;
    }

    public EstimateResult estimate(MultipartFile file, int infill) throws Exception {

        File tempSTL = File.createTempFile("estimate_", ".stl");
        file.transferTo(tempSTL);

        GcodeMetadataReader.GcodeInfo info = slicer.sliceAndReadMetadata(tempSTL, infill);

        EstimateResult result = new EstimateResult();
        result.filename = file.getOriginalFilename();
        result.grams = info.filamentUsedGrams;
        result.seconds = info.durationSeconds;

        return result;
    }

    public static class EstimateResult {
        public String filename;
        public int grams;
        public int seconds;
    }
}
