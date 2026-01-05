package com.example.acowebservice;

public class SimulationStep {
    private int iterationNumber;
    private int quality;
    private int[] solution;
    
    // [MỚI] Mảng lưu độ tự tin (0.0 -> 1.0) cho từng node
    private double[] confidence;

    public SimulationStep(int iterationNumber, int quality, int[] solution, double[] confidence) {
        this.iterationNumber = iterationNumber;
        this.quality = quality;
        this.solution = solution;
        this.confidence = confidence;
    }

    public int getIterationNumber() { return iterationNumber; }
    public int getQuality() { return quality; }
    public int[] getSolution() { return solution; }
    public double[] getConfidence() { return confidence; }
}