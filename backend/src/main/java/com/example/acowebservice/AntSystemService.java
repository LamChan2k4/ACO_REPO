package com.example.acowebservice;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Random;
import org.springframework.stereotype.Service;

@Service
public class AntSystemService implements SolverStrategy {
	

	@Override
	public SimulationResponse solve(SimulationRequest req) {
		// Đầu hàm solve
		List<Node> nodeList = req.getNodes();
		int numNodes = nodeList.size();

		// Sắp xếp các Node theo bậc giảm dần (Thằng nhiều hàng xóm nhất đứng đầu)
		List<Node> sortedNodes = new ArrayList<>(nodeList);
		sortedNodes.sort((n1, n2) -> n2.getNeighbors().size() - n1.getNeighbors().size());

		// Tạo mảng thứ tự đi: walkingOrder[0] là ID của thằng khó nhất
		int[] walkingOrder = new int[numNodes];
		for (int i = 0; i < numNodes; i++) {
		    walkingOrder[i] = sortedNodes.get(i).getId();
		}
		List<Node> nodes = req.getNodes();
		int populationSize= req.getNumAnts();
		int numColors = req.getNumColors();
		int maxIterations = req.getMaxIterations();
        // 1. KHỞI TẠO
		double evaporationRate=0.5;
		double alpha = 1.0;
		double initialPheromone=1.0;
		
		int numAnts = populationSize;
		
		Ant[] ants = new Ant[numAnts];
		for(int i=0; i < numAnts; i++) {
			ants[i] = new Ant(numNodes);
		}
		
		double[][] pheromoneMatrix = new double[numNodes][numColors];
		int[] bestSolution = new int[numNodes];
		int bestSolutionQuality = Integer.MAX_VALUE;
		
		initializePheromones(pheromoneMatrix, numNodes, numColors, initialPheromone);
		
		// Cuộn phim lịch sử
		List<SimulationStep> history = new ArrayList<>();

        // 2. VÒNG LẶP CHÍNH
		for (int i = 0; i < maxIterations; i++) {
			
            // A. Chạy Kiến & Cập nhật Pheromone
            constructSolutions(ants, numNodes, pheromoneMatrix, alpha, nodes, numColors,walkingOrder);
            updatePheromones(numNodes, pheromoneMatrix, evaporationRate, ants, numColors);
            
            // B. Thu thập thống kê & Tìm kỷ lục
            double sumCosts = 0.0;
            int minCostInLoop = Integer.MAX_VALUE;
            boolean foundNewBest = false; // Cờ đánh dấu
            
            // Dùng 1 vòng lặp duy nhất để làm tất cả việc thống kê
            for (int k = 0; k < numAnts; k++) {
                int cost = ants[k].getNumberOfColorsUsed();
                sumCosts += cost;
                
                if (cost < minCostInLoop) {
                	minCostInLoop = cost;
                }
                
                // Nếu tìm thấy kỷ lục toàn cục mới
                if (cost < bestSolutionQuality) {
                    bestSolutionQuality = cost;
                    System.arraycopy(ants[k].getSolution(), 0, bestSolution, 0, numNodes);
                    foundNewBest = true; // Bật cờ lên
                }
            }
            
            // C. Tính toán các chỉ số thống kê (sau khi vòng lặp kiến kết thúc)
            double currentAverage = (double)sumCosts / numAnts;
            // (Nếu cần tính Std thì thêm vòng lặp phụ ở đây, nhưng để log gọn thì tạm bỏ qua)

            // D. Ghi lại lịch sử (Quay phim)
            // QUAN TRỌNG: Nằm ngoài vòng lặp k, chỉ thực hiện 1 lần mỗi thế hệ
			if (foundNewBest || i == 0 || (i + 1) % 5 == 0) { // Lưu dày hơn chút để xem cho mượt
			                
                // --- TÍNH ĐỘ TỰ TIN (CONFIDENCE) DỰA TRÊN PHEROMONE ---
                double[] confidence = new double[numNodes];
                for (int n = 0; n < numNodes; n++) {
                    int selectedColor = bestSolution[n];
                    if (selectedColor == -1) {
                        confidence[n] = 0.1; // Chưa tô thì mờ tịt
                    } else {
                        double p_best = pheromoneMatrix[n][selectedColor];
                        double p_total = 0;
                        for (int c = 0; c < numColors; c++) p_total += pheromoneMatrix[n][c];
                        
                        // Tỷ lệ mùi hương của màu này so với tổng thể
                        confidence[n] = (p_total > 0) ? (p_best / p_total) : 0.1;
                    }
                }

                history.add(new SimulationStep(
                    (i + 1), 
                    bestSolutionQuality, 
                    bestSolution.clone(),
                    confidence // <-- Truyền mảng độ đậm nhạt vào
                ));
            }
            // E. In log báo cáo (Mỗi 10 vòng)
            if ((i + 1) % 10 == 0 || (i + 1) == maxIterations) {
                System.out.printf("   [Running] Iter %03d/%d: Best Colors=%d, Loop Best=%d, Avg=%.2f%n", 
                        (i + 1), maxIterations, bestSolutionQuality, minCostInLoop, currentAverage);
            }
		}
		
        // 3. KẾT THÚC: Trả về trace chi tiết của con tốt nhất để diễn hoạt
        // Bạn chưa có SimulationResponse nhận 3 tham số (quality, solution, trace) 
        // ở code cũ của bạn là 3 tham số: quality, solution, HISTORY. 
        // Nhưng nếu bạn muốn cả Trace và History thì SimulationResponse cần 4 tham số hoặc gộp.
        
        // GIẢ SỬ SimulationResponse của bạn nhận 3 tham số: (int, int[], List<History>)
        // Vì History đã chứa snapshot rồi nên Frontend có thể dùng History để diễn hoạt tua nhanh.
        
        // Nếu Frontend của bạn cần "NodeColorAction" (Trace từng bước), ta tạo thêm:
        List<NodeColorAction> trace = new ArrayList<>();
		for (int i = 0; i < bestSolution.length; i++) {
		     trace.add(new NodeColorAction(i, bestSolution[i], i));
		}
        
        // Lưu ý: Constructor này phải khớp với file SimulationResponse.java của bạn
        // Nếu file đó đang là (quality, solution, history) thì truyền history vào.
        // Nếu file đó đang là (quality, solution, detailedTrace) thì truyền trace vào.
        // Dưới đây tôi truyền cả 2 (giả sử bạn đã sửa DTO) hoặc bạn chọn 1 cái phù hợp.
        
        // PA1: Dùng History (Chiếu phim tua nhanh từng thế hệ) -> KHUYÊN DÙNG CHO NCKH
		// Cuối hàm solve
		int finalColorsUsed = countUniqueColors(bestSolution);
		int finalConflicts = calculateTotalConflicts(bestSolution, nodes);

		// Trả về đủ 4 thông số cho Frontend: 
		// (Chất lượng, Mảng lời giải, Lịch sử cuộn phim, Số lỗi xung đột)
		
		
	    for (int i = 0; i < numNodes; i++) {
	        // i chính là số bước, lấy nodeId từ mảng thứ tự đi walkingOrder
	        int targetNodeId = walkingOrder[i]; 
	        trace.add(new NodeColorAction(targetNodeId, bestSolution[targetNodeId], i));
	    }
	    
		return new SimulationResponse(finalColorsUsed, bestSolution, history, finalConflicts,trace);

	}
	
	private void initializePheromones(double[][] pheromoneMatrix, int numNodes, int numColors, double initialPheromone) {
		for(int i=0;i<numNodes;i++) {
			for(int j=0;j<numColors;j++) {
				pheromoneMatrix[i][j]=initialPheromone;
			}
		}
		
	}
	private void constructSolutions(Ant[] ants, int numNodes, double[][] matrix, double alpha, List<Node> nodes, int numColors, int[] walkingOrder) {
	    for (Ant ant : ants) ant.reset();
	    
	    // Kiến đi theo thứ tự thông minh
	    for (int i = 0; i < numNodes; i++) {
	        int targetNodeId = walkingOrder[i]; // Lấy ID của Node ưu tiên
	        for (Ant ant : ants) {
	            int selectedColor = selectNextColor(ant, targetNodeId, matrix, alpha, nodes, numColors);
	            ant.setColor(targetNodeId, selectedColor);
	        }
	    }
	}
	private void updatePheromones(int numNodes,double[][] pheromoneMatrix,double evaporationRate,Ant[] ants,int numColors) {
		for(int i=0;i<numNodes;i++) {
			for(int j=0;j<numColors;j++) {
				 pheromoneMatrix[i][j] *= (1.0 - evaporationRate);
			}
		}
		for(Ant ant: ants) {
			int numColorsUsed = ant.getNumberOfColorsUsed();
			double pheromoneToAdd = 1.0 / numColorsUsed;
			int[] solution = ant.getSolution();
			for(int i =0;i<solution.length;i++) {
				int color=solution[i];
				 pheromoneMatrix[i][color] += pheromoneToAdd;
			}
		}
	}
	private int selectNextColor(Ant ant,int nodeId,double [][] pheromoneMatrix,double alpha,List<Node> nodes,int numColors) {
		List<Integer> validColors=findValidColors(ant, nodeId,nodes,numColors);
		if (validColors.size()==1) {
			return validColors.get(0);
		}
		double[] probabilities = new double[validColors.size()];
		double probabilitiesSum = 0.0;
		for(int i=0;i<validColors.size();i++) {
			int color=validColors.get(i);
			double pheromone =  pheromoneMatrix[nodeId][color];
			double score = Math.pow(pheromone,  alpha);
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
 