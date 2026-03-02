package com.example.acowebservice;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AntSystemService implements SolverStrategy {

    @Override
    public SimulationResponse solve(SimulationRequest req) {
        long startNano = System.nanoTime();
        
        // --- 1. KHỞI TẠO AN TOÀN ---
        List<Node> nodes = req.getNodes();
        int numAnts = (req.getNumAnts() != null) ? req.getNumAnts() : 30;
        int maxIterations = (req.getMaxIterations() != null) ? req.getMaxIterations() : 100;
        int numColors = (req.getNumColors() != null) ? req.getNumColors() : 20;
        double alpha = (req.getAlpha() != null) ? req.getAlpha() : 1.0;
        double rho = (req.getEvaporation() != null) ? req.getEvaporation() : 0.1;

        List<Node> validNodes = nodes.stream()
                .filter(n -> n != null && n.getId() != null)
                .collect(Collectors.toList());
        if (validNodes.isEmpty()) return new SimulationResponse();

        int numNodes = validNodes.size();
        int[] nodeDegrees = new int[numNodes];
        for (int i = 0; i < numNodes; i++) {
            nodeDegrees[i] = validNodes.get(i).getNeighbors().size();
        }

        // Khởi tạo nồng độ mùi (MMAS Style - Initial Max)
        double[][] pheromoneMatrix = new double[numNodes][numColors];
        for(int i = 0; i < numNodes; i++) Arrays.fill(pheromoneMatrix[i], 1.0);

        Ant[] ants = new Ant[numAnts];
        for (int i = 0; i < numAnts; i++) ants[i] = new Ant(numNodes);

        // --- BỘ NHỚ GIỮ KỶ LỤC GLOBAL ---
        int[] globalBestSolution = new int[numNodes];
        int globalBestQuality = Integer.MAX_VALUE; // Số màu tối ưu
        int globalBestConflicts = Integer.MAX_VALUE; // Xung đột tối ưu (mục tiêu chính = 0)
        List<Integer> globalBestTourOrder = new ArrayList<>();
        List<SimulationStep> history = new ArrayList<>();

        // --- 2. VÒNG LẶP CHÍNH ---
        for (int i = 0; i < maxIterations; i++) {
            // Tín hiệu dừng dọn rác từ Controller
            if (Thread.currentThread().isInterrupted()) {
                System.out.println("🛑 [LOG] AS dừng để giải phóng CPU.");
                return new SimulationResponse();
            }

            // Kiến đi tìm lời giải
            constructSolutionsDSATUR(ants, numNodes, pheromoneMatrix, alpha, validNodes, numColors, nodeDegrees);
            updatePheromones(numNodes, pheromoneMatrix, rho, ants, numColors);

            // ========================================================
            // ✅ PHẦN 3: LOGIC FIX "BẤY KIẾN THÔNG MINH" (TỐI ƯU CONFLICTS TRƯỚC)
            // ========================================================
            Ant iterBestAnt = null;
            int iterMinConflicts = Integer.MAX_VALUE;
            int iterMinColors = Integer.MAX_VALUE;

            for (Ant ant : ants) {
                int currentConflicts = calculateTotalConflicts(ant.getSolution(), validNodes);
                int currentColors = ant.getNumberOfColorsUsed();

                // KIỂM TRA: Ai là kiến tốt nhất trong vòng này?
                // Ưu tiên 1: Ít conflict nhất
                // Ưu tiên 2: Nếu conflict bằng nhau thì ai dùng ít màu hơn
                if (iterBestAnt == null || currentConflicts < iterMinConflicts || 
                   (currentConflicts == iterMinConflicts && currentColors < iterMinColors)) {
                    
                    iterBestAnt = ant;
                    iterMinConflicts = currentConflicts;
                    iterMinColors = currentColors;
                }
            }

            // SO SÁNH VỚI KỶ LỤC THẾ GIỚI (GLOBAL BEST)
            boolean foundNewBest = false;
            if (iterMinConflicts < globalBestConflicts || 
               (iterMinConflicts == globalBestConflicts && iterMinColors < globalBestQuality)) {
                
                globalBestConflicts = iterMinConflicts;
                globalBestQuality = iterMinColors;
                System.arraycopy(iterBestAnt.getSolution(), 0, globalBestSolution, 0, numNodes);
                globalBestTourOrder = new ArrayList<>(iterBestAnt.getTourOrder());
                foundNewBest = true;
            }
            // ========================================================

            // Lưu lịch sử tiến hóa (Chỉ lưu 30 mốc tiêu biểu)
            if (foundNewBest || (numNodes < 250 && i % 10 == 0)) {
                double[] currentConf = calculateACOConfidence(pheromoneMatrix, globalBestSolution, numColors);
                // SimulationStep nhận: Iteration, Colors, Solution, Confidence
                history.add(new SimulationStep(i + 1, globalBestQuality, globalBestSolution.clone(), currentConf));
                if (history.size() > 30) history.remove(0);
            }
            
            // Nếu đã giải quyết được tuyệt đối (0 lỗi và số màu tối ưu), có thể dừng sớm
            if (globalBestConflicts == 0 && globalBestQuality <= 3) break; // (3 là ví dụ số sắc số bé nhất)
        }

        long durationMs = (System.nanoTime() - startNano) / 1_000_000;

        // --- 4. TRACE REPLAY ---
        List<NodeColorAction> trace = new ArrayList<>();
        if (numNodes < 300) {
            List<Integer> pathOrder = globalBestTourOrder.isEmpty() ? 
                    validNodes.stream().map(Node::getId).collect(Collectors.toList()) : globalBestTourOrder;
            int stepCount = 0;
            for (Integer nodeId : pathOrder) {
                trace.add(new NodeColorAction(nodeId, globalBestSolution[nodeId], stepCount++));
            }
        }

        // TRẢ VỀ RESPONSE 7 THAM SỐ
        return new SimulationResponse(
            globalBestQuality,               // 1. bestQuality
            globalBestSolution,              // 2. bestSolution
            globalBestConflicts,             // 3. conflicts (Số thực tế!)
            history,                         // 4. history
            trace,                           // 5. detailedTrace
            durationMs,                      // 6. executionTimeMs
            validNodes                       // 7. nodes
        );
    }

    // --- CÁC HÀM TỐI ƯU HIỆU NĂNG ---

    private void constructSolutionsDSATUR(Ant[] ants, int numNodes, double[][] matrix, double alpha, 
                                         List<Node> nodes, int numColors, int[] nodeDegrees) {
        for (Ant ant : ants) {
            ant.reset();
            Set<Integer> unvisited = new HashSet<>();
            for (int i = 0; i < numNodes; i++) unvisited.add(i);
            while (!unvisited.isEmpty()) {
                if (Thread.currentThread().isInterrupted()) return;
                int selectedNodeId = selectNextNodeDSATUR(ant, unvisited, nodes, nodeDegrees);
                unvisited.remove(selectedNodeId);
                int selectedColor = selectNextColor(ant, selectedNodeId, matrix, alpha, nodes, numColors);
                ant.setColor(selectedNodeId, selectedColor);
            }
        }
    }

    private int selectNextColor(Ant ant, int nodeId, double[][] matrix, double alpha, List<Node> nodes, int numColors) {
        // Dùng mảng boolean O(N) tìm màu
        boolean[] usedColors = new boolean[numColors];
        for (int neighborId : nodes.get(nodeId).getNeighbors()) {
            int c = ant.getSolution()[neighborId];
            if (c != -1 && c < numColors) usedColors[c] = true;
        }

        List<Integer> validColors = new ArrayList<>();
        for (int i = 0; i < numColors; i++) if (!usedColors[i]) validColors.add(i);

        if (validColors.isEmpty()) return new Random().nextInt(numColors);
        if (validColors.size() == 1) return validColors.get(0);

        // Bốc thăm theo xác suất nồng độ mùi
        double[] probs = new double[validColors.size()];
        double sum = 0.0;
        for (int i = 0; i < validColors.size(); i++) {
            probs[i] = Math.pow(matrix[nodeId][validColors.get(i)], alpha);
            sum += probs[i];
        }
        double r = Math.random() * sum;
        double current = 0.0;
        for (int i = 0; i < probs.length; i++) {
            current += probs[i];
            if (current >= r) return validColors.get(i);
        }
        return validColors.get(validColors.size() - 1);
    }

    private int selectNextNodeDSATUR(Ant ant, Set<Integer> unvisited, List<Node> nodes, int[] nodeDegrees) {
        int bestId = -1; int maxSat = -1;
        List<Integer> candidates = new ArrayList<>();
        for (int id : unvisited) {
            Set<Integer> colors = new HashSet<>();
            for (int neigh : nodes.get(id).getNeighbors()) {
                if (ant.getSolution()[neigh] != -1) colors.add(ant.getSolution()[neigh]);
            }
            int sat = colors.size();
            if (sat > maxSat) {
                maxSat = sat; candidates.clear(); candidates.add(id);
            } else if (sat == maxSat) {
                candidates.add(id);
            }
        }
        return candidates.get(new Random().nextInt(candidates.size()));
    }

    private void updatePheromones(int numNodes, double[][] matrix, double rho, Ant[] ants, int numColors) {
        for (int i = 0; i < numNodes; i++) {
            for (int j = 0; j < numColors; j++) matrix[i][j] *= (1.0 - rho);
        }
        for (Ant ant : ants) {
            double deposit = 1.0 / (ant.getNumberOfColorsUsed() + 1);
            int[] sol = ant.getSolution();
            for (int i = 0; i < numNodes; i++) {
                if (sol[i] != -1) matrix[i][sol[i]] += deposit;
            }
        }
    }

    private int calculateTotalConflicts(int[] solution, List<Node> nodes) {
        int conflicts = 0;
        for (Node u : nodes) {
            int uId = u.getId();
            for (int vId : u.getNeighbors()) {
                if (solution[uId] != -1 && solution[uId] == solution[vId]) conflicts++;
            }
        }
        return conflicts / 2;
    }

    private int countUniqueColors(int[] solution) {
        Set<Integer> colors = new HashSet<>();
        for (int c : solution) if (c != -1) colors.add(c);
        return colors.size();
    }

    private double[] calculateACOConfidence(double[][] matrix, int[] sol, int numColors) {
        double[] conf = new double[sol.length];
        for (int i = 0; i < sol.length; i++) {
            if (sol[i] == -1) continue;
            double sum = 0;
            for (int j = 0; j < numColors; j++) sum += matrix[i][j];
            conf[i] = (sum > 0) ? (matrix[i][sol[i]] / sum) : 0.0;
        }
        return conf;
    }

    private void initializePheromones(double[][] matrix, int numNodes, int numColors, double initP) {
        for (int i = 0; i < numNodes; i++) Arrays.fill(matrix[i], initP);
    }
}