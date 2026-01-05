package com.example.acowebservice;

import java.util.List;

public class SimulationRequest {
	private String algorithm;
	private List<Node> nodes;
	private int numAnts;
	private int maxIterations;
	private int numColors;
	private int numberOfRuns = 1;
	private double alpha = 1.0;         // Cho AS, ACS
    private double evaporation = 0.5;   // Cho AS, ACS (rho)
    
    private double beta = 2.0;          // Cho ACS
    private double q0 = 0.9;            // Cho ACS
    
    private double mutationRate = 0.05; // Cho GA
    private int tournamentSize = 5;
	public int getNumberOfRuns() { return numberOfRuns; }
    public void setNumberOfRuns(int numberOfRuns) { 
    	this.numberOfRuns = numberOfRuns;
    }
    public String getAlgorithm() {
        return algorithm;
    }
    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }
	public List<Node> getNodes(){
		return nodes;
	}
	public void setNodes(List<Node> nodes) {
        this.nodes = nodes;
    }
    public int getNumAnts() {
        return numAnts;
    }
    public void setNumAnts(int numAnts) {
        this.numAnts = numAnts;
    }
    public int getMaxIterations() {
        return maxIterations;
    }
    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }
    public int getNumColors() {
        return numColors;
    }
    public void setNumColors(int numColors) {
        this.numColors = numColors;
    }
    public double getAlpha() { return alpha; }
    public void setAlpha(double alpha) { this.alpha = alpha; }

    public double getEvaporation() { return evaporation; }
    public void setEvaporation(double evaporation) { this.evaporation = evaporation; }

    public double getBeta() { return beta; }
    public void setBeta(double beta) { this.beta = beta; }

    public double getQ0() { return q0; }
    public void setQ0(double q0) { this.q0 = q0; }

    public double getMutationRate() { return mutationRate; }
    public void setMutationRate(double mutationRate) { this.mutationRate = mutationRate; }
    
    public int getTournamentSize() { return tournamentSize; }
    public void setTournamentSize(int tournamentSize) { this.tournamentSize = tournamentSize; }
}
