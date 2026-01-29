package com.example.acowebservice;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class MMASService implements SolverStrategy {

    private final Random random = new Random();

    @Override
    public SimulationResponse solve(SimulationRequest req) {
    	long startNano = System.nanoTime(); 
	    
        List<Node> nodes = req.getNodes();
        int numAnts = req.getNumAnts();
        int maxIterations = req.getMaxIterations();
        int numColors = req.getNumColors();
        double alpha = req.getAlpha();
        double beta = req.getBeta();
        double rho = req.getEvaporation();
        List<Node> validNodes = req.getNodes().stream()
                .filter(n -> n != null && n.getId() != null)
                .collect(Collectors.toList());
            
            if (validNodes.isEmpty()) {
                System.out.println(">>> [CẢNH BÁO] Không có Node nào hợp lệ!");
                // Trả về rỗng thay vì ném ra Exception làm sập server
                return new SimulationResponse(0, new int[0], 0, new ArrayList<>(), new ArrayList<>(), 0,
                	    validNodes );
            }

            int numNodes = validNodes.size();

        // 1. CHẠY THAM LAM ĐỂ TÍNH BIÊN PHEROMONE BAN ĐẦU
        int greedyQuality = runSimpleGreedyInner(nodes);
        double tauMax = 1.0 / (rho * greedyQuality);
        double tauMin = tauMax / (2.0 * numNodes);
        double[][] pheromoneMatrix = new double[numNodes][numColors];
        
        initializePheromones(pheromoneMatrix, numNodes, numColors, tauMax);

        // BỘ NHỚ GLOBAL BEST
        int[] globalBestSolution = new int[numNodes];
        Arrays.fill(globalBestSolution, -1);
        int globalBestQuality = Integer.MAX_VALUE;
        List<Integer> globalBestTourOrder = new ArrayList<>();
        List<SimulationStep> history = new ArrayList<>();

        Ant[] ants = new Ant[numAnts];
        for (int i = 0; i < numAnts; i++) ants[i] = new Ant(numNodes);

        // --- VÒNG LẶP CHÍNH ---
        for (int i = 0; i < maxIterations; i++) {
        	if (Thread.currentThread().isInterrupted()) {
                System.out.println("🛑 [LOG] MMAS bị buộc dừng để giải phóng CPU.");
                // Trả về kết quả rỗng hoặc null thay vì chạy tiếp
                return new SimulationResponse(0, new int[0], 0, new ArrayList<>(), new ArrayList<>(), 0, new ArrayList<>());
            }
            // A. Xây dựng giải pháp
            constructSolutionsDSATUR(ants, nodes, numNodes, numColors, pheromoneMatrix, alpha, beta);

            // B. Tìm kiến tốt nhất trong vòng (Iteration Best)
            Ant iterBestAnt = ants[0];
            int iterBestQuality = iterBestAnt.getNumberOfColorsUsed();

            for (Ant ant : ants) {
                if (ant.getNumberOfColorsUsed() < iterBestQuality) {
                    iterBestQuality = ant.getNumberOfColorsUsed();
                    iterBestAnt = ant;
                }
            }

            // C. Cập nhật Global Best (Cơ chế MMAS: Chỉ update khi có hàng mới xịn hơn)
            boolean foundNewBest = false;
            if (iterBestQuality < globalBestQuality) {
                globalBestQuality = iterBestQuality;
                System.arraycopy(iterBestAnt.getSolution(), 0, globalBestSolution, 0, numNodes);
                globalBestTourOrder = new ArrayList<>(iterBestAnt.getTourOrder());
                foundNewBest = true;
                
                // Reset biên theo best mới
                tauMax = 1.0 / (rho * globalBestQuality);
                tauMin = tauMax / (2.0 * numNodes);
            }

            // D. Cập nhật Pheromone kiểu MMAS
            updatePheromonesMMAS(pheromoneMatrix, globalBestSolution, globalBestQuality, rho, tauMin, tauMax);

            // E. Lưu History để vẽ Chart
            if (shouldSave(i, maxIterations, numNodes, foundNewBest)) {
                double[] conf = calculateConfidence(pheromoneMatrix, globalBestSolution, numColors);
                history.add(new SimulationStep(i + 1, globalBestQuality, globalBestSolution.clone(), conf));
                if (history.size() > 50) history.remove(0);
            }
        }
        long endNano = System.nanoTime();
	    long duration = (endNano - startNano) / 1_000_000; 
	    if (duration == 0) {
	        System.out.println(">>> [" + this.getClass().getSimpleName() + "] Super fast! Microseconds: " + (endNano - startNano) / 1000);
	    }
        // --- TRACE (Để UI chạy Animation bước tô màu) ---
        List<NodeColorAction> trace = generateTrace(globalBestTourOrder, globalBestSolution, nodes);

        return new SimulationResponse(
            countUniqueColors(globalBestSolution), 
            globalBestSolution,  
            calculateTotalConflicts(globalBestSolution, nodes), 
            history,
            trace,
            duration,
            validNodes 
        );
    }

    // ==================== CÁC HÀM CORE MMAS ====================

    private void initializePheromones(double[][] matrix, int numNodes, int numColors, double initialValue) {
        for (int i = 0; i < numNodes; i++) {
            for (int j = 0; j < numColors; j++) matrix[i][j] = initialValue;
        }
    }

    private void constructSolutionsDSATUR(Ant[] ants, List<Node> nodes, int numNodes, int numColors, 
                                        double[][] matrix, double alpha, double beta) {
        for (Ant ant : ants) {
            ant.reset();
            Set<Integer> unvisited = new HashSet<>();
            for (int i = 0; i < numNodes; i++) unvisited.add(i);

            while (!unvisited.isEmpty()) {
                int nodeId = selectNextNodeDSATUR(ant, unvisited, nodes);
                unvisited.remove(nodeId);
                int color = selectNextColorMMAS(ant, nodeId, matrix, nodes, numColors, alpha, beta);
                ant.setColor(nodeId, color);
            }
        }
    }

    private int selectNextColorMMAS(Ant ant, int nodeId, double[][] matrix, List<Node> nodes, 
                                   int numColors, double alpha, double beta) {
        List<Integer> validColors = findValidColors(ant, nodeId, nodes, numColors);
        if (validColors.size() == 1) return validColors.get(0);

        double[] probs = new double[validColors.size()];
        double sum = 0;
        double heuristic = nodes.get(nodeId).getNeighbors().size() + 0.1;

        for (int i = 0; i < validColors.size(); i++) {
            int color = validColors.get(i);
            probs[i] = Math.pow(matrix[nodeId][color], alpha) * Math.pow(heuristic, beta);
            sum += probs[i];
        }

        if (sum == 0) return validColors.get(random.nextInt(validColors.size()));

        double r = random.nextDouble() * sum;
        double current = 0;
        for (int i = 0; i < probs.length; i++) {
            current += probs[i];
            if (current >= r) return validColors.get(i);
        }
        return validColors.get(validColors.size() - 1);
    }

    private void updatePheromonesMMAS(double[][] matrix, int[] bestSol, int bestQuality, 
                                     double rho, double tMin, double tMax) {
        // Bay hơi & Min boundary
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                matrix[i][j] *= (1.0 - rho);
                if (matrix[i][j] < tMin) matrix[i][j] = tMin;
            }
        }
        // Deposit từ Global Best & Max boundary
        double deposit = 1.0 / bestQuality;
        for (int i = 0; i < bestSol.length; i++) {
            int color = bestSol[i];
            if (color != -1) {
                matrix[i][color] += deposit;
                if (matrix[i][color] > tMax) matrix[i][color] = tMax;
            }
        }
    }

    // ==================== CÁC HÀM HELPER (BỊ THIẾU NÃY) ====================

    private boolean shouldSave(int iter, int maxIter, int numNodes, boolean foundNewBest) {
        if (foundNewBest) return true; // Luôn lưu nếu tìm ra kỉ lục mới
        if (iter == 0 || iter == maxIter - 1) return true;
        if (numNodes < 300 && (iter + 1) % 10 == 0) return true; // Cách 10 vòng lưu 1 lần
        return false;
    }

    private double[] calculateConfidence(double[][] matrix, int[] sol, int numColors) {
        double[] conf = new double[sol.length];
        for (int n = 0; n < sol.length; n++) {
            int c = sol[n];
            if (c == -1) { conf[n] = 0.0; continue; }
            double currentP = matrix[n][c];
            double sumP = 0;
            for (int i = 0; i < numColors; i++) sumP += matrix[n][i];
            conf[n] = (sumP > 0) ? (currentP / sumP) : 0.0;
        }
        return conf;
    }

    private List<NodeColorAction> generateTrace(List<Integer> order, int[] sol, List<Node> nodes) {
        List<NodeColorAction> trace = new ArrayList<>();
        // Fallback nếu order rỗng
        List<Integer> realOrder = order.isEmpty() ? 
                nodes.stream().map(Node::getId).collect(Collectors.toList()) : order;
        
        int step = 0;
        for (int id : realOrder) {
            trace.add(new NodeColorAction(id, sol[id], step++));
        }
        return trace;
    }

    private int countUniqueColors(int[] sol) {
        Set<Integer> unique = new HashSet<>();
        for (int c : sol) if (c != -1) unique.add(c);
        return unique.size();
    }

    private int calculateTotalConflicts(int[] sol, List<Node> nodes) {
        int conflicts = 0;
        for (Node node : nodes) {
            int u = node.getId();
            for (int v : node.getNeighbors()) {
                if (sol[u] != -1 && sol[u] == sol[v]) conflicts++;
            }
        }
        return conflicts / 2; // Đồ thị vô hướng tính 2 lần
    }

    private int selectNextNodeDSATUR(Ant ant, Set<Integer> unvisited, List<Node> nodes) {
        int maxSaturation = -1; int maxDegree = -1;
        List<Integer> candidates = new ArrayList<>();

        for (int id : unvisited) {
            Set<Integer> nColors = new HashSet<>();
            for (int nId : nodes.get(id).getNeighbors()) {
                int c = ant.getSolution()[nId];
                if (c != -1) nColors.add(c);
            }
            int sat = nColors.size();
            int deg = nodes.get(id).getNeighbors().size();

            if (sat > maxSaturation || (sat == maxSaturation && deg > maxDegree)) {
                maxSaturation = sat; maxDegree = deg;
                candidates.clear(); candidates.add(id);
            } else if (sat == maxSaturation && deg == maxDegree) {
                candidates.add(id);
            }
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    private List<Integer> findValidColors(Ant ant, int nodeId, List<Node> nodes, int numColors) {
        Set<Integer> neighborColors = new HashSet<>();
        for (int neighborId : nodes.get(nodeId).getNeighbors()) {
            int c = ant.getSolution()[neighborId];
            if (c != -1) neighborColors.add(c);
        }
        List<Integer> valid = new ArrayList<>();
        for (int i = 0; i < numColors; i++) {
            if (!neighborColors.contains(i)) valid.add(i);
        }
        if (valid.isEmpty()) {
            for (int i = 0; i < numColors; i++) valid.add(i);
        }
        return valid;
    }

    private int runSimpleGreedyInner(List<Node> nodes) {
        // Welch-Powell tinh gọn
        int[] sol = new int[nodes.size()];
        Arrays.fill(sol, -1);
        List<Node> sorted = new ArrayList<>(nodes);
        sorted.sort((a,b) -> b.getNeighbors().size() - a.getNeighbors().size());

        for (Node n : sorted) {
            Set<Integer> used = new HashSet<>();
            for (int neigh : n.getNeighbors()) if (sol[neigh] != -1) used.add(sol[neigh]);
            int color = 0;
            while (used.contains(color)) color++;
            sol[n.getId()] = color;
        }
        return countUniqueColors(sol);
    }
}