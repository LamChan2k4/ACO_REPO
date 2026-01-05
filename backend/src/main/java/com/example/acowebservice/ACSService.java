package com.example.acowebservice;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ACSService implements SolverStrategy  {
	@Override
	public SimulationResponse solve(SimulationRequest req) {
	    // --- 1. CHUẨN BỊ (GIỮ NGUYÊN) ---
	    List<Node> nodes = req.getNodes();
	    int numNodes = nodes.size();
	    int numColors = req.getNumColors();
	    int maxIterations = req.getMaxIterations();
	    int numAnts = req.getNumAnts();

	    double alpha = req.getAlpha();
	    double beta = req.getBeta();
	    double q0 = req.getQ0();
	    double evaporationRate = req.getEvaporation(); // Rho
	    double localEvaporation = 0.1; // Xi
	    double initialPheromone = 1.0 / ((double) numNodes * numColors); 

	    // --- BƯỚC CẢI TIẾN: THỨ TỰ ĐI THÔNG MINH (NCKH) ---
	    // Sắp xếp các Node: Thằng nhiều hàng xóm nhất tô trước
	    List<Node> sortedNodes = new ArrayList<>(nodes);
	    sortedNodes.sort((n1, n2) -> n2.getNeighbors().size() - n1.getNeighbors().size());
	    
	    int[] walkingOrder = new int[numNodes];
	    for (int i = 0; i < numNodes; i++) {
	        walkingOrder[i] = sortedNodes.get(i).getId();
	    }

	    // --- KHỞI TẠO BỘ NHỚ ---
	    Ant[] ants = new Ant[numAnts];
	    for(int i = 0; i < numAnts; i++) ants[i] = new Ant(numNodes);
	    
	    double[][] pheromoneMatrix = new double[numNodes][numColors];
	    int[] bestSolution = new int[numNodes];
	    int bestSolutionQuality = Integer.MAX_VALUE;
	    
	    initializePheromones(pheromoneMatrix, numNodes, numColors, initialPheromone);
	    List<SimulationStep> history = new ArrayList<>();

	    // --- 2. VÒNG LẶP CHÍNH ---
	    for (int i = 0; i < maxIterations; i++) {
	        
	        // CHÚ Ý: Cập nhật hàm constructSolutions để nó nhận walkingOrder
	        constructSolutions(ants, numNodes, pheromoneMatrix, alpha, nodes, numColors, initialPheromone, beta, localEvaporation, q0, walkingOrder);
	        
	        boolean foundNewBest = false;
	        for (Ant ant : ants) {
	            int cost = ant.getNumberOfColorsUsed();
	            if (cost < bestSolutionQuality) {
	                bestSolutionQuality = cost;
	                System.arraycopy(ant.getSolution(), 0, bestSolution, 0, numNodes);
	                foundNewBest = true;
	            }
	        }
	        
	        // ACS Global Update: Chỉ update cho nhà vô địch
	        updatePheromones(pheromoneMatrix, bestSolution, bestSolutionQuality, evaporationRate, numNodes);
	        
	        // --- QUAY PHIM TIME-LAPSE ---
	        if (foundNewBest || i == 0 || (i + 1) % 10 == 0) {
	            double[] confidence = new double[numNodes];
	            for (int n = 0; n < numNodes; n++) {
	                int col = bestSolution[n];
	                if (col != -1) {
	                    double p_best = pheromoneMatrix[n][col];
	                    double p_total = 0;
	                    for (int c = 0; c < numColors; c++) p_total += pheromoneMatrix[n][c];
	                    confidence[n] = (p_total > 0) ? (p_best / p_total) : 0.1;
	                }
	            }
	            history.add(new SimulationStep((i + 1), countUniqueColors(bestSolution), bestSolution.clone(), confidence));
	        }
	    }

	    // --- 3. TẠO TRACE REPLAY THÔNG MINH ---
	    // Giúp con kiến trên web bò từ trung tâm đồ thị lan ra ngoài
	    List<NodeColorAction> trace = new ArrayList<>();
	    for (int i = 0; i < numNodes; i++) {
	        int nodeId = walkingOrder[i]; 
	        trace.add(new NodeColorAction(nodeId, bestSolution[nodeId], i));
	    }

	    // TÍNH TOÁN KẾT QUẢ CUỐI CÙNG (Dùng đúng chuẩn Response mới)
	    int finalQuality = countUniqueColors(bestSolution);
	    int finalConflicts = calculateTotalConflicts(bestSolution, nodes);

	    // BÁO CÁO LOG CONSOLE
	    System.out.println("ACS Finished: Colors=" + finalQuality + ", Conflicts=" + finalConflicts);

	    for (int i = 0; i < numNodes; i++) {
	        // i chính là số bước, lấy nodeId từ mảng thứ tự đi walkingOrder
	        int targetNodeId = walkingOrder[i]; 
	        trace.add(new NodeColorAction(targetNodeId, bestSolution[targetNodeId], i));
	    }
	    return new SimulationResponse(finalQuality, bestSolution, history, finalConflicts,trace);
	}
	private void initializePheromones(double[][] pheromoneMatrix, int numNodes, int numColors, double initialPheromone) {
		for(int i=0;i<numNodes;i++) {
			for(int j=0;j<numColors;j++) {
				pheromoneMatrix[i][j]=initialPheromone;
			}
		}
	}
	private void constructSolutions(Ant[] ants, int numNodes, double[][] pheromoneMatrix, double alpha, List<Node> nodes, int numColors, double initialPheromone, double beta, double localEvaporation, double q0, int[] walkingOrder) {
	    for(Ant ant : ants) {
	        ant.reset();
	    }
	    
	    // THAY ĐỔI: Sử dụng walkingOrder để kiến đi theo thứ tự thông minh
	    for(int i = 0; i < numNodes; i++) {
	        int targetNodeId = walkingOrder[i]; // Lấy đỉnh "khó" ra xử trước
	        
	        for(Ant ant : ants) {
	            int selectedColor = selectNextColor(ant, targetNodeId, pheromoneMatrix, alpha, nodes, numColors, beta, q0);
	            ant.setColor(targetNodeId, selectedColor);
	            
	            // Local Update ACS
	            double oldP = pheromoneMatrix[targetNodeId][selectedColor];
	            pheromoneMatrix[targetNodeId][selectedColor] = (1.0 - localEvaporation) * oldP + localEvaporation * initialPheromone;
	        }
	    }
	}
	private void updatePheromones(double [][] pheromoneMatrix,int[] bestSolution,int bestSolutionQuality,double evaporationRate, int numNodes) {
		double additionalPheromone = 1.0 / bestSolutionQuality;
		for(int i=0;i<numNodes;i++) {
			int color = bestSolution[i];
			if(color != -1) {
				pheromoneMatrix[i][color]= (1 - evaporationRate) * pheromoneMatrix[i][color] + evaporationRate * additionalPheromone;
			}
		}
	}
	private int selectNextColor(Ant ant,int nodeId,double [][] pheromoneMatrix,double alpha,List<Node> nodes,int numColors,double beta,double q0) {
		List<Integer> validColors=findValidColors(ant, nodeId,nodes,numColors);
		if (validColors.size()==1) {
			return validColors.get(0);
		}
		double q = Math.random(); 
		if (q <= q0) {
			int bestColor=-1;
			double maxScore=-1.0;
			
			for(int color:validColors) {
				double pheromone=pheromoneMatrix[nodeId][color];
				double heuristic=1.0;
				//double heuristic = currentNode.getNeighbors().size();
				double score = Math.pow(pheromone,  alpha)* Math.pow(heuristic, beta);
				if(score>maxScore) {
					maxScore = score;
	                bestColor = color;
				}
			}
			return bestColor;
		}
		else {
			
			double[] probabilities = new double[validColors.size()];
			double probabilitiesSum = 0.0;
			for(int i=0;i<validColors.size();i++) {
				int color=validColors.get(i);
				double pheromone =  pheromoneMatrix[nodeId][color];
				double heuristic = 1.0;
				double score = Math.pow(pheromone,  alpha)* Math.pow(heuristic, beta);
				probabilities[i] = score;
				probabilitiesSum += score;
			}
			if (probabilitiesSum == 0) {
		        return validColors.get(new java.util.Random().nextInt(validColors.size()));
		    }
			double randomValue = Math.random() * probabilitiesSum;
		    double cumulativeSum = 0.0;
		    
		    for (int i = 0; i < validColors.size(); i++) {
		        cumulativeSum += probabilities[i];
		        if (cumulativeSum >= randomValue) {
		            return validColors.get(i); 
		        }
		    }
		    
			return validColors.get(validColors.size() - 1);
		}
	}
	private List<Integer> findValidColors(Ant ant, int nodeId,List<Node> nodes,int numColors){
		Set<Integer> usedByNeighbors = new HashSet<>();
		Node currentNode = nodes.get(nodeId);
		List<Integer> neighborIds = currentNode.getNeighbors();
		for (Integer neighborId : neighborIds) {
			int neighborColor = ant.getSolution()[neighborId];
			if (neighborColor != -1) {
				usedByNeighbors.add(neighborColor);
			}
		}
		List<Integer> validColors = new ArrayList<>();
		for (int color = 0; color < numColors; color++) {
			if (!usedByNeighbors.contains(color)) {
				validColors.add(color);
			}
		}
		if (validColors.isEmpty()) {
			List<Integer> allColors = new ArrayList<>();
			for (int i = 0; i < numColors; i++) {
			    allColors.add(i);
			}
			return allColors;
		}
		return validColors;
	}
	private int calculateTotalConflicts(int[] solution, List<Node> nodes) {
	    int totalConflicts = 0;
	    for (Node u : nodes) {
	        int uId = u.getId();
	        int uColor = solution[uId];
	        
	        // Duyệt qua tất cả hàng xóm của Node hiện tại
	        for (int vId : u.getNeighbors()) {
	            // Nếu hàng xóm trùng màu -> Phát hiện 1 lỗi
	            if (uColor == solution[vId] && uColor != -1) {
	                totalConflicts++;
	            }
	        }
	    }
	    // Vì đồ thị vô hướng, cạnh A-B được đếm 2 lần (lúc ở A và lúc ở B)
	    // nên ta chia 2 để ra số lượng cạnh bị trùng màu thực tế.
	    return totalConflicts / 2;
	}
	private int countUniqueColors(int[] solution) {
	    Set<Integer> uniqueColors = new HashSet<>();
	    for (int color : solution) {
	        if (color != -1) {
	            uniqueColors.add(color);
	        }
	    }
	    return uniqueColors.size();
	}
}
