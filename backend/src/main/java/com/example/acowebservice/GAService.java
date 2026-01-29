package com.example.acowebservice;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service 
public class GAService implements SolverStrategy{
	Random rand = new Random();
	@Override
    public SimulationResponse solve(SimulationRequest req) {
		long startNano = System.nanoTime(); 
	    
        List<Node> nodes = req.getNodes();
        
        int popSize = req.getNumAnts(); // Population Size
        int maxGenerations = req.getMaxIterations();
        int numColors = req.getNumColors();
        double mutationRate = req.getMutationRate();
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
        
        // --- BƯỚC CẢI TIẾN 1: SMART ORDERING (Việc khó làm trước) ---
        List<Node> sortedNodes = new ArrayList<>(nodes);
        sortedNodes.sort((n1, n2) -> n2.getNeighbors().size() - n1.getNeighbors().size());
        int[] walkingOrder = new int[numNodes];
        for (int i = 0; i < numNodes; i++) walkingOrder[i] = sortedNodes.get(i).getId();

        // --- BƯỚC 2: TIỀN XỬ LÝ CẠNH (Tối ưu tính Fitness) ---
        List<int[]> edges = new ArrayList<>();
        for (Node u : nodes) {
            for (int vId : u.getNeighbors()) {
                if (u.getId() < vId) edges.add(new int[] { u.getId(), vId });
            }
        }

        // --- BƯỚC 3: KHỞI TẠO QUẦN THỂ ---
        List<int[]> population = new ArrayList<>();
        for (int i = 0; i < popSize; i++) {
            int[] ind = new int[numNodes];
            for (int j = 0; j < numNodes; j++) ind[j] = rand.nextInt(numColors);
            population.add(ind);
        }

        int[] globalBestSolution = new int[numNodes];
        int globalBestConflicts = Integer.MAX_VALUE;
        List<SimulationStep> history = new ArrayList<>();

        // --- BƯỚC 4: VÒNG LẶP TIẾN HÓA (GENERATIONS) ---
        for (int g = 0; g < maxGenerations; g++) {
        	if (Thread.currentThread().isInterrupted()) {
                System.out.println("🛑 [LOG] GA bị buộc dừng để giải phóng CPU.");
                // Trả về kết quả rỗng hoặc null thay vì chạy tiếp
                return new SimulationResponse(0, new int[0], 0, new ArrayList<>(), new ArrayList<>(), 0, new ArrayList<>());
            }
            boolean foundNewBest = false;
            
            // Tìm cá thể tốt nhất trong quần thể hiện tại
            for (int[] individual : population) {
                int currentConflicts = calculateConflicts(individual, edges);
                if (currentConflicts < globalBestConflicts) {
                    globalBestConflicts = currentConflicts;
                    System.arraycopy(individual, 0, globalBestSolution, 0, numNodes);
                    foundNewBest = true;
                }
            }

            // --- BƯỚC TỐI ƯU 2: GHI LỊCH SỬ CHỌN LỌC (Cho 5000 Nodes) ---
            // Chỉ chụp ảnh (Snapshot) nếu: 
            // - Là vòng đầu hoặc cuối
            // - Hoặc tìm thấy kỷ lục mới thực sự tốt
            boolean shouldSave = (g == 0) || (g == maxGenerations - 1) || (foundNewBest && history.size() < 30);
            
            // Nếu đồ thị nhỏ, lưu định kỳ cho đẹp mắt
            if (numNodes < 300 && (g + 1) % 20 == 0) shouldSave = true;

            if (shouldSave) {
                double[] confidence = calculateGAConfidence(population, globalBestSolution);
                history.add(new SimulationStep(
                    g + 1, 
                    countUniqueColors(globalBestSolution), 
                    globalBestSolution.clone(), 
                    confidence
                ));
                // Khống chế kịch trần để tránh treo Browser khi JSON quá to
                if (history.size() > 50) history.remove(0); 
            }

            // Tạo thế hệ tiếp theo
            List<int[]> newPop = new ArrayList<>();
            newPop.add(globalBestSolution.clone()); // Elitism
            
            while (newPop.size() < popSize) {
                int[] p1 = tournamentSelection(population, edges, 5);
                int[] p2 = tournamentSelection(population, edges, 5);
                int[] child = crossover(p1, p2);
                mutate(child, numColors, mutationRate);
                newPop.add(child);
            }
            population = newPop;
        }
        long endNano = System.nanoTime();
	    long duration = (endNano - startNano) / 1_000_000; 
	    if (duration == 0) {
	        System.out.println(">>> [" + this.getClass().getSimpleName() + "] Super fast! Microseconds: " + (endNano - startNano) / 1000);
	    }
        // --- BƯỚC 5: TẠO TRACE REPLAY THÔNG MINH ---
        List<NodeColorAction> trace = new ArrayList<>();
        // Nếu node quá nhiều (>500), chỉ gửi trace 50 node tiêu biểu để tránh lag web
        int stepCount = Math.min(numNodes, 500); 
        for (int i = 0; i < stepCount; i++) {
            int nodeId = walkingOrder[i];
            trace.add(new NodeColorAction(nodeId, globalBestSolution[nodeId], i));
        }

        // --- TRẢ VỀ RESPONSE CHUẨN 5 THAM SỐ ---
        return new SimulationResponse(
            countUniqueColors(globalBestSolution), 
            globalBestSolution, 
            globalBestConflicts, 
            history, 
            trace, 
            duration,
            validNodes 
        );
    }
	
