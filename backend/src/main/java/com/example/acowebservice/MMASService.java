package com.example.acowebservice;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class MMASService implements SolverStrategy {

    private final Random random = new Random();

    @Override
    public SimulationResponse solve(SimulationRequest req) {
        // 1. BẤM GIỜ NANO & KIỂM TRA ĐẦU VÀO (PHÒNG THỦ NULL)
        long startNano = System.nanoTime();
        
        List<Node> nodes = req.getNodes();
        if (nodes == null || nodes.isEmpty()) return new SimulationResponse();

        // Lọc nốt để đảm bảo tính ổn định của hệ thống
        List<Node> validNodes = nodes.stream()
                .filter(n -> n != null && n.getId() != null)
                .collect(Collectors.toList());
        
        int numNodes = validNodes.size();
        
        // Gán giá trị mặc định nếu tham số bị null (Tránh lỗi 400/NullPointer)
        int numAnts = (req.getNumAnts() != null) ? req.getNumAnts() : 30;
        int maxIterations = (req.getMaxIterations() != null) ? req.getMaxIterations() : 100;
        int numColors = (req.getNumColors() != null) ? req.getNumColors() : 20;
        double alpha = (req.getAlpha() != null) ? req.getAlpha() : 1.0;
        double beta = (req.getBeta() != null) ? req.getBeta() : 2.0;
        double rho = (req.getEvaporation() != null) ? req.getEvaporation() : 0.1;

        // 2. TIỀN XỬ LÝ (TỐI ƯU CHO PENTIUM)
        // Cache bậc đỉnh để thuật toán DSATUR chạy thần tốc
        int[] nodeDegrees = new int[numNodes];
        for (int i = 0; i < numNodes; i++) {
            nodeDegrees[i] = validNodes.get(i).getNeighbors().size();
        }

        // Chạy tham lam nhanh để xác định giới hạn mùi Pheromone (tauMax/tauMin)
        int greedyQuality = runSimpleGreedyFast(validNodes, nodeDegrees, numColors);
        double tauMax = 1.0 / (rho * greedyQuality);
        double tauMin = tauMax / (2.0 * numNodes);
        
        double[][] pheromoneMatrix = new double[numNodes][numColors];
        for(int i = 0; i < numNodes; i++) Arrays.fill(pheromoneMatrix[i], tauMax);

        // Khởi tạo bộ nhớ cho hạm đội kiến
        int[] globalBestSolution = new int[numNodes];
        Arrays.fill(globalBestSolution, -1);
        int globalBestQuality = Integer.MAX_VALUE;  // Mục tiêu phụ: Ít màu nhất
        int globalBestConflicts = Integer.MAX_VALUE; // Mục tiêu chính: 0 xung đột
        
        List<Integer> globalBestTourOrder = new ArrayList<>();
        List<SimulationStep> history = new ArrayList<>();

        Ant[] ants = new Ant[numAnts];
        for (int i = 0; i < numAnts; i++) ants[i] = new Ant(numNodes);

        // 3. VÒNG LẶP TIẾN HÓA CHÍNH (VÙNG TỐI ƯU HIỆU NĂNG)
        for (int i = 0; i < maxIterations; i++) {
            // ✅ CƠ CHẾ TỰ HỦY: Giải phóng CPU ngay khi Refresh trình duyệt
            if (Thread.currentThread().isInterrupted()) {
                System.out.println("🛑 [LOG] MMAS dừng để cứu hộ CPU Server.");
                return new SimulationResponse();
            }

            // Mỗi con kiến xây dựng một giải pháp dựa trên mùi hương và luật DSATUR
            for (Ant ant : ants) {
                ant.reset();
                Set<Integer> unvisited = new HashSet<>();
                for (int n = 0; n < numNodes; n++) unvisited.add(n);

                while (!unvisited.isEmpty()) {
                    if (Thread.currentThread().isInterrupted()) return new SimulationResponse();
                    
                    int nodeId = selectNextNodeDSATUR(ant, unvisited, validNodes, nodeDegrees);
                    unvisited.remove(nodeId);
                    
                    // Sử dụng mảng Boolean O(N) để chọn màu cực nhanh
                    int color = selectNextColorMMASFast(ant, nodeId, pheromoneMatrix, validNodes, numColors, alpha, beta);
                    ant.setColor(nodeId, color);
                }
            }

            // ✅ CHỌN KIẾN TỐT NHẤT VÒNG (Tìm thằng ít lỗi nhất)
            Ant iterBestAnt = null;
            int iterMinConflicts = Integer.MAX_VALUE;
            int iterMinColors = Integer.MAX_VALUE;

            for (Ant ant : ants) {
                int curConf = calculateTotalConflicts(ant.getSolution(), validNodes);
                int curCols = ant.getNumberOfColorsUsed();

                // Logic so sánh kép: Ít lỗi trước -> Ít màu sau
                if (iterBestAnt == null || curConf < iterMinConflicts || (curConf == iterMinConflicts && curCols < iterMinColors)) {
                    iterBestAnt = ant;
                    iterMinConflicts = curConf;
                    iterMinColors = curCols;
                }
            }

            // Cập nhật Kỷ lục Thế giới (Global Best)
            boolean foundNewBest = false;
            if (iterMinConflicts < globalBestConflicts || (iterMinConflicts == globalBestConflicts && iterMinColors < globalBestQuality)) {
                globalBestConflicts = iterMinConflicts;
                globalBestQuality = iterMinColors;
                System.arraycopy(iterBestAnt.getSolution(), 0, globalBestSolution, 0, numNodes);
                globalBestTourOrder = new ArrayList<>(iterBestAnt.getTourOrder());
                foundNewBest = true;
                
                // Cập nhật lại biên mùi dựa trên kết quả mới xịn hơn
                tauMax = 1.0 / (rho * (globalBestQuality + globalBestConflicts));
                tauMin = tauMax / (2.0 * numNodes);
            }

            // Bay hơi mùi cũ và củng cố mùi trên đường của "Nhà vô địch"
            updatePheromonesMMAS(pheromoneMatrix, globalBestSolution, globalBestQuality, globalBestConflicts, rho, tauMin, tauMax);

            // Ghi lại lịch sử (Lưới lọc Pruning cho Pentium-kun)
            if (foundNewBest || (numNodes < 300 && i % 20 == 0)) {
                double[] conf = (numNodes < 400) ? calculateConfidence(pheromoneMatrix, globalBestSolution, numColors) : new double[0];
                history.add(new SimulationStep(i + 1, globalBestQuality, globalBestSolution.clone(), conf));
                if (history.size() > 25) history.remove(0); // Chỉ giữ lại các frame quý giá
            }
            
            // Nếu đã tìm thấy 0 lỗi và số màu rất nhỏ, có thể dừng sớm để nghỉ ngơi
            if (globalBestConflicts == 0 && globalBestQuality <= 3) break;
        }

        // 4. KẾT THÚC VÀ ĐÓNG GÓI DỮ LIỆU
        long endTime = System.nanoTime();
        long durationMs = (endTime - startNano) / 1_000_000;

        // Cắt tỉa trace nếu nốt quá nhiều (Tránh Broken Pipe khi gửi JSON khổng lồ)
        List<NodeColorAction> trace = new ArrayList<>();
        if (numNodes < 300) {
            List<Integer> path = globalBestTourOrder.isEmpty() ? 
                    validNodes.stream().map(Node::getId).collect(Collectors.toList()) : globalBestTourOrder;
            int step = 0;
            for (int id : path) trace.add(new NodeColorAction(id, globalBestSolution[id], step++));
        }

        System.out.println(">>> [MMAS] Finished in: " + durationMs + "ms | Colors: " + globalBestQuality + " | Conflicts: " + globalBestConflicts);

        return new SimulationResponse(
            globalBestQuality,               // bestQuality
            globalBestSolution,              // bestSolution
            globalBestConflicts,             // conflicts
            history,                         // history
            trace,                           // detailedTrace
            durationMs,                      // executionTimeMs
            validNodes                       // 🔴 TRẢ VỀ NODES ĐỂ FRONTEND HIỆN MAP
        );
    }

    // ==================== CÁC HÀM TIỆN ÍCH TỐI ƯU ====================

    private int selectNextColorMMASFast(Ant ant, int nodeId, double[][] matrix, List<Node> nodes, int numColors, double alpha, double beta) {
        // ✅ TỐI ƯU $O(N)$: Mảng boolean đánh dấu màu đã dùng
        boolean[] isTaken = new boolean[numColors];
        for (int neigh : nodes.get(nodeId).getNeighbors()) {
            int color = ant.getSolution()[neigh];
            if (color != -1 && color < numColors) isTaken[color] = true;
        }

        List<Integer> valid = new ArrayList<>();
        for (int i = 0; i < numColors; i++) if (!isTaken[i]) valid.add(i);
        
        // Nếu không có màu nào trống (Ràng buộc quá chặt), chấp nhận chọn bừa và sinh conflict
        if (valid.isEmpty()) return random.nextInt(numColors);
        if (valid.size() == 1) return valid.get(0);

        // Roulette Wheel Selection (Tung xúc xắc theo nồng độ mùi)
        double[] probs = new double[valid.size()];
        double sum = 0;
        double heuristic = nodes.get(nodeId).getNeighbors().size() + 0.1;

        for (int i = 0; i < valid.size(); i++) {
            probs[i] = Math.pow(matrix[nodeId][valid.get(i)], alpha) * Math.pow(heuristic, beta);
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

    private void updatePheromonesMMAS(double[][] matrix, int[] bestSol, int bestQ, int bestC, double rho, double tMin, double tMax) {
        double retain = 1.0 - rho;
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                matrix[i][j] *= retain;
                if (matrix[i][j] < tMin) matrix[i][j] = tMin;
            }
        }
        // Thêm mùi dựa trên chất lượng (càng ít lỗi, ít màu thì mùi càng đậm)
        double deposit = 1.0 / (double)(bestQ + bestC + 1);
        for (int i = 0; i < bestSol.length; i++) {
            int color = bestSol[i];
            if (color != -1) {
                matrix[i][color] += deposit;
                if (matrix[i][color] > tMax) matrix[i][color] = tMax;
            }
        }
    }

    private int selectNextNodeDSATUR(Ant ant, Set<Integer> unvisited, List<Node> nodes, int[] degrees) {
        int bestId = -1; int maxSat = -1;
        List<Integer> candidates = new ArrayList<>();
        int[] sol = ant.getSolution();

        for (int id : unvisited) {
            Set<Integer> nColors = new HashSet<>();
            for (int neigh : nodes.get(id).getNeighbors()) {
                if (sol[neigh] != -1) nColors.add(sol[neigh]);
            }
            int sat = nColors.size();
            if (sat > maxSat) {
                maxSat = sat; candidates.clear(); candidates.add(id);
            } else if (sat == maxSat) {
                candidates.add(id);
            }
        }
        // Trả về ngẫu nhiên giữa các thằng cùng độ Saturation cao để tăng tính thám hiểm
        return candidates.get(random.nextInt(candidates.size()));
    }

    private int runSimpleGreedyFast(List<Node> nodes, int[] degrees, int limit) {
        int num = nodes.size();
        int[] sol = new int[num]; Arrays.fill(sol, -1);
        List<Integer> order = new ArrayList<>();
        for(int i=0; i<num; i++) order.add(i);
        order.sort((a, b) -> degrees[b] - degrees[a]);

        for (int id : order) {
            boolean[] used = new boolean[num + 1];
            for (int n : nodes.get(id).getNeighbors()) if(sol[n] != -1) used[sol[n]] = true;
            int c = 0;
            while(used[c]) c++;
            sol[id] = c;
        }
        Set<Integer> unique = new HashSet<>();
        for (int c : sol) unique.add(c);
        return unique.size();
    }

    private double[] calculateConfidence(double[][] matrix, int[] sol, int numColors) {
        double[] conf = new double[sol.length];
        for (int i = 0; i < sol.length; i++) {
            int c = sol[i];
            if (c == -1) continue;
            double sum = 0;
            for (int j = 0; j < numColors; j++) sum += matrix[i][j];
            conf[i] = (sum > 0) ? (matrix[i][c] / sum) : 0.0;
        }
        return conf;
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
}