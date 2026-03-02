package com.example.acowebservice;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class Ant {
	private final int[] solution;
	private List<Integer> tourOrder = new ArrayList<>();
	// Thêm hàm này vào class Ant.java của ông
	public int getSolutionFitness(List<Node> nodes) {
	    int totalConflicts = 0;
	    for (Node u : nodes) {
	        int uId = u.getId();
	        int uColor = this.solution[uId];
	        if (uColor == -1) {
	            totalConflicts += 100; // Phạt nặng nếu bỏ trống không tô màu
	            continue;
	        }
	        for (int vId : u.getNeighbors()) {
	            if (this.solution[uId] == this.solution[vId]) {
	                totalConflicts++;
	            }
	        }
	    }
	    return totalConflicts / 2; // Đồ thị vô hướng tính 2 lần nên chia 2
	}
	public Ant(int numNodes) {
		this.solution=new int[numNodes];
		Arrays.fill(this.solution, -1);
	}
	public void setColor(int nodeId,int color) {
		this.solution[nodeId]=color;
		tourOrder.add(nodeId);
	}
	public int getNumberOfColorsUsed() {
		HashSet<Integer>uniqueColors = new HashSet<>();
		for(int color:solution) {
			uniqueColors.add(color);
		}
		return uniqueColors.size();
	}
	public void reset() {
		Arrays.fill(this.solution, -1);
		tourOrder.clear();
	}
	public int[] getSolution() {
		return solution;
	}
	public List<Integer> getTourOrder() {
        return tourOrder;
    }
}
