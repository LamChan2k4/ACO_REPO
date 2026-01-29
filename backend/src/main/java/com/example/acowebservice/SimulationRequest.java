package com.example.acowebservice;

import java.util.List;

public class SimulationRequest {
    private String algorithm;
    private List<Node> nodes;
    private Integer numAnts;
    private Integer maxIterations;
    private Integer numColors;
    private Double alpha;
    private Double beta;
    private Double evaporation;
    private Double q0;
    private Double mutationRate;
    private Integer numberOfRuns;
    private Integer tournamentSize;

    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

    public List<Node> getNodes() { return nodes; }
    public void setNodes(List<Node> nodes) { this.nodes = nodes; }

    // Dùng Integer thay cho int để cho phép nhận null từ JSON mà không lỗi 400
    public Integer getNumAnts() { return numAnts; }
    public void setNumAnts(Integer numAnts) { this.numAnts = numAnts; }

    public Integer getMaxIterations() { return maxIterations; }
    public void setMaxIterations(Integer maxIterations) { this.maxIterations = maxIterations; }

    public Integer getNumColors() { return numColors; }
    public void setNumColors(Integer numColors) { this.numColors = numColors; }

    public Double getAlpha() { return alpha; }
    public void setAlpha(Double alpha) { this.alpha = alpha; }

    public Double getBeta() { return beta; }
    public void setBeta(Double beta) { this.beta = beta; }

    public Double getEvaporation() { return evaporation; }
    public void setEvaporation(Double evaporation) { this.evaporation = evaporation; }

    public Double getQ0() { return q0; }
    public void setQ0(Double q0) { this.q0 = q0; }

    public Double getMutationRate() { return mutationRate; }
    public void setMutationRate(Double mutationRate) { this.mutationRate = mutationRate; }

    public Integer getNumberOfRuns() { return numberOfRuns; }
    public void setNumberOfRuns(Integer numberOfRuns) { this.numberOfRuns = numberOfRuns; }

    public Integer getTournamentSize() { return tournamentSize; }
    public void setTournamentSize(Integer tournamentSize) { this.tournamentSize = tournamentSize; }
}