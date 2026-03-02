package com.example.acowebservice;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service 
public class GAService implements SolverStrategy {
    private final Random rand = new Random();

    @Override
    public SimulationResponse solve(SimulationRequest req) {
        // --- 1. BẮT ĐẦU BẤM GIỜ NANO ---
        long startNano = System.nanoTime(); 
        
        // --- 2. KIỂM TRA ĐẦU VÀO VÀ PHÒNG THỦ NULL ---
        List<Node> nodes = req.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            return new SimulationResponse(0, new int[0], 0, new ArrayList<>(), new ArrayList<>(), 0, new ArrayList<>());
        }

        // Lọc node để đảm bảo ID không null (Bảo vệ tính ổn định)
        List<Node> validNodes = nodes.stream()
                .filter(n -> n != null && n.getId() != null)
                .collect(Collectors.toList());
        
        int numNodes = validNodes.size();
        
        // Tham số an toàn: Ưu tiên giá trị user gửi, nếu thiếu lấy giá trị mặc định tối ưu
        int popSize = (req.getNumAnts() != null) ? req.getNumAnts() : 50; 
        int maxGenerations = (req.getMaxIterations() != null) ? req.getMaxIterations() : 100;
        int numColors = (req.getNumColors() != null) ? req.getNumColors() : 10;
        double mutationRate = (req.getMutationRate() != null) ? req.getMutationRate() : 0.05;
        int tournamentSize = (req.getTournamentSize() != null) ? req.getTournamentSize() : 5;

        // --- 3. TIỀN XỬ LÝ DỮ LIỆU (GIẢM TẢI CPU PENTIUM) ---
        // Biến danh sách Neighbors phức tạp thành mảng 2 chiều int[][] để duyệt O(1)
        List<int[]> edgeList = new ArrayList<>();
        for (Node u : validNodes) {
            for (int vId : u.getNeighbors()) {
                // Đồ thị vô hướng nên chỉ lấy 1 chiều u < v để tính fitness nhanh gấp đôi
                if (u.getId() < vId) {
                    edgeList.add(new int[] { u.getId(), vId });
                }
            }
        }
        int[][] edges = edgeList.toArray(new int[0][]);

        // Xếp hạng node theo bậc đỉnh (phục vụ Replay Animation)
        List<Node> sortedNodes = new ArrayList<>(validNodes);
        sortedNodes.sort((n1, n2) -> n2.getNeighbors().size() - n1.getNeighbors().size());

        // --- 4. KHỞI TẠO QUẦN THỂ BAN ĐẦU ---
        List<int[]> population = new ArrayList<>(popSize);
        for (int i = 0; i < popSize; i++) {
            int[] individual = new int[numNodes];
            for (int j = 0; j < numNodes; j++) {
                individual[j] = rand.nextInt(numColors);
            }
            population.add(individual);
        }

        int[] globalBestSolution = new int[numNodes];
        Arrays.fill(globalBestSolution, -1);
        int globalBestConflicts = Integer.MAX_VALUE;
        List<SimulationStep> history = new ArrayList<>();

        // --- 5. VÒNG LẶP TIẾN HÓA (GENERATIONS) ---
        for (int g = 0; g < maxGenerations; g++) {
            // ✅ CƠ CHẾ DỪNG KHẨN CẤP: Giải phóng Pentium ngay khi Refresh web
            if (Thread.currentThread().isInterrupted()) {
                System.out.println("🛑 [LOG] GAService đã dừng khẩn cấp.");
                return new SimulationResponse();
            }

            boolean foundNewBest = false;
            
            // Đánh giá toàn bộ quần thể (Tìm ra King/Queen mới)
            for (int[] individual : population) {
                int currentConflicts = calculateConflicts(individual, edges);
                if (currentConflicts < globalBestConflicts) {
                    globalBestConflicts = currentConflicts;
                    System.arraycopy(individual, 0, globalBestSolution, 0, numNodes);
                    foundNewBest = true;
                }
            }

            // Lưu lịch sử tiến hóa (Cắt tỉa để tránh lỗi treo trình duyệt)
            if (foundNewBest || (numNodes < 300 && (g + 1) % 20 == 0)) {
                // Chỉ tính Confidence cho đồ thị nhỏ (<500) vì phép tính này tốn O(N*Pop)
                double[] confidence = (numNodes < 500) ? calculateGAConfidence(population, globalBestSolution) : new double[0];
                
                history.add(new SimulationStep(
                    g + 1, 
                    countUniqueColors(globalBestSolution), 
                    globalBestSolution.clone(), 
                    confidence
                ));
                // Khống chế History tối đa 30 mốc (Linh hồn của "Pruning")
                if (history.size() > 30) history.remove(0); 
            }

            // Nếu đã tối ưu tuyệt đối (không còn trùng màu), dừng luôn để bảo vệ CPU
            if (globalBestConflicts == 0) break;

            // --- BƯỚC SANG THẾ HỆ TIẾP THEO ---
            List<int[]> nextPop = new ArrayList<>(popSize);
            nextPop.add(globalBestSolution.clone()); // Elitism: Luôn giữ lại "Nhà vô địch"
            
            while (nextPop.size() < popSize) {
                // Chọn lọc tự nhiên qua Đấu trường (Tournament)
                int[] p1 = tournamentSelection(population, edges, tournamentSize);
                int[] p2 = tournamentSelection(population, edges, tournamentSize);
                
                // Lai ghép (Crossover) và Đột biến (Mutation)
                int[] child = crossover(p1, p2);
                mutate(child, numColors, mutationRate);
                nextPop.add(child);
            }
            population = nextPop;
        }

