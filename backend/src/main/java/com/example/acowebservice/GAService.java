package com.example.acowebservice;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service 
public class GAService implements SolverStrategy {
    private final Random rand = new Random();

    @Override
    public SimulationResponse solve(SimulationRequest req) {
        long startNano = System.nanoTime(); 
        
        // --- 1. KIỂM TRA ĐẦU VÀO VÀ PHÒNG THỦ NULL ---
        List<Node> nodes = req.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            return new SimulationResponse(0, new int[0], 0, new ArrayList<>(), new ArrayList<>(), 0, new ArrayList<>());
        }

        List<Node> validNodes = nodes.stream()
                .filter(n -> n != null && n.getId() != null)
                .collect(Collectors.toList());
        
        int numNodes = validNodes.size();
        int popSize = (req.getNumAnts() != null) ? req.getNumAnts() : 50; 
        int maxGenerations = (req.getMaxIterations() != null) ? req.getMaxIterations() : 100;
        int numColors = (req.getNumColors() != null) ? req.getNumColors() : 10;
        double mutationRate = (req.getMutationRate() != null) ? req.getMutationRate() : 0.05;
        int tournamentSize = (req.getTournamentSize() != null) ? req.getTournamentSize() : 5;

        // --- 2. TIỀN XỬ LÝ DỮ LIỆU (TỐI ƯU CHO PENTIUM) ---
        // Chuyển cấu hình cạnh sang mảng int[] để truy xuất O(1)
        List<int[]> edgeList = new ArrayList<>();
        for (Node u : validNodes) {
            for (int vId : u.getNeighbors()) {
                if (u.getId() < vId) edgeList.add(new int[] { u.getId(), vId });
            }
        }
        int[][] edges = edgeList.toArray(new int[0][]);

        // Smart Ordering phục vụ cho Trace Replay
        List<Node> sortedNodes = new ArrayList<>(validNodes);
        sortedNodes.sort((n1, n2) -> n2.getNeighbors().size() - n1.getNeighbors().size());
        int[] walkingOrder = new int[numNodes];
        for (int i = 0; i < numNodes; i++) walkingOrder[i] = sortedNodes.get(i).getId();

        // --- 3. KHỞI TẠO QUẦN THỂ ---
        List<int[]> population = new ArrayList<>(popSize);
        for (int i = 0; i < popSize; i++) {
            int[] ind = new int[numNodes];
            for (int j = 0; j < numNodes; j++) ind[j] = rand.nextInt(numColors);
            population.add(ind);
        }

        int[] globalBestSolution = new int[numNodes];
        int globalBestConflicts = Integer.MAX_VALUE;
        List<SimulationStep> history = new ArrayList<>();

        // --- 4. VÒNG LẶP TIẾN HÓA ---
        for (int g = 0; g < maxGenerations; g++) {
            // ✅ CƠ CHẾ DỪNG KHẨN CẤP
            if (Thread.currentThread().isInterrupted()) {
                System.out.println("🛑 [LOG] GA dừng để giải phóng CPU Pentium.");
                return new SimulationResponse();
            }

            boolean foundNewBest = false;
            
            // Đánh giá Fitness
            for (int[] individual : population) {
                int currentConflicts = calculateConflicts(individual, edges);
                if (currentConflicts < globalBestConflicts) {
                    globalBestConflicts = currentConflicts;
                    System.arraycopy(individual, 0, globalBestSolution, 0, numNodes);
                    foundNewBest = true;
                }
            }

            // Ghi lịch sử (Tiết kiệm RAM: chỉ lưu khi đồ thị nhỏ hoặc có cải tiến lớn)
            if (foundNewBest || (numNodes < 300 && (g + 1) % 20 == 0)) {
                // Chỉ tính Confidence khi cần thiết để tiết kiệm CPU
                double[] confidence = (numNodes < 500) ? calculateGAConfidence(population, globalBestSolution) : new double[0];
                
                history.add(new SimulationStep(
                    g + 1, 
                    countUniqueColors(globalBestSolution), 
                    globalBestSolution.clone(), 
                    confidence
                ));
                if (history.size() > 30) history.remove(0); 
            }

            // Nếu đã tìm thấy lời giải hoàn hảo (0 xung đột), dừng sớm để tiết kiệm tài nguyên
            if (globalBestConflicts == 0) break;

            // Tạo thế hệ mới
            List<int[]> nextPop = new ArrayList<>(popSize);
            nextPop.add(globalBestSolution.clone()); // Elitism (Giữ lại thằng giỏi nhất)
            
            while (nextPop.size() < popSize) {
                int[] p1 = tournamentSelection(population, edges, tournamentSize);
                int[] p2 = tournamentSelection(population, edges, tournamentSize);
                
                int[] child = crossover(p1, p2);
                mutate(child, numColors, mutationRate);
                nextPop.add(child);
            }
            population = nextPop;
        }

        // --- 5. ĐO THỜI GIAN & TRACE ---
        long durationMs = (System.nanoTime() - startNano) / 1_000_000;

        List<NodeColorAction> trace = new ArrayList<>();
        // Chỉ gửi Trace nếu đồ thị nhỏ (<300) để tránh Broken Pipe (JSON quá to)
        if (numNodes < 300) {
            for (int i = 0; i < numNodes; i++) {
                int nodeId = walkingOrder[i];
                trace.add(new NodeColorAction(nodeId, globalBestSolution[nodeId], i));
            }
        }

        return new SimulationResponse(
            countUniqueColors(globalBestSolution), 
            globalBestSolution, 
            globalBestConflicts, 
            history, 
            trace, 
            durationMs,
            validNodes 
        );
    }

    // --- CÁC HÀM TRỢ GIÚP TỐI ƯU ---

    private int calculateConflicts(int[] solution, int[][] edges) {
        int conflicts = 0;
        for (int[] edge : edges) {
            if (solution[edge[0]] == solution[edge[1]]) {
                conflicts++;
            }
        }
        return conflicts;
    }

    private int[] tournamentSelection(List<int[]> population, int[][] edges, int size) {
        int[] best = population.get(rand.nextInt(population.size()));
        int bestFit = calculateConflicts(best, edges);
        
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
        int cp = rand.nextInt(n); // Crossover point
        System.arraycopy(p1, 0, child, 0, cp);
        System.arraycopy(p2, cp, child, cp, n - cp);
        return child;
    }

    private void mutate(int[] ind, int colors, double rate) {
        for (int i = 0; i < ind.length; i++) {
            if (rand.nextDouble() < rate) {
                ind[i] = rand.nextInt(colors);
            }
        }
    }

    private double[] calculateGAConfidence(List<int[]> population, int[] bestSolution) {
        int n = bestSolution.length;
        double invSize = 1.0 / population.size();
        double[] conf = new double[n];
        for (int i = 0; i < n; i++) {
            int bestC = bestSolution[i];
            int count = 0;
            for (int[] ind : population) {
                if (ind[i] == bestC) count++;
            }
            conf[i] = count * invSize;
        }
        return conf;
    }

    private int countUniqueColors(int[] sol) {
        Set<Integer> set = new HashSet<>();
        for (int c : sol) set.add(c);
        return set.size();
    }
}