	private int[] crossover(int[] parent1, int[] parent2) {
		int n= parent1.length;
		int[] child=new int[n];
		
		
		int crossoverPoint=rand.nextInt(n);
		
		for(int i=0; i<n; i++) {
			if(i< crossoverPoint) {
				child[i]=parent1[i];
			}
			else {
				child[i]=parent2[i];
			}
		}
		return child;
	}
	private void mutate(int[] individual, int numColors,double mutationRate) {
		Random rand=new Random();
		for(int i=0;i<individual.length;i++) {
			if(rand.nextDouble()<mutationRate) {
				individual[i]=rand.nextInt(numColors);
			}
		}
	}
	private int calculateConflicts(int[] solution, List<int[]> edges) {
		int conflicts=0;
		
		for(int[]edge:edges) {
			int u=edge[0];
			int v=edge[1];
			
			if(solution[u]==solution[v]) {
				conflicts++;
			}
		}
		return conflicts;
	}
	
	private int[] tournamentSelection(List<int[]> population,List<int[]>edges, int tournamentSize) {
		
		int[] bestCandidate=population.get(rand.nextInt(population.size()));
		
		int bestFitness=calculateConflicts(bestCandidate, edges);
		
		for(int i=0; i<tournamentSize-1;i++) {
			int[] contender=population.get(rand.nextInt(population.size()));
			
			int contenderFitness=calculateConflicts(contender,edges);
			
			if(contenderFitness<bestFitness) {
				bestCandidate=contender;
				bestFitness = contenderFitness;
			}
		}
		return bestCandidate;
		
	}
	private double[] calculateGAConfidence(List<int[]> population, int[] bestSolution) {
	    int numNodes = bestSolution.length;
	    int popSize = population.size();
	    double[] confidence = new double[numNodes];

	    for (int i = 0; i < numNodes; i++) {
	        int bestColor = bestSolution[i];
	        int agreeCount = 0;

	        // Đếm xem bao nhiêu cá thể trong quần thể chọn cùng màu với "nhà vô địch"
	        for (int[] individual : population) {
	            if (individual[i] == bestColor) {
	                agreeCount++;
	            }
	        }

	        confidence[i] = (double) agreeCount / popSize;
	    }
	    return confidence;
	}
	private int countUniqueColors(int[] solution) {
        Set<Integer> uniqueColors = new HashSet<>();
        for (int color : solution) {
            uniqueColors.add(color);
        }
        return uniqueColors.size();
    }
}