        // --- 6. KẾT THÚC VÀ ĐÓNG GÓI ---
        long endNano = System.nanoTime();
        long durationMs = (endNano - startNano) / 1_000_000;

        // Xử lý DetailedTrace (Dữ liệu vẽ phim Kiến bò)
        List<NodeColorAction> trace = new ArrayList<>();
        // Nếu đồ thị > 300 nốt, không gửi trace để tiết kiệm băng thông (Fix Broken Pipe)
        if (numNodes < 300) {
            for (int i = 0; i < numNodes; i++) {
                int nodeId = sortedNodes.get(i).getId();
                trace.add(new NodeColorAction(nodeId, globalBestSolution[nodeId], i));
            }
        }

        System.out.println(">>> [GA] Completed: " + durationMs + " ms | Best Conflicts: " + globalBestConflicts);

        return new SimulationResponse(
            countUniqueColors(globalBestSolution), // bestQuality
            globalBestSolution,                    // bestSolution
            globalBestConflicts,                  // conflicts
            history,                               // history
            trace,                                 // detailedTrace
            durationMs,                            // executionTimeMs
            validNodes                             // 🔴 TRUYỀN NODES VỀ ĐỂ FRONTEND VẼ DÂY NỐI
        );
    }

    // --- CÁC HÀM XỬ LÝ LÕI TỐI ƯU ---

    private int calculateConflicts(int[] solution, int[][] edges) {
        int conflicts = 0;
        // Duyệt theo mảng phẳng nhanh hơn Duyệt Object rất nhiều
        for (int i = 0; i < edges.length; i++) {
            if (solution[edges[i][0]] == solution[edges[i][1]]) {
                conflicts++;
            }
        }
        return conflicts;
    }

    private int[] tournamentSelection(List<int[]> population, int[][] edges, int size) {
        // Lấy 1 thằng ngẫu nhiên làm ứng viên ban đầu
        int[] best = population.get(rand.nextInt(population.size()));
        int bestFit = calculateConflicts(best, edges);
        
        // Cho chiến đấu với (size - 1) thằng khác
        for (int i = 0; i < size - 1; i++) {
            int[] contender = population.get(rand.nextInt(population.size()));
            int fit = calculateConflicts(contender, edges);
            if (fit < bestFit) {
                best = contender;
                bestFit = fit;
            }
        }
        return best;
    }

    private int[] crossover(int[] p1, int[] p2) {
        int n = p1.length;
        int[] child = new int[n];
        int crossoverPoint = rand.nextInt(n); // Lai tại 1 điểm
        
        // Dùng System.arraycopy cho hiệu năng cao nhất
        System.arraycopy(p1, 0, child, 0, crossoverPoint);
        System.arraycopy(p2, crossoverPoint, child, crossoverPoint, n - crossoverPoint);
        return child;
    }

    private void mutate(int[] ind, int numColors, double rate) {
        for (int i = 0; i < ind.length; i++) {
            if (rand.nextDouble() < rate) {
                ind[i] = rand.nextInt(numColors); // Đột biến gen thành màu khác ngẫu nhiên
            }
        }
    }

    private double[] calculateGAConfidence(List<int[]> population, int[] bestSolution) {
        int n = bestSolution.length;
        int popSize = population.size();
        double[] confidence = new double[n];
        for (int i = 0; i < n; i++) {
            int winningColor = bestSolution[i];
            int agreementCount = 0;
            for (int[] individual : population) {
                if (individual[i] == winningColor) agreementCount++;
            }
            confidence[i] = (double) agreementCount / popSize;
        }
        return confidence;
    }

    private int countUniqueColors(int[] solution) {
        Set<Integer> uniqueColors = new HashSet<>();
        for (int color : solution) {
            if (color != -1) uniqueColors.add(color);
        }
        return uniqueColors.size();
    }
}