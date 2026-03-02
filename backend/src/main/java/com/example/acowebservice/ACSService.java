package com.example.acowebservice;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ACSService implements SolverStrategy {

    private final Random random = new Random();

    @Override
    public SimulationResponse solve(SimulationRequest req) {
        // --- 1. BẤM GIỜ NANO & KHỞI TẠO AN TOÀN ---
        long startNano = System.nanoTime();
        
        List<Node> nodes = req.getNodes();
        if (nodes == null || nodes.isEmpty()) return new SimulationResponse();

        // Lọc node rác bảo vệ hệ thống
        List<Node> validNodes = nodes.stream()
                .filter(n -> n != null && n.getId() != null)
                .collect(Collectors.toList());
        
        int numNodes = validNodes.size();
        
        // Tham số an toàn từ Request
        int numAnts = (req.getNumAnts() != null) ? req.getNumAnts() : 30;
        int maxIterations = (req.getMaxIterations() != null) ? req.getMaxIterations() : 100;
        int numColors = (req.getNumColors() != null) ? req.getNumColors() : 20;
        double alpha = (req.getAlpha() != null) ? req.getAlpha() : 1.0;
        double beta = (req.getBeta() != null) ? req.getBeta() : 2.0;
        double rho = (req.getEvaporation() != null) ? req.getEvaporation() : 0.1;
        double q0 = (req.getQ0() != null) ? req.getQ0() : 0.9;
        double localXi = 0.1; // Local decay constant cho ACS

        // Giá trị Pheromone khởi tạo cực nhỏ (Đặc trưng ACS)
        double tau0 = 1.0 / (numNodes * 2.0); 

        // Cache bậc đỉnh cho DSATUR
        int[] nodeDegrees = new int[numNodes];
        for (int i = 0; i < numNodes; i++) {
            nodeDegrees[i] = validNodes.get(i).getNeighbors().size();
        }

        // --- 2. KHỞI TẠO BỘ NHỚ ---
        double[][] pheromoneMatrix = new double[numNodes][numColors];
        for(int i = 0; i < numNodes; i++) Arrays.fill(pheromoneMatrix[i], tau0);

        Ant[] ants = new Ant[numAnts];
        for(int i = 0; i < numAnts; i++) ants[i] = new Ant(numNodes);

        // BỘ NHỚ GIỮ KỶ LỤC GLOBAL (Mục tiêu: Min Conflicts -> Min Colors)
        int[] globalBestSolution = new int[numNodes];
        Arrays.fill(globalBestSolution, -1);
        int globalBestQuality = Integer.MAX_VALUE; 
        int globalBestConflicts = Integer.MAX_VALUE;
        List<Integer> globalBestTourOrder = new ArrayList<>();
        List<SimulationStep> history = new ArrayList<>();

        // --- 3. VÒNG LẶP CHÍNH ---
        for (int i = 0; i < maxIterations; i++) {
            // Kiểm tra tín hiệu dừng (Refresh web)
            if (Thread.currentThread().isInterrupted()) {
                System.out.println("🛑 [LOG] ACS dừng khẩn cấp!");
                return new SimulationResponse();
            }

            // Xây dựng lời giải & Áp dụng LOCAL UPDATE (Mùi hương giảm ngay khi kiến đi qua)
            constructSolutionsACS(ants, numNodes, pheromoneMatrix, alpha, beta, validNodes, 
                                 numColors, nodeDegrees, q0, localXi, tau0);
            
            // Tìm kiến tốt nhất vòng (Priority: Conflicts -> Colors)
            Ant iterBestAnt = null;
            int iterMinConflicts = Integer.MAX_VALUE;
            int iterMinColors = Integer.MAX_VALUE;

            for (Ant ant : ants) {
                int curConf = calculateTotalConflicts(ant.getSolution(), validNodes);
                int curCols = ant.getNumberOfColorsUsed();
                
                if (iterBestAnt == null || curConf < iterMinConflicts || (curConf == iterMinConflicts && curCols < iterMinColors)) {
                    iterBestAnt = ant;
                    iterMinConflicts = curConf;
                    iterMinColors = curCols;
                }
            }

            // Cập nhật kỷ lục thế giới
            boolean foundNewBest = false;
            if (iterMinConflicts < globalBestConflicts || (iterMinConflicts == globalBestConflicts && iterMinColors < globalBestQuality)) {
                globalBestConflicts = iterMinConflicts;
                globalBestQuality = iterMinColors;
                System.arraycopy(iterBestAnt.getSolution(), 0, globalBestSolution, 0, numNodes);
                globalBestTourOrder = new ArrayList<>(iterBestAnt.getTourOrder());
                foundNewBest = true;
            }

            // ✅ GLOBAL UPDATE: Chỉ duy nhất Global Best được phép củng cố mùi hương (Mô hình ACS chuẩn)
            updatePheromonesGlobal(numNodes, pheromoneMatrix, rho, globalBestSolution, globalBestQuality);

            // Lưu lịch sử (Cắt tỉa JSON)
            if (foundNewBest || (numNodes < 300 && i % 20 == 0)) {
                double[] conf = (numNodes < 500) ? calculateACOConfidence(pheromoneMatrix, globalBestSolution, numColors) : new double[0];
                history.add(new SimulationStep(i + 1, globalBestQuality, globalBestSolution.clone(), conf));
                if (history.size() > 30) history.remove(0);
            }
        }

        long durationMs = (System.nanoTime() - startNano) / 1_000_000;

        // --- 4. TRACE REPLAY ---
        List<NodeColorAction> trace = new ArrayList<>();
        if (numNodes < 300) {
            List<Integer> path = globalBestTourOrder.isEmpty() ? 
                    validNodes.stream().map(Node::getId).collect(Collectors.toList()) : globalBestTourOrder;
            int step = 0;
            for (Integer nid : path) trace.add(new NodeColorAction(nid, globalBestSolution[nid], step++));
        }

        return new SimulationResponse(
            globalBestQuality, 
            globalBestSolution, 
            globalBestConflicts, 
            history, 
            trace, 
            durationMs, 
            validNodes // Quan trọng để Frontend vẽ Edges
        );
    }

    private void constructSolutionsACS(Ant[] ants, int numNodes, double[][] matrix, double alpha, double beta,
                                       List<Node> nodes, int numColors, int[] nodeDegrees, 
                                       double q0, double xi, double tau0) {
        for (Ant ant : ants) {
            ant.reset();
            Set<Integer> unvisited = new HashSet<>();
            for (int i = 0; i < numNodes; i++) unvisited.add(i);

            while (!unvisited.isEmpty()) {
                if (Thread.currentThread().isInterrupted()) return;

                int nodeId = selectNextNodeDSATUR(ant, unvisited, nodes, nodeDegrees);
                unvisited.remove(nodeId);

                int color = selectNextColorACS(ant, nodeId, matrix, alpha, beta, nodes, numColors, q0);
                ant.setColor(nodeId, color);

                // ✅ LOCAL PHEROMONE UPDATE (Cốt lõi ACS): "Ăn mòn" mùi hương ngay tại nốt vừa chọn
                // Điều này làm cho các con kiến sau trong cùng 1 vòng thám hiểm các màu khác
                matrix[nodeId][color] = (1.0 - xi) * matrix[nodeId][color] + xi * tau0;
            }
        }
    }

    private int selectNextColorACS(Ant ant, int nodeId, double[][] matrix, double alpha, double beta, 
                                   List<Node> nodes, int numColors, double q0) {
        // TỐI ƯU O(N): Tìm màu chưa dùng của hàng xóm
        boolean[] used = new boolean[numColors];
        for (int neighborId : nodes.get(nodeId).getNeighbors()) {
            int c = ant.getSolution()[neighborId];
            if (c != -1 && c < numColors) used[c] = true;
        }

        List<Integer> validColors = new ArrayList<>();
        for (int i = 0; i < numColors; i++) if (!used[i]) validColors.add(i);
        
        if (validColors.isEmpty()) return random.nextInt(numColors); // Tránh bế tắc
        if (validColors.size() == 1) return validColors.get(0);

        double heuristic = nodes.get(nodeId).getNeighbors().size() + 0.1;

        // Cơ chế Pseudo-Random Proportional Rule (ACS)
        if (Math.random() <= q0) {
            // Chế độ THAM LAM (Exploitation): Chọn nồng độ tốt nhất trực tiếp
            int bestColor = validColors.get(0);
            double maxScore = -1.0;
            for (int c : validColors) {
                double score = Math.pow(matrix[nodeId][c], alpha) * Math.pow(heuristic, beta);
                if (score > maxScore) { maxScore = score; bestColor = c; }
            }
            return bestColor;
        } else {
            // Chế độ XÁC SUẤT (Exploration): Quay vòng quay may mắn (Roulette)
            double[] probs = new double[validColors.size()];
            double sum = 0;
            for (int i = 0; i < validColors.size(); i++) {
                probs[i] = Math.pow(matrix[nodeId][validColors.get(i)], alpha) * Math.pow(heuristic, beta);
                sum += probs[i];
            }
            double r = random.nextDouble() * sum;
            double cur = 0;
            for (int i = 0; i < probs.length; i++) {
                cur += probs[i];
                if (cur >= r) return validColors.get(i);
            }
            return validColors.get(validColors.size() - 1);
        }
    }

    private void updatePheromonesGlobal(int numNodes, double[][] matrix, double rho, int[] bestSol, int bestQuality) {
        double deposit = 1.0 / (double)(bestQuality + 1);
        // Lưu ý trong ACS: Chỉ Best-So-Far updates pheromone (giá trị tích lũy)
        for (int i = 0; i < numNodes; i++) {
            int c = bestSol[i];
            if (c != -1) {
                matrix[i][c] = (1.0 - rho) * matrix[i][c] + rho * deposit;
            }
        }
    }

    // Các hàm helper dùng chung tối ưu
    private int selectNextNodeDSATUR(Ant ant, Set<Integer> unvisited, List<Node> nodes, int[] nodeDegrees) {
        int bestId = -1; int maxSat = -1;
        List<Integer> candidates = new ArrayList<>();
        int[] sol = ant.getSolution();
        for (int id : unvisited) {
            Set<Integer> nCols = new HashSet<>();
            for (int neigh : nodes.get(id).getNeighbors()) if (sol[neigh] != -1) nCols.add(sol[neigh]);
            int sat = nCols.size();
            if (sat > maxSat) {
                maxSat = sat; candidates.clear(); candidates.add(id);
            } else if (sat == maxSat) {
                candidates.add(id);
            }
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    private int calculateTotalConflicts(int[] solution, List<Node> nodes) {
        int total = 0;
        for (Node u : nodes) {
            int uId = u.getId();
            for (int vId : u.getNeighbors()) if (solution[uId] != -1 && solution[uId] == solution[vId]) total++;
        }
        return total / 2;
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
}