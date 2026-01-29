package com.example.acowebservice;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
public class SimpleGreedyService implements SolverStrategy {

    @Override
    public SimulationResponse solve(SimulationRequest req) {
    	long startNano = System.nanoTime(); 
	    
        List<Node> nodes = req.getNodes();
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
        int[] solution = new int[numNodes];
        Arrays.fill(solution, -1);

        // Thuật toán: Welch-Powell (Bậc cao tô trước)
        List<Node> sortedNodes = new ArrayList<>(nodes);
        sortedNodes.sort((n1, n2) -> n2.getNeighbors().size() - n1.getNeighbors().size());

        for (Node node : sortedNodes) {
            Set<Integer> neighborColors = new HashSet<>();
            for (int neighborId : node.getNeighbors()) {
                if (solution[neighborId] != -1) neighborColors.add(solution[neighborId]);
            }
            // Chọn màu thấp nhất có thể
            int color = 0;
            while (neighborColors.contains(color)) {
                color++;
            }
            solution[node.getId()] = color;
        }

        long endNano = System.nanoTime();
	    long duration = (endNano - startNano) / 1_000_000; 
	    if (duration == 0) {
	        System.out.println(">>> [" + this.getClass().getSimpleName() + "] Super fast! Microseconds: " + (endNano - startNano) / 1000);
	    }
        // Tạo trace giả lập (vì greedy chạy 1 lần xong luôn)
        List<NodeColorAction> trace = new ArrayList<>();
        for (int i = 0; i < sortedNodes.size(); i++) {
        	if (Thread.currentThread().isInterrupted()) {
                System.out.println("🛑 [LOG] SGS bị buộc dừng để giải phóng CPU.");
                // Trả về kết quả rỗng hoặc null thay vì chạy tiếp
                return new SimulationResponse(0, new int[0], 0, new ArrayList<>(), new ArrayList<>(), 0, new ArrayList<>());
            }
            int nid = sortedNodes.get(i).getId();
            trace.add(new NodeColorAction(nid, solution[nid], i));
        }

        return new SimulationResponse(
                countUniqueColors(solution), 
                solution, 
                0,              // Conflicts của tham lam thường là 0 nếu code chuẩn
                new ArrayList<>(), // History trống vì tham lam không lặp
                trace, 
                duration,
                validNodes 
            );
    }
    
    private int countUniqueColors(int[] solution) {
        Set<Integer> colors = new HashSet<>();
        for (int c : solution) if(c != -1) colors.add(c);
        return colors.size();
    }
}