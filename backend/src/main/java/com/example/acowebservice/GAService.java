package com.example.acowebservice;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.springframework.stereotype.Service;

@Service 
public class GAService implements SolverStrategy{
	Random rand = new Random();
	@Override
	public SimulationResponse solve(SimulationRequest req) {
	    List<Node> nodes = req.getNodes();
	    int populationSize = req.getNumAnts();
	    int maxIterations = req.getMaxIterations();
	    int numColors = req.getNumColors();
	    double mutationRate = req.getMutationRate();
	    int tournamentSize = req.getTournamentSize();
	    int numNodes = nodes.size();

	    // --- BƯỚC CẢI TIẾN: TẠO THỨ TỰ TÔ MÀU THÔNG MINH (SMART ORDERING) ---
	    // Sắp xếp ID các node theo bậc (số hàng xóm) giảm dần
	    List<Node> sortedNodes = new ArrayList<>(nodes);
	    sortedNodes.sort((n1, n2) -> n2.getNeighbors().size() - n1.getNeighbors().size());
	    
	    int[] walkingOrder = new int[numNodes];
	    for (int i = 0; i < numNodes; i++) {
	        walkingOrder[i] = sortedNodes.get(i).getId();
	    }

	    // --- KHỞI TẠO GA ---
	    List<int[]> edges = new ArrayList<>();
	    for (Node u : nodes) {
	        for (int vId : u.getNeighbors()) {
	            if (u.getId() < vId) edges.add(new int[] { u.getId(), vId });
	        }
	    }

	    List<int[]> population = new ArrayList<>();
	    for (int i = 0; i < populationSize; i++) {
	        int[] individual = new int[numNodes];
	        for (int j = 0; j < numNodes; j++) individual[j] = rand.nextInt(numColors);
	        population.add(individual);
	    }

	    int[] globalBestSolution = new int[numNodes];
	    int globalBestConflicts = Integer.MAX_VALUE;
	    List<SimulationStep> history = new ArrayList<>();

	    // --- VÒNG LẶP TIẾN HÓA ---
	    for (int gen = 0; gen < maxIterations; gen++) {
	        boolean foundNewBest = false;
	        for (int[] individual : population) {
	            int conflicts = calculateConflicts(individual, edges);
	            if (conflicts < globalBestConflicts) {
	                globalBestConflicts = conflicts;
	                System.arraycopy(individual, 0, globalBestSolution, 0, numNodes);
	                foundNewBest = true;
	            }
	        }

	        // Tạo thế hệ mới
	        List<int[]> newPopulation = new ArrayList<>();
	        newPopulation.add(globalBestSolution.clone()); // Elitism
	        while (newPopulation.size() < populationSize) {
	            int[] p1 = tournamentSelection(population, edges, tournamentSize);
	            int[] p2 = tournamentSelection(population, edges, tournamentSize);
	            int[] child = crossover(p1, p2);
	            mutate(child, numColors, mutationRate);
	            newPopulation.add(child);
	        }
	        population = newPopulation;

	        // --- QUAY PHIM LỊCH SỬ ---
	        if (foundNewBest || gen == 0 || (gen + 1) % 10 == 0) {
	            // TÍNH ĐỘ ĐỒNG THUẬN (CONFIDENCE)
	            double[] confidence = new double[numNodes];
	            for (int n = 0; n < numNodes; n++) {
	                int bestColor = globalBestSolution[n];
	                int countAgreement = 0;
	                for (int[] ind : population) { if (ind[n] == bestColor) countAgreement++; }
	                confidence[n] = (double) countAgreement / populationSize;
	            }

	            history.add(new SimulationStep(
	                (gen + 1), 
	                countUniqueColors(globalBestSolution), // Chỉ gửi số màu thực tế
	                globalBestSolution.clone(),
	                confidence
	            ));
	        }
	    }

	    // --- TẠO CHI TIẾT TRACE THEO THỨ TỰ THÔNG MINH ---
	    // Thay vì 0,1,2... ta dùng walkingOrder đã tính ở đầu
	    List<NodeColorAction> trace = new ArrayList<>();
	    for (int i = 0; i < numNodes; i++) {
	        int nodeId = walkingOrder[i]; // Lấy node khó nhất ra trước
	        trace.add(new NodeColorAction(nodeId, globalBestSolution[nodeId], i));
	    }

	    // Trả về: Số màu thực, Mảng màu, Lịch sử, và Số lỗi xung đột
	    return new SimulationResponse(countUniqueColors(globalBestSolution), globalBestSolution, history, globalBestConflicts,trace);
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
	private int countUniqueColors(int[] solution) {
        Set<Integer> uniqueColors = new HashSet<>();
        for (int color : solution) {
            uniqueColors.add(color);
        }
        return uniqueColors.size();
    }
}
