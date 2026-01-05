package com.example.acowebservice;

import java.util.List;

public class SimulationResponse {
    private int bestQuality;
    private int[] bestSolution;
    private int conflicts;
    // Cái này chứa cuộn phim time-lapse
    private List<SimulationStep> history;
    private List<NodeColorAction> detailedTrace;
    // Constructor
    public SimulationResponse(int bestQuality, int[] bestSolution, List<SimulationStep> history, int conflicts,List<NodeColorAction> detailedTrace) {
        this.bestQuality = bestQuality;
        this.bestSolution = bestSolution;
        this.history = history;
        this.conflicts = conflicts;
        this.detailedTrace = detailedTrace;
    }

    public List<NodeColorAction> getDetailedTrace() {
        return detailedTrace;
    }
    // --- GETTER (BẮT BUỘC PHẢI CÓ ĐỦ) ---
    public int getBestQuality() { return bestQuality; }
    public int[] getBestSolution() { return bestSolution; }
    public int getConflicts() { return conflicts; }
    // Spring Boot tìm hàm này để tạo ra field "history" trong JSON
    public List<SimulationStep> getHistory() { return history; }
}

