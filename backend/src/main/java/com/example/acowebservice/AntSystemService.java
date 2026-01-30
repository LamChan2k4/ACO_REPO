package com.example.acowebservice;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AntSystemService implements SolverStrategy {

    @Override
    public SimulationResponse solve(SimulationRequest req) {
        // --- 1. KHỞI TẠO VÀ BẤM GIỜ (NANO) ---
        long startNano = System.nanoTime();
        
        List<Node> nodes = req.getNodes();
        int numAnts = (req.getNumAnts() != null) ? req.getNumAnts() : 30;
        int maxIterations = (req.getMaxIterations() != null) ? req.getMaxIterations() : 100;
        int numColors = (req.getNumColors() != null) ? req.getNumColors() : 20;
        double alpha = (req.getAlpha() != null) ? req.getAlpha() : 1.0;
        double rho = (req.getEvaporation() != null) ? req.getEvaporation() : 0.1;
        double initialPheromone = 1.0;

        // Lọc node rác
        List<Node> validNodes = nodes.stream()
                .filter(n -> n != null && n.getId() != null)
                .collect(Collectors.toList());

        if (validNodes.isEmpty()) return new SimulationResponse(0, new int[0], 0, new ArrayList<>(), new ArrayList<>(), 0, validNodes);

        int numNodes = validNodes.size();
        
        // --- 2. CHUẨN BỊ DỮ LIỆU TỐI ƯU ---
        // Cache bậc của node để không phải gọi hàm size() liên tục
        int[] nodeDegrees = new int[numNodes];
        for (int i = 0; i < numNodes; i++) {
            nodeDegrees[i] = validNodes.get(i).getNeighbors().size();
        }

        double[][] pheromoneMatrix = new double[numNodes][numColors];
        initializePheromones(pheromoneMatrix, numNodes, numColors, initialPheromone);

        Ant[] ants = new Ant[numAnts];
        for (int i = 0; i < numAnts; i++) ants[i] = new Ant(numNodes);

        int[] bestSolution = new int[numNodes];
        int bestSolutionQuality = Integer.MAX_VALUE;
        List<Integer> bestTourOrder = new ArrayList<>();
        List<SimulationStep> history = new ArrayList<>();

        // --- 3. VÒNG LẶP CHÍNH (VÙNG TỐI ƯU) ---
        for (int i = 0; i < maxIterations; i++) {
            // Kiểm tra tín hiệu dừng khẩn cấp (F5/Refresh)
            if (Thread.currentThread().isInterrupted()) {
                System.out.println("🛑 [LOG] AS dừng để giải phóng CPU Pentium.");
                return new SimulationResponse(); 
            }

            // Kiến xây dựng giải pháp dùng DSATUR
            constructSolutionsDSATUR(ants, numNodes, pheromoneMatrix, alpha, validNodes, numColors, nodeDegrees);
            
            // Cập nhật Pheromone
            updatePheromones(numNodes, pheromoneMatrix, rho, ants, numColors);

            // Tìm con kiến tốt nhất vòng này
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

            // LƯU HISTORY (Chỉ lưu khi có cải tiến hoặc đồ thị nhỏ để tránh tràn RAM)
            if (foundNewBest || (numNodes < 200 && i % 10 == 0)) {
                double[] currentConf = calculateACOConfidence(pheromoneMatrix, bestSolution, numColors);
                history.add(new SimulationStep(i + 1, bestSolutionQuality, bestSolution.clone(), currentConf));
                if (history.size() > 30) history.remove(0); // Giới hạn history
            }
        }

        long durationMs = (System.nanoTime() - startNano) / 1_000_000;

        // --- 4. TẠO TRACE (CẮT TỈA CHO ĐỒ THỊ LỚN) ---
        List<NodeColorAction> trace = new ArrayList<>();
        // Chỉ gửi DetailedTrace nếu đồ thị < 300 nốt để tránh lỗi Broken Pipe (JSON quá to)
        if (numNodes < 300) {
            List<Integer> pathOrder = bestTourOrder.isEmpty() ? 
                    validNodes.stream().map(Node::getId).collect(Collectors.toList()) : bestTourOrder;
            int stepCount = 0;
            for (Integer nodeId : pathOrder) {
                trace.add(new NodeColorAction(nodeId, bestSolution[nodeId], stepCount++));
            }
        }

        return new SimulationResponse(
            bestSolutionQuality, 
            bestSolution, 
            calculateTotalConflicts(bestSolution, validNodes), 
            history, 
            trace, 
            durationMs, 
            validNodes
        );
    }

    // ✅ TỐI ƯU 1: Dùng mảng boolean thay vì HashSet để tìm màu khả dụng
    private List<Integer> findValidColorsOptimized(Ant ant, int nodeId, List<Node> nodes, int numColors) {
        boolean[] usedColors = new boolean[numColors];
        List<Integer> neighbors = nodes.get(nodeId).getNeighbors();
        int[] currentSolution = ant.getSolution();

        for (int neighborId : neighbors) {
            int color = currentSolution[neighborId];
            if (color != -1 && color < numColors) {
                usedColors[color] = true;
            }
        }

        List<Integer> validColors = new ArrayList<>();
        for (int c = 0; c < numColors; c++) {
            if (!usedColors[c]) validColors.add(c);
        }

        if (validColors.isEmpty()) {
            for (int i = 0; i < numColors; i++) validColors.add(i);
        }
        return validColors;
    }

    private void constructSolutionsDSATUR(Ant[] ants, int numNodes, double[][] matrix, double alpha, 
                                         List<Node> nodes, int numColors, int[] nodeDegrees) {
        for (Ant ant : ants) {
            ant.reset();
            Set<Integer> unvisited = new HashSet<>();
            for (int i = 0; i < numNodes; i++) unvisited.add(i);

            while (!unvisited.isEmpty()) {
                // Kiểm tra interrupt ngay trong vòng lặp con
                if (Thread.currentThread().isInterrupted()) return;

                int selectedNodeId = selectNextNodeDSATUR(ant, unvisited, nodes, nodeDegrees);
                unvisited.remove(selectedNodeId);

                int selectedColor = selectNextColor(ant, selectedNodeId, matrix, alpha, nodes, numColors);
                ant.setColor(selectedNodeId, selectedColor);
            }
        }
    }

    private int selectNextColor(Ant ant, int nodeId, double[][] matrix, double alpha, List<Node> nodes, int numColors) {
        List<Integer> validColors = findValidColorsOptimized(ant, nodeId, nodes, numColors);
        if (validColors.size() == 1) return validColors.get(0);

        double[] probs = new double[validColors.size()];
        double sum = 0.0;
        for (int i = 0; i < validColors.size(); i++) {
            int color = validColors.get(i);
            double score = Math.pow(matrix[nodeId][color], alpha);
            probs[i] = score;
            sum += score;
        }

        if (sum == 0) return validColors.get(new Random().nextInt(validColors.size()));
        
        double r = Math.random() * sum;
        double cumulative = 0.0;
        for (int i = 0; i < probs.length; i++) {
            cumulative += probs[i];
            if (cumulative >= r) return validColors.get(i);
        }
        return validColors.get(validColors.size() - 1);
    }

    private int selectNextNodeDSATUR(Ant ant, Set<Integer> unvisited, List<Node> nodes, int[] nodeDegrees) {
        int bestNodeId = -1;
        int maxSaturation = -1;
        int maxDegree = -1;
        List<Integer> candidates = new ArrayList<>();

        int[] sol = ant.getSolution();

        for (int nodeId : unvisited) {
            // TÍNH NHANH SATURATION
            Set<Integer> neighborColors = new HashSet<>();
            for (int neighbor : nodes.get(nodeId).getNeighbors()) {
                if (sol[neighbor] != -1) neighborColors.add(sol[neighbor]);
            }
            int saturation = neighborColors.size();
            int degree = nodeDegrees[nodeId];

            if (saturation > maxSaturation) {
                maxSaturation = saturation; maxDegree = degree;
                candidates.clear(); candidates.add(nodeId);
            } else if (saturation == maxSaturation) {
                if (degree > maxDegree) {
                    maxDegree = degree; candidates.clear(); candidates.add(nodeId);
                } else if (degree == maxDegree) {
                    candidates.add(nodeId);
                }
            }
        }
        return candidates.get(new Random().nextInt(candidates.size()));
    }

    private void updatePheromones(int numNodes, double[][] matrix, double rho, Ant[] ants, int numColors) {
        // Bay hơi
        double retainRate = 1.0 - rho;
        for (int i = 0; i < numNodes; i++) {
            for (int j = 0; j < numColors; j++) matrix[i][j] *= retainRate;
        }
        // Thêm mùi
        for (Ant ant : ants) {
            double deposit = 1.0 / ant.getNumberOfColorsUsed();
            int[] sol = ant.getSolution();
            for (int i = 0; i < numNodes; i++) {
                int c = sol[i];
                if (c != -1) matrix[i][c] += deposit;
            }
        }
    }

    private void initializePheromones(double[][] matrix, int numNodes, int numColors, double initialPheromone) {
        for (int i = 0; i < numNodes; i++) {
            for (int j = 0; j < numColors; j++) matrix[i][j] = initialPheromone;
        }
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