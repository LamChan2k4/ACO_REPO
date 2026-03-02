package com.example.acowebservice;

import java.util.List;

public class SimulationResponse {
	
    private int bestQuality;
    private int[] bestSolution;
    private int bestConflicts;
    private List<SimulationStep> history;
    private List<NodeColorAction> detailedTrace;
    private long executionTimeMs;
    
    // ✅ THÊM TRƯỜNG NÀY: Để gửi lại cấu trúc đồ thị ( Neighbors, X, Y ) cho Frontend vẽ
    private List<Node> nodes; 

    // --- 1. CONSTRUCTOR MẶC ĐỊNH (Rất quan trọng cho JSON) ---
    public SimulationResponse() {
    }

    // --- 2. CONSTRUCTOR ĐẦY ĐỦ THAM SỐ (6 tham số cũ + 1 mới) ---
    public SimulationResponse(int bestQuality, int[] bestSolution, int bestConflicts, List<SimulationStep> history,
			List<NodeColorAction> detailedTrace, long executionTimeMs, List<Node> nodes) {
		super();
		this.bestQuality = bestQuality;
		this.bestSolution = bestSolution;
		this.bestConflicts = bestConflicts;
		this.history = history;
		this.detailedTrace = detailedTrace;
		this.executionTimeMs = executionTimeMs;
		this.nodes = nodes; // Lưu lại danh sách node có chứa neighbor
	}
    
    // --- GETTER & SETTER ---
    public List<Node> getNodes() { return nodes; }
    public void setNodes(List<Node> nodes) { this.nodes = nodes; }

    public int getBestQuality() { return bestQuality; }
    public void setBestQuality(int bestQuality) { this.bestQuality = bestQuality; }

    public int[] getBestSolution() { return bestSolution; }
    public void setBestSolution(int[] bestSolution) { this.bestSolution = bestSolution; }

    public int getBestConflicts() { return bestConflicts; }
    public void setBestConflicts(int bestConflicts) { this.bestConflicts = bestConflicts; }

    public List<SimulationStep> getHistory() { return history; }
    public void setHistory(List<SimulationStep> history) { this.history = history; }

    public List<NodeColorAction> getDetailedTrace() { return detailedTrace; }
    public void setDetailedTrace(List<NodeColorAction> detailedTrace) { this.detailedTrace = detailedTrace; }

    public long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }
}