package com.example.acowebservice;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class MMASService implements SolverStrategy {

    private final Random random = new Random();

    @Override
    public SimulationResponse solve(SimulationRequest req) {
        // 1. BẤM GIỜ NANO & KIỂM TRA ĐẦU VÀO
        long startNano = System.nanoTime();
        
        List<Node> nodes = req.getNodes();
        if (nodes == null || nodes.isEmpty()) return new SimulationResponse();

        // Lọc node hợp lệ
        List<Node> validNodes = nodes.stream()
                .filter(n -> n != null && n.getId() != null)
                .collect(Collectors.toList());
        
        int numNodes = validNodes.size();
        
        // Lấy tham số an toàn (phòng thủ Null)
        int numAnts = (req.getNumAnts() != null) ? req.getNumAnts() : 30;
        int maxIterations = (req.getMaxIterations() != null) ? req.getMaxIterations() : 100;
        int numColors = (req.getNumColors() != null) ? req.getNumColors() : 20;
        double alpha = (req.getAlpha() != null) ? req.getAlpha() : 1.0;
        double beta = (req.getBeta() != null) ? req.getBeta() : 2.0;
        double rho = (req.getEvaporation() != null) ? req.getEvaporation() : 0.1;

        // 2. TIỀN XỬ LÝ (TỐI ƯU CHO PENTIUM)
        // Cache bậc đỉnh để DSATUR chạy nhanh hơn
        int[] nodeDegrees = new int[numNodes];
        for (int i = 0; i < numNodes; i++) {
            nodeDegrees[i] = validNodes.get(i).getNeighbors().size();
        }

        // Chạy tham lam nhanh để tính tauMax
        int greedyQuality = runSimpleGreedyInner(validNodes, nodeDegrees);
        double tauMax = 1.0 / (rho * greedyQuality);
        double tauMin = tauMax / (2.0 * numNodes);
        
        double[][] pheromoneMatrix = new double[numNodes][numColors];
        for(int i = 0; i < numNodes; i++) Arrays.fill(pheromoneMatrix[i], tauMax);

        // Khởi tạo bộ nhớ
        int[] globalBestSolution = new int[numNodes];
        Arrays.fill(globalBestSolution, -1);
        int globalBestQuality = Integer.MAX_VALUE;
        List<Integer> globalBestTourOrder = new ArrayList<>();
        List<SimulationStep> history = new ArrayList<>();

        Ant[] ants = new Ant[numAnts];
        for (int i = 0; i < numAnts; i++) ants[i] = new Ant(numNodes);

        // 3. VÒNG LẶP CHÍNH (VÙNG TỐI ƯU HIỆU NĂNG)
        for (int i = 0; i < maxIterations; i++) {
            // ✅ CƠ CHẾ TỰ HỦY KHI REFRESH WEB
            if (Thread.currentThread().isInterrupted()) {
                System.out.println("🛑 [LOG] MMAS dừng khẩn cấp giải phóng CPU.");
                return new SimulationResponse();
            }

            // Xây dựng lời giải
            constructSolutionsDSATUR(ants, validNodes, numNodes, numColors, pheromoneMatrix, alpha, beta, nodeDegrees);

            // Tìm kiến tốt nhất vòng này
            Ant iterBestAnt = ants[0];
            int iterBestQuality = iterBestAnt.getNumberOfColorsUsed();
            for (Ant ant : ants) {
                if (ant.getNumberOfColorsUsed() < iterBestQuality) {
                    iterBestQuality = ant.getNumberOfColorsUsed();
                    iterBestAnt = ant;
                }
            }

            // Cập nhật kỷ lục Global (Cơ chế MMAS)
            boolean foundNewBest = false;
            if (iterBestQuality < globalBestQuality) {
                globalBestQuality = iterBestQuality;
                System.arraycopy(iterBestAnt.getSolution(), 0, globalBestSolution, 0, numNodes);
                globalBestTourOrder = new ArrayList<>(iterBestAnt.getTourOrder());
                foundNewBest = true;
                
                // Reset biên Pheromone theo chất lượng mới
                tauMax = 1.0 / (rho * globalBestQuality);
                tauMin = tauMax / (2.0 * numNodes);
            }

            // Bay hơi và Cập nhật Pheromone (Chỉ Elite được update)
            updatePheromonesMMAS(pheromoneMatrix, globalBestSolution, globalBestQuality, rho, tauMin, tauMax);

            // Lưu lịch sử (Cắt tỉa để tránh tràn RAM 8GB)
            if (foundNewBest || (numNodes < 300 && i % 20 == 0)) {
                double[] conf = (numNodes < 500) ? calculateConfidence(pheromoneMatrix, globalBestSolution, numColors) : new double[0];
                history.add(new SimulationStep(i + 1, globalBestQuality, globalBestSolution.clone(), conf));
                if (history.size() > 30) history.remove(0);
            }
        }

        long durationMs = (System.nanoTime() - startNano) / 1_000_000;

        // 4. ĐÓNG GÓI KẾT QUẢ (CẮT TỈA JSON CHO ĐỒ THỊ LỚN)
        List<NodeColorAction> trace = new ArrayList<>();
        // Nếu đồ thị > 300 nốt, bỏ qua detailedTrace để tránh lỗi Broken Pipe (JSON quá nặng)
        if (numNodes < 300) {
            List<Integer> path = globalBestTourOrder.isEmpty() ? 
                    validNodes.stream().map(Node::getId).collect(Collectors.toList()) : globalBestTourOrder;
            int step = 0;
            for (int id : path) trace.add(new NodeColorAction(id, globalBestSolution[id], step++));
        }

        return new SimulationResponse(
            globalBestQuality, 
            globalBestSolution, 
            calculateTotalConflicts(globalBestSolution, validNodes), 
            history, 
            trace, 
            durationMs, 
            validNodes
        );
    }

    // ==================== CÁC HÀM TỐI ƯU CỐT LÕI ====================

    private void constructSolutionsDSATUR(Ant[] ants, List<Node> nodes, int numNodes, int numColors, 
                                        double[][] matrix, double alpha, double beta, int[] nodeDegrees) {
        for (Ant ant : ants) {
            ant.reset();
            Set<Integer> unvisited = new HashSet<>();
            for (int i = 0; i < numNodes; i++) unvisited.add(i);

            while (!unvisited.isEmpty()) {
                if (Thread.currentThread().isInterrupted()) return;

                int nodeId = selectNextNodeDSATUR(ant, unvisited, nodes, nodeDegrees);
                unvisited.remove(nodeId);
                
                int color = selectNextColorMMAS(ant, nodeId, matrix, nodes, numColors, alpha, beta);
                ant.setColor(nodeId, color);
            }
        }
    }

    private int selectNextColorMMAS(Ant ant, int nodeId, double[][] matrix, List<Node> nodes, 
                                   int numColors, double alpha, double beta) {
        // ✅ TỐI ƯU: Sử dụng mảng boolean thay cho contains()
        boolean[] used = new boolean[numColors];
        for (int neighborId : nodes.get(nodeId).getNeighbors()) {
            int c = ant.getSolution()[neighborId];
            if (c != -1 && c < numColors) used[c] = true;
        }

        List<Integer> valid = new ArrayList<>();
        for (int i = 0; i < numColors; i++) if (!used[i]) valid.add(i);
        
        if (valid.isEmpty()) return random.nextInt(numColors); // Fallback nếu kẹt màu
        if (valid.size() == 1) return valid.get(0);

        // Tính xác suất
        double[] probs = new double[valid.size()];
        double sum = 0;
        double heuristic = nodes.get(nodeId).getNeighbors().size() + 0.1;

        for (int i = 0; i < valid.size(); i++) {
            int c = valid.get(i);
            probs[i] = Math.pow(matrix[nodeId][c], alpha) * Math.pow(heuristic, beta);
            sum += probs[i];
        }

        double r = random.nextDouble() * sum;
        double current = 0;
        for (int i = 0; i < probs.length; i++) {
            current += probs[i];
            if (current >= r) return valid.get(i);
        }
        return valid.get(valid.size() - 1);
    }

    private void updatePheromonesMMAS(double[][] matrix, int[] bestSol, int bestQuality, 
                                     double rho, double tMin, double tMax) {
        double retain = 1.0 - rho;
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                matrix[i][j] *= retain;
                if (matrix[i][j] < tMin) matrix[i][j] = tMin;
            }
        }
        double deposit = 1.0 / bestQuality;
        for (int i = 0; i < bestSol.length; i++) {
            int color = bestSol[i];
            if (color != -1) {
                matrix[i][color] += deposit;
                if (matrix[i][color] > tMax) matrix[i][color] = tMax;
            }
        }
    }

    private int selectNextNodeDSATUR(Ant ant, Set<Integer> unvisited, List<Node> nodes, int[] nodeDegrees) {
        int bestId = -1; int maxSat = -1; int maxDeg = -1;
        List<Integer> candidates = new ArrayList<>();
        int[] sol = ant.getSolution();

        for (int id : unvisited) {
            Set<Integer> nColors = new HashSet<>();
            for (int neigh : nodes.get(id).getNeighbors()) {
                if (sol[neigh] != -1) nColors.add(sol[neigh]);
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
        return candidates.get(random.nextInt(candidates.size()));
    }

    private int runSimpleGreedyInner(List<Node> nodes, int[] degrees) {
        int num = nodes.size();
        int[] sol = new int[num];
        Arrays.fill(sol, -1);
        
        List<Integer> order = new ArrayList<>();
        for(int i=0; i<num; i++) order.add(i);
        order.sort((a, b) -> degrees[b] - degrees[a]);

        for (int id : order) {
            boolean[] used = new boolean[num + 1];
            for (int neigh : nodes.get(id).getNeighbors()) {
                if (sol[neigh] != -1) used[sol[neigh]] = true;
            }
            int color = 0;
            while (used[color]) color++;
            sol[id] = color;
        }
        Set<Integer> unique = new HashSet<>();
        for (int c : sol) unique.add(c);
        return unique.size();
    }

    private double[] calculateConfidence(double[][] matrix, int[] sol, int numColors) {
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