package com.example.acowebservice;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SimpleGreedyService implements SolverStrategy {

    @Override
    public SimulationResponse solve(SimulationRequest req) {
        // --- 1. KHỞI TẠO VÀ BẤM GIỜ NANO ---
        long startNano = System.nanoTime(); 
        
        List<Node> nodes = req.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            return new SimulationResponse();
        }

        // Lọc node rác bảo vệ server
        List<Node> validNodes = nodes.stream()
                .filter(n -> n != null && n.getId() != null)
                .collect(Collectors.toList());
            
        if (validNodes.isEmpty()) {
            return new SimulationResponse(0, new int[0], 0, new ArrayList<>(), new ArrayList<>(), 0, validNodes);
        }

        int numNodes = validNodes.size();
        int[] solution = new int[numNodes];
        Arrays.fill(solution, -1);

        // --- 2. TIỀN XỬ LÝ (WELCH-POWELL) ---
        // Sắp xếp các node theo bậc giảm dần (Đỉnh khó tô trước)
        List<Node> sortedNodes = new ArrayList<>(validNodes);
        sortedNodes.sort((n1, n2) -> n2.getNeighbors().size() - n1.getNeighbors().size());

        // ✅ TỐI ƯU: Sử dụng mảng boolean dùng chung để check màu hàng xóm
        // Không tạo mới HashSet trong vòng lặp để cứu chip Pentium
        boolean[] usedColors = new boolean[numNodes + 1]; 

        // --- 3. VÒNG LẶP TÔ MÀU CHÍNH ---
        for (Node node : sortedNodes) {
            // Kiểm tra tín hiệu dừng từ hệ thống (F5/Refresh)
            if (Thread.currentThread().isInterrupted()) {
                System.out.println("🛑 [LOG] SimpleGreedy dừng khẩn cấp.");
                return new SimulationResponse();
            }

            // Reset mảng đánh dấu (Nhanh hơn tạo mới HashSet)
            Arrays.fill(usedColors, false);

            // Đánh dấu các màu mà hàng xóm đã dùng
            for (int neighborId : node.getNeighbors()) {
                // Kiểm tra neighborId có hợp lệ trong mảng solution không
                if (neighborId >= 0 && neighborId < numNodes) {
                    int color = solution[neighborId];
                    if (color != -1) {
                        usedColors[color] = true;
                    }
                }
            }

            // Tìm màu nhỏ nhất chưa bị hàng xóm dùng
            int color = 0;
            while (usedColors[color]) {
                color++;
            }
            solution[node.getId()] = color;
        }

        long durationMs = (System.nanoTime() - startNano) / 1_000_000;
        
        if (durationMs == 0) {
            System.out.println(">>> [SimpleGreedy] Super fast! Execution < 1ms");
        }

        // --- 4. TẠO TRACE REPLAY (CẮT TỈA CHO ĐỒ THỊ LỚN) ---
        List<NodeColorAction> trace = new ArrayList<>();
        // Chỉ gửi Trace nếu đồ thị nhỏ (< 300 nốt) để tránh lỗi Broken Pipe (JSON quá nặng)
        if (numNodes < 300) {
            for (int i = 0; i < sortedNodes.size(); i++) {
                int nid = sortedNodes.get(i).getId();
                trace.add(new NodeColorAction(nid, solution[nid], i));
            }
        }

        // Trả về kết quả (Greedy không có History tiến hóa)
        return new SimulationResponse(
                countUniqueColors(solution), 
                solution, 
                0,                 // Conflicts của tham lam luôn là 0 (vì nó né màu hàng xóm)
                new ArrayList<>(), // History rỗng
                trace, 
                durationMs,
                validNodes 
            );
    }
    
    private int countUniqueColors(int[] solution) {
        Set<Integer> colors = new HashSet<>();
        for (int c : solution) {
            if (c != -1) colors.add(c);
        }
        return colors.size();
    }
}