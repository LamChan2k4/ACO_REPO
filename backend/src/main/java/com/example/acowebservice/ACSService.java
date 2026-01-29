package com.example.acowebservice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
public class ACSService implements SolverStrategy  {
	@Override
	public SimulationResponse solve(SimulationRequest req) {
		long startNano = System.nanoTime(); 
	    List<Node> nodes = req.getNodes();
	    
	    int numAnts = (req.getNumAnts() != null) ? req.getNumAnts() : 30;
	    int maxIterations = (req.getMaxIterations() != null) ? req.getMaxIterations() : 100;
	    int numColors = (req.getNumColors() != null) ? req.getNumColors() : 20;
	    
	    double alpha = (req.getAlpha() != null) ? req.getAlpha() : 1.0;
	    double beta = (req.getBeta() != null) ? req.getBeta() : 2.0;
	    double rho = (req.getEvaporation() != null) ? req.getEvaporation() : 0.1;
	    
	    // ✅ ĐÂY LÀ CHỖ QUAN TRỌNG: Fix lỗi sập khi chạy ACS
	    double q0 = (req.getQ0() != null) ? req.getQ0() : 0.9;
	    double xi = 0.1; // Local evaporation
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
	    double initialPheromone = 1.0 / (numNodes * 2.0);
	    double localEvaporation = 0.1;
	    List<Integer> bestTourOrder = new ArrayList<>();
	    // --- SMART ORDERING ---
	    List<Node> sortedNodes = new ArrayList<>(nodes);
	    sortedNodes.sort((n1, n2) -> n2.getNeighbors().size() - n1.getNeighbors().size());
	    int[] walkingOrder = new int[numNodes];
	    for (int i = 0; i < numNodes; i++) walkingOrder[i] = sortedNodes.get(i).getId();

	    // --- BỘ NHỚ ---
	    Ant[] ants = new Ant[numAnts];
	    for(int i = 0; i < numAnts; i++) ants[i] = new Ant(numNodes);
	    double[][] pheromoneMatrix = new double[numNodes][numColors];
	    int[] bestSolution = new int[numNodes];
	    int bestSolutionQuality = Integer.MAX_VALUE;
	    
	    initializePheromones(pheromoneMatrix, numNodes, numColors, initialPheromone);
	    List<SimulationStep> history = new ArrayList<>();

	    // --- VÒNG LẶP ---
	    /*
			    for (int i = 0; i < maxIterations; i++) {
			        // CẬP NHẬT CỤC BỘ DIỄN RA TRONG NÀY
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
			        */
	    for (int i = 0; i < maxIterations; i++) {
	    	if (Thread.currentThread().isInterrupted()) {
	            System.out.println("🛑 [LOG] ACS bị buộc dừng để giải phóng CPU.");
	            // Trả về kết quả rỗng hoặc null thay vì chạy tiếp
	            return new SimulationResponse(0, new int[0], 0, new ArrayList<>(), new ArrayList<>(), 0, new ArrayList<>());
	        }
	        // CẬP NHẬT CỤC BỘ DIỄN RA TRONG NÀY
	    	constructSolutionsNodeDSATUR(ants, numNodes, pheromoneMatrix, alpha, nodes, numColors, initialPheromone, beta, localEvaporation, q0);
	        
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
	        // GLOBAL UPDATE: Chỉ update cho bestSolution
	        updatePheromones(pheromoneMatrix, bestSolution, bestSolutionQuality, rho, numNodes);
	        
	        boolean shouldSaveHistory = foundNewBest || i == 0 || i == maxIterations - 1;
	        
	        // Nếu là đồ thị nhỏ, ta lưu thêm mỗi 10 vòng để xem cho mượt
	        if (numNodes < 300 && (i + 1) % 10 == 0) {
	            shouldSaveHistory = true;
	        }

	        if (shouldSaveHistory) {
	            // Sử dụng tên hàm calculateConfidence đồng nhất
	            double[] currentConf = calculateACOConfidence(pheromoneMatrix, bestSolution, numColors);

	            history.add(new SimulationStep(
	                i + 1, 
	                bestSolutionQuality, 
	                bestSolution.clone(), // Copy dữ liệu sang mảng mới
	                currentConf
	            ));
	            
	            // Giới hạn an toàn: Nếu history quá 50 bức ảnh, xóa bức ảnh cũ nhất 
	            // (để trình duyệt không bị treo khi nhận JSON quá to)
	            if (history.size() > 50) {
	                history.remove(0); 
	            }
	        }
	    }
	    long endNano = System.nanoTime();
	    long duration = (endNano - startNano) / 1_000_000; 
	    if (duration == 0) {
	        System.out.println(">>> [" + this.getClass().getSimpleName() + "] Super fast! Microseconds: " + (endNano - startNano) / 1000);
	    }
	    // --- TRACE ---
	    List<NodeColorAction> trace = new ArrayList<>();

		 // Nếu tìm thấy đường đi (đề phòng trường hợp chưa tìm ra thì fallback về sortedNodes)
		 List<Integer> pathOrderToUse = bestTourOrder.isEmpty() ? 
		                                sortedNodes.stream().map(Node::getId).collect(Collectors.toList()) : 
		                                bestTourOrder;
	
		 int stepCount = 0;
		 for (Integer nodeId : pathOrderToUse) {
		     // Backend gửi danh sách Node ID theo đúng thứ tự kiến đã nhảy
		     trace.add(new NodeColorAction(nodeId, bestSolution[nodeId], stepCount++));
		 }
	    return new SimulationResponse(countUniqueColors(bestSolution), bestSolution,  calculateTotalConflicts(bestSolution, nodes), history,trace,duration,
	    	    validNodes  );
	}
	private void initializePheromones(double[][] pheromoneMatrix, int numNodes, int numColors, double initialPheromone) {
		for(int i=0;i<numNodes;i++) {
			for(int j=0;j<numColors;j++) {
				pheromoneMatrix[i][j]=initialPheromone;
			}
		}
	}
	private double[] calculateACOConfidence(double[][] pheromoneMatrix, int[] currentSolution, int numColors) {
	    int numNodes = currentSolution.length;
	    double[] confidence = new double[numNodes];

	    for (int i = 0; i < numNodes; i++) {
	        int selectedColor = currentSolution[i];
	        if (selectedColor == -1) {
	            confidence[i] = 0.0;
	            continue;
	        }

	        double pBest = pheromoneMatrix[i][selectedColor]; // Mùi của màu tốt nhất
	        double pSum = 0.0; // Tổng mùi của tất cả màu tại node này
	        for (int c = 0; c < numColors; c++) {
	            pSum += pheromoneMatrix[i][c];
	        }

	        // Tỷ lệ áp đảo của màu này (thường nằm trong khoảng 0.0 -> 1.0)
	        confidence[i] = (pSum > 0) ? (pBest / pSum) : 0.0;
	    }
	    return confidence;
	}
	private void constructSolutions(Ant[] ants, int numNodes, double[][] pheromoneMatrix, 
            double alpha, List<Node> nodes, int numColors, 
            double initialPheromone, double beta, double localEvaporation, 
            double q0, int[] walkingOrder) {

			// Tạo danh sách gốc để làm "khuôn" xáo trộn
			List<Integer> masterOrder = new ArrayList<>();
			for (int id : walkingOrder) masterOrder.add(id);
			
			for (Ant ant : ants) {
			ant.reset();
			
			// --- TẠO CHIẾN THUẬT RIÊNG CHO TỪNG CON KIẾN ---
			List<Integer> myPath = new ArrayList<>(masterOrder);
			
			// 15% xác suất con kiến này đi thám hiểm ngẫu nhiên hoàn toàn
			if (Math.random() < 0.15) {
				
			} 
			else {
				// 85% còn lại: đi theo "walkingOrder" thông minh (hub trước)
				// nhưng thỉnh thoảng đảo vị trí 2 node ngẫu nhiên để tăng tính đa dạng
				if (numNodes > 2) {
				int idx1 = new Random().nextInt(numNodes);
				int idx2 = new Random().nextInt(numNodes);
				Collections.swap(myPath, idx1, idx2);
				}
			}

// Bắt đầu bò theo lộ trình riêng
for (int targetNodeId : myPath) {
// 1. Kiến chọn màu dựa trên mùi hương và luật q0
int selectedColor = selectNextColor(ant, targetNodeId, pheromoneMatrix, alpha, nodes, numColors, beta, q0);

ant.setColor(targetNodeId, selectedColor);

// 2. LOCAL UPDATE (Đặc trưng ACS): "Ăn bớt" mùi hương ngay lập tức
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
	private int selectNextColor(Ant ant, int nodeId, double[][] pheromoneMatrix, double alpha, List<Node> nodes, int numColors, double beta, double q0) {
	    
	    List<Integer> validColors = findValidColors(ant, nodeId, nodes, numColors);
	    if (validColors.size() == 1) return validColors.get(0);

	    double q = Math.random();
	    
	    // --- CHẾ ĐỘ THAM LAM (EXPLOITATION) ---
	    if (q <= q0) {
	        int bestColor = -1;
	        double maxScore = -1.0;

	        for (int color : validColors) {
	            double pheromone = pheromoneMatrix[nodeId][color];
	            
	            // Dùng số lượng hàng xóm làm Heuristic (Bậc của đỉnh)
	            double heuristic = nodes.get(nodeId).getNeighbors().size() + 0.1;
	            
	            double score = Math.pow(pheromone, alpha) * Math.pow(heuristic, beta);

	            if (score > maxScore) {
	                maxScore = score;
	                bestColor = color;
	            }
	        }
	        return bestColor;
	    } 
	    // --- CHẾ ĐỘ NGẪU NHIÊN (EXPLORATION) ---
	    else {
	        double[] probabilities = new double[validColors.size()];
	        double sum = 0.0;
	        
	        for (int i = 0; i < validColors.size(); i++) {
	            int c = validColors.get(i);
	            double h = nodes.get(nodeId).getNeighbors().size() + 0.1;
	            double s = Math.pow(pheromoneMatrix[nodeId][c], alpha) * Math.pow(h, beta);
	            probabilities[i] = s;
	            sum += s;
	        }

	        if (sum == 0) return validColors.get(new Random().nextInt(validColors.size()));

	        double r = Math.random() * sum;
	        double total = 0.0;
	        for (int i = 0; i < probabilities.length; i++) {
	            total += probabilities[i];
	            if (total >= r) return validColors.get(i);
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
	private double[] calculateConfidence(double[][] matrix, int[] sol, int numColors) {
	    double[] conf = new double[sol.length];
	    for(int n=0; n<sol.length; n++) {
	        if(sol[n] == -1) { conf[n] = 0.1; continue; }
	        double bestP = matrix[n][sol[n]];
	        double totalP = 0;
	        for(int c=0; c<numColors; c++) totalP += matrix[n][c];
	        conf[n] = (totalP > 0) ? (bestP / totalP) : 0.1;
	    }
	    return conf;
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
	private void constructSolutionsNodeDSATUR(Ant[] ants, int numNodes, double[][] pheromoneMatrix, 
            double alpha, List<Node> nodes, int numColors, 
            double initialPheromone, double beta, double localEvaporation, 
            double q0 /* int[] walkingOrder - BỎ CÁI NÀY ĐI */ ) {

for (Ant ant : ants) {
ant.reset();

// 1. Quản lý danh sách các node chưa thăm (cho riêng con kiến này)
Set<Integer> unvisitedNodes = new HashSet<>();
for(int i=0; i<numNodes; i++) unvisitedNodes.add(i);

// --- VÒNG LẶP XÂY DỰNG ---
// Kiến tự quyết định Node nào tô tiếp theo, không theo danh sách có sẵn
while (!unvisitedNodes.isEmpty()) {

// BƯỚC A: CHỌN NODE TIẾP THEO (DYNAMIC HEURISTIC - DSATUR)
int selectedNodeId = selectNextNodeDSATUR(ant, unvisitedNodes, nodes);

// Xóa khỏi danh sách chưa thăm
unvisitedNodes.remove(selectedNodeId); 

// BƯỚC B: CHỌN MÀU CHO NODE ĐÓ (Như logic cũ, nhưng sửa lại heuristic)
int selectedColor = selectNextColor(ant, selectedNodeId, pheromoneMatrix, alpha, nodes, numColors, beta, q0);

// Set màu
ant.setColor(selectedNodeId, selectedColor);

// BƯỚC C: LOCAL UPDATE
double oldP = pheromoneMatrix[selectedNodeId][selectedColor];
pheromoneMatrix[selectedNodeId][selectedColor] = (1.0 - localEvaporation) * oldP + localEvaporation * initialPheromone;
}
}
}

//Hàm hỗ trợ tìm Node tiếp theo theo DSATUR (Saturation Degree)
private int selectNextNodeDSATUR(Ant ant, Set<Integer> unvisited, List<Node> nodes) {
int bestNodeId = -1;
int maxSaturation = -1;
int maxDegree = -1;

// Tìm các ứng viên tốt nhất (Candidate List)
List<Integer> candidates = new ArrayList<>();

for (int nodeId : unvisited) {
// TÍNH SATURATION: Số lượng màu khác nhau đã tô ở hàng xóm
Set<Integer> neighborColors = new HashSet<>();
for (int neighbor : nodes.get(nodeId).getNeighbors()) {
int c = ant.getSolution()[neighbor];
if (c != -1) neighborColors.add(c);
}
int saturation = neighborColors.size();
int degree = nodes.get(nodeId).getNeighbors().size();

if (saturation > maxSaturation) {
maxSaturation = saturation;
maxDegree = degree;
candidates.clear();
candidates.add(nodeId);
} else if (saturation == maxSaturation) {
// Nếu Saturation bằng nhau, ưu tiên Bậc cao (Degree)
if (degree > maxDegree) {
maxDegree = degree;
candidates.clear();
candidates.add(nodeId);
} else if (degree == maxDegree) {
candidates.add(nodeId);
}
}
}

// TRẢ VỀ NGẪU NHIÊN TRONG SỐ CÁC ỨNG VIÊN TỐT NHẤT (RCL)
// Để tránh việc kiến nào cũng đi y hệt nhau
return candidates.get(new Random().nextInt(candidates.size())); 
}
}
