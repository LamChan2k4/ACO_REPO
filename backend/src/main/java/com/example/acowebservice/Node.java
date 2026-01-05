package com.example.acowebservice;
import java.util.List;
import java.util.ArrayList;
public class Node {
	private List<Integer> neighbors=new ArrayList<>();
	private int id;
	private double x;
	private double y;
	public Node() {
		
    }
	
	public Node(double x,double y,int id) {
		this.x=x;
		this.y=y;
		this.id=id;
	}
	
	public void addNeighbor(int neighborId) {
		this.neighbors.add(neighborId);
	}
	
	public double distance(Node otherCity) {
		double deltaX=this.x-otherCity.getX();
		double deltaY=this.y-otherCity.getY();
		return Math.sqrt(deltaX * deltaX + deltaY * deltaY);
	}
	public double getX() {
		return x;
	}
	public double getY() {
		return y;
	}
	public int getId() {
		return id;
	}
	public List<Integer> getNeighbors(){
		return neighbors;
	}
}
