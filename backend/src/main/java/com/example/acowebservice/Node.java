package com.example.acowebservice;

import java.util.List;
import java.util.ArrayList;

public class Node {
    // Thuộc tính
    private List<Integer> neighbors = new ArrayList<>();
    private Integer id;   // Đổi từ int sang Integer để nhận null
    private Double x;     // Đổi từ double sang Double để nhận null
    private Double y;     // Đổi từ double sang Double để nhận null

    // 1. Constructor mặc định (Bắt buộc cho JSON Mapping)
    public Node() {
    }

    // 2. Constructor có tham số (Sử dụng Double và Integer)
    public Node(Double x, Double y, Integer id) {
        this.x = x;
        this.y = y;
        this.id = id;
    }

    // 3. Method thêm hàng xóm
    public void addNeighbor(Integer neighborId) {
        if (this.neighbors == null) {
            this.neighbors = new ArrayList<>();
        }
        this.neighbors.add(neighborId);
    }

    // 4. Method tính khoảng cách (Phòng khi dùng cho Robot/TSP)
    public double distance(Node otherCity) {
        // Kiểm tra tránh lỗi NullPointerException
        double x1 = (this.x != null) ? this.x : 0.0;
        double y1 = (this.y != null) ? this.y : 0.0;
        double x2 = (otherCity.getX() != null) ? otherCity.getX() : 0.0;
        double y2 = (otherCity.getY() != null) ? otherCity.getY() : 0.0;

        double deltaX = x1 - x2;
        double deltaY = y1 - y2;
        return Math.sqrt(deltaX * deltaX + deltaY * deltaY);
    }

    // --- 5. HỆ THỐNG GETTER VÀ SETTER (Dùng Double/Integer) ---

    public Double getX() {
        return x;
    }

    public void setX(Double x) {
        this.x = x;
    }

    public Double getY() {
        return y;
    }

    public void setY(Double y) {
        this.y = y;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public List<Integer> getNeighbors() {
        return neighbors;
    }

    public void setNeighbors(List<Integer> neighbors) {
        this.neighbors = neighbors;
    }
}