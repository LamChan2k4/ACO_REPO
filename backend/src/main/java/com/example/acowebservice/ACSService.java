package com.example.acowebservice;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ACSService implements SolverStrategy {

    @Override
    public SimulationResponse solve(SimulationRequest req) {
        // --- 1. BẤM GIỜ & KHỞI TẠO AN TOÀN ---
        long startNano = System.nanoTime();
        
        List<Node> nodes = req.getNodes();
        int numAnts = (req.getNumAnts() != null) ? req.getNumAnts() : 30;
        int maxIterations = (req.getMaxIterations() != null) ? req.getMaxIterations() : 100;
        int numColors = (req.getNumColors() != null) ? req.getNumColors() : 20;
        
        double alpha = (req.getAlpha() != null) ? req.getAlpha() : 1.0;
        double beta = (req.getBeta() != null) ? req.getBeta() : 2.0;
        double rho = (req.getEvaporation() != null) ? req.getEvaporation() : 0.1;
        double q0 = (req.getQ0() != null) ? req.getQ0() : 0.9;
        double localXi = 0.1; // Local evaporation (ξ)

        // Lọc node rác bảo vệ hệ thống
        List<Node> validNodes = nodes.stream()
                .filter(n -> n != null && n.getId() != null)
                .collect(Collectors.toList());
        
        if (validNodes.isEmpty()) return new SimulationResponse(0, new int[0], 0, new ArrayList<>(), new ArrayList<>(), 0, validNodes);

        int numNodes = validNodes.size();
        double initialPheromone = 1.0 / (numNodes * 2.0);

        // Cache bậc đỉnh để DSATUR chạy nhanh
        int[] nodeDegrees = new int[numNodes];
        for (int i = 0; i < numNodes; i++) {
            nodeDegrees[i] = validNodes.get(i).getNeighbors().size();
        }

        // --- 2. KHỞI TẠO BỘ NHỚ ---
        double[][] pheromoneMatrix = new double[numNodes][numColors];
        for(int i = 0; i < numNodes; i++) Arrays.fill(pheromoneMatrix[i], initialPheromone);

        Ant[] ants = new Ant[numAnts];
        for(int i = 0; i < numAnts; i++) ants[i] = new Ant(numNodes);

        int[] bestSolution = new int[numNodes];
        int bestSolutionQuality = Integer.MAX_VALUE;
        List<Integer> bestTourOrder = new ArrayList<>();
        List<SimulationStep> history = new ArrayList<>();

        // --- 3. VÒNG LẶP TIẾN HÓA ---
        for (int i = 0; i < maxIterations; i++) {
            // Kiểm tra tín hiệu dừng (F5/Refresh)
            if (Thread.currentThread().isInterrupted()) {
                System.out.println("🛑 [LOG] ACS dừng khẩn cấp!");
                return new SimulationResponse();
            }

            // Xây dựng lời giải với Local Update (Đặc trưng của ACS)
            constructSolutionsACS(ants, numNodes, pheromoneMatrix, alpha, beta, validNodes, 
                                 numColors, nodeDegrees, q0, localXi, initialPheromone);
            
            // GLOBAL UPDATE: Chỉ con kiến tốt nhất lịch sử mới được để lại mùi
            boolean foundNewBest = false;
            for (Ant ant : ants) {
                int cost = ant.getNumberOfColorsUsed();
                if (cost < bestSolutionQuality) {
                    bestSolutionQuality = cost;
                    System.arraycopy(ant.getSolution(), 0, bestSolution, 0, numNodes);
                    bestTourOrder = new ArrayList<>(ant.getTourOrder());
                    foundNewBest = true;
                }
            }

            // Bay hơi và cập nhật Global Pheromone (Chỉ dành cho Best-So-Far)
            updatePheromonesGlobal(numNodes, pheromoneMatrix, rho, bestSolution, bestSolutionQuality);

            // Lưu lịch sử (Pruning cho đồ thị lớn)
            if (foundNewBest || (numNodes < 200 && i % 10 == 0)) {
                double[] currentConf = calculateACOConfidence(pheromoneMatrix, bestSolution, numColors);
                history.add(new SimulationStep(i + 1, bestSolutionQuality, bestSolution.clone(), currentConf));
                if (history.size() > 30) history.remove(0);
            }
        }

        long durationMs = (System.nanoTime() - startNano) / 1_000_000;

        // --- 4. ĐÓNG GÓI KẾT QUẢ (CẮT TỈA TRACE) ---
        List<NodeColorAction> trace = new ArrayList<>();
        if (numNodes < 300) { // Tránh Broken Pipe khi gửi JSON quá to
            List<Integer> path = bestTourOrder.isEmpty() ? 
                    validNodes.stream().map(Node::getId).collect(Collectors.toList()) : bestTourOrder;
            int step = 0;
            for (Integer nid : path) trace.add(new NodeColorAction(nid, bestSolution[nid], step++));
        }

        return new SimulationResponse(
            bestSolutionQuality, bestSolution, 
            calculateTotalConflicts(bestSolution, validNodes), 
            history, trace, durationMs, validNodes
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

                // Chọn nốt theo DSATUR
                int nodeId = selectNextNodeDSATUR(ant, unvisited, nodes, nodeDegrees);
                unvisited.remove(nodeId);

                // Chọn màu theo quy tắc ACS (Pseudo-random proportional rule)
                int color = selectNextColorACS(ant, nodeId, matrix, alpha, beta, nodes, numColors, q0);
                ant.setColor(nodeId, color);

                // ✅ LOCAL UPDATE: "Ăn bớt" mùi ngay khi vừa tô xong
                matrix[nodeId][color] = (1.0 - xi) * matrix[nodeId][color] + xi * tau0;
            }
        }
    }

    private int selectNextColorACS(Ant ant, int nodeId, double[][] matrix, double alpha, double beta, 
                                   List<Node> nodes, int numColors, double q0) {
        List<Integer> validColors = findValidColorsOptimized(ant, nodeId, nodes, numColors);
        if (validColors.size() == 1) return validColors.get(0);

        double heuristic = nodes.get(nodeId).getNeighbors().size() + 0.1;

        // Quy tắc Q0
        if (Math.random() <= q0) {
            // Khai thác (Exploitation): Chọn màu có tích (Mùi * Heuristic) lớn nhất
            int bestColor = validColors.get(0);
            double maxVal = -1.0;
            for (int c : validColors) {
                double val = Math.pow(matrix[nodeId][c], alpha) * Math.pow(heuristic, beta);
                if (val > maxVal) { maxVal = val; bestColor = c; }
            }
            return bestColor;
        } else {
            // Khám phá (Exploration): Tung xúc xắc theo xác suất
            double[] probs = new double[validColors.size()];
            double sum = 0;
            for (int i = 0; i < validColors.size(); i++) {
                probs[i] = Math.pow(matrix[nodeId][validColors.get(i)], alpha) * Math.pow(heuristic, beta);
                sum += probs[i];
            }
            if (sum == 0) return validColors.get(new Random().nextInt(validColors.size()));
            double r = Math.random() * sum;
            double cur = 0;
            for (int i = 0; i < probs.length; i++) {
                cur += probs[i];
                if (cur >= r) return validColors.get(i);
            }
            return validColors.get(validColors.size() - 1);
        }
    }

    private List<Integer> findValidColorsOptimized(Ant ant, int nodeId, List<Node> nodes, int numColors) {
        // TỐI ƯU: Dùng boolean array thay cho HashSet/Contains
        boolean[] used = new boolean[numColors];
        for (int neighborId : nodes.get(nodeId).getNeighbors()) {
            int c = ant.getSolution()[neighborId];
            if (c != -1 && c < numColors) used[c] = true;
        }
        List<Integer> valid = new ArrayList<>();
        for (int i = 0; i < numColors; i++) if (!used[i]) valid.add(i);
        if (valid.isEmpty()) for (int i = 0; i < numColors; i++) valid.add(i);
        return valid;
    }

    private void updatePheromonesGlobal(int numNodes, double[][] matrix, double rho, int[] bestSol, int bestQuality) {
        double deposit = 1.0 / bestQuality;
        double retain = 1.0 - rho;
        for (int i = 0; i < numNodes; i++) {
            int c = bestSol[i];
            if (c != -1) {
                matrix[i][c] = retain * matrix[i][c] + rho * deposit;
            }
        }
    }

    private int selectNextNodeDSATUR(Ant ant, Set<Integer> unvisited, List<Node> nodes, int[] nodeDegrees) {
        int bestId = -1; int maxSat = -1; int maxDeg = -1;
        List<Integer> candidates = new ArrayList<>();
        int[] sol = ant.getSolution();

        for (int id : unvisited) {
            Set<Integer> nColors = new HashSet<>();
            for (int neighbor : nodes.get(id).getNeighbors()) {
                if (sol[neighbor] != -1) nColors.add(sol[neighbor]);
            }
            int sat = nColors.size();
            int deg = nodeDegrees[id];

            if (sat > maxSat) {
                maxSat = sat; maxDeg = deg; candidates.clear(); candidates.add(id);
            } else if (sat == maxSat) {
                if (deg > maxDeg) {
                    maxDeg = deg; candidates.clear(); candidates.add(id);
                } else if (deg == maxDeg) candidates.add(id);
            }
        }
        return candidates.get(new Random().nextInt(candidates.size()));
    }

    private double[] calculateACOConfidence(double[][] matrix, int[] sol, int numColors) {
        double[] conf = new double[sol.length];
        for (int i = 0; i < sol.length; i++) {
            int c = sol[i];
            if (c == -1) { conf[i] = 0.0; continue; }
            double sum = 0;
            for (int j = 0; j < numColors; j++) sum += matrix[i][j];
            conf[i] = (sum > 0) ? (matrix[i][c] / sum) : 0.0;
        }
        return conf;
    }

    private int calculateTotalConflicts(int[] solution, List<Node> nodes) {
        int total = 0;
        for (Node u : nodes) {
            int uId = u.getId();
            for (int vId : u.getNeighbors()) {
                if (solution[uId] != -1 && solution[uId] == solution[vId]) total++;
            }
        }
        return total / 2;
    }

    private int countUniqueColors(int[] solution) {
        Set<Integer> colors = new HashSet<>();
        for (int c : solution) if (c != -1) colors.add(c);
        return colors.size();
    }
}