package com.example.acowebservice;

public class NodeColorAction {
    private int nodeId;
    private int colorCode;
    private int stepNumber;

    public NodeColorAction(int nodeId, int colorCode, int stepNumber) {
        this.nodeId = nodeId;
        this.colorCode = colorCode;
        this.stepNumber = stepNumber;
    }

    // Spring Boot cần Getter để tạo JSON
    public int getNodeId() { return nodeId; }
    public int getColorCode() { return colorCode; }
    public int getStepNumber() { return stepNumber; }
}