package be.ucll.dto;

public class EstimateResponse {
    public String filename;
    public double weightGrams;

    public EstimateResponse(String filename, double weightGrams) {
        this.filename = filename;
        this.weightGrams = weightGrams;
    }
}
