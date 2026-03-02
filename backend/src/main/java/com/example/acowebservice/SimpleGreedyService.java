package com.example.acowebservice;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SimpleGreedyService implements SolverStrategy {

    @Override
    public SimulationResponse solve(SimulationRequest req) {
        // --- 1. BẮT ĐẦU BẤM GIỜ NANO (Độ chính xác cao cho dân DS) ---
        long startNano = System.nanoTime(); 
        
        List<Node> nodes = req.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            return new SimulationResponse();
        }

        // Lọc node bảo vệ Server-kun
        List<Node> validNodes = nodes.stream()
                .filter(n -> n != null && n.getId() != null)
                .collect(Collectors.toList());
            
        if (validNodes.isEmpty()) {
            return new SimulationResponse(0, new int[0], 0, new ArrayList<>(), new ArrayList<>(), 0, validNodes);
        }

        int numNodes = validNodes.size();
        int colorLimit = (req.getNumColors() != null) ? req.getNumColors() : numNodes;
        int[] solution = new int[numNodes];
        Arrays.fill(solution, -1);

        // --- 2. TIỀN XỬ LÝ: SẮP XẾP BẬC ĐỈNH (WELCH-POWELL) ---
        List<Node> sortedNodes = new ArrayList<>(validNodes);
        // Sắp xếp giảm dần: Node nhiều hàng xóm nhất đứng đầu
        sortedNodes.sort((n1, n2) -> n2.getNeighbors().size() - n1.getNeighbors().size());

        // ✅ TỐI ƯU SIÊU CẤP: Sử dụng mảng boolean đánh dấu thay cho HashSet
        // Pentium xử lý mảng này cực kỳ "nhàn nhã"
        boolean[] usedByNeighbors = new boolean[numNodes + 1];

        // --- 3. VÒNG LẶP TÔ MÀU THAM LAM ---
        for (Node node : sortedNodes) {
            // Kiểm tra tín hiệu ngắt (Dành cho việc dọn Task từ Controller)
            if (Thread.currentThread().isInterrupted()) {
                System.out.println("🛑 [LOG] SimpleGreedy dừng khẩn cấp!");
                return new SimulationResponse();
            }

            // Làm sạch mảng đánh dấu trước khi xét hàng xóm của Node hiện tại
            Arrays.fill(usedByNeighbors, false);

            for (int neighborId : node.getNeighbors()) {
                // Chỉ đánh dấu nếu neighborId nằm trong giải số nốt hợp lệ
                if (neighborId >= 0 && neighborId < numNodes) {
                    int assignedColor = solution[neighborId];
                    if (assignedColor != -1) {
                        usedByNeighbors[assignedColor] = true;
                    }
                }
            }

            // Quy tắc tham lam: Chọn màu số nhỏ nhất còn trống
            int color = 0;
            while (usedByNeighbors[color]) {
                color++;
            }
            solution[node.getId()] = color;
        }

        // ⏱️ Kết thúc bấm giờ
        long endTime = System.nanoTime();
        long durationMs = (endTime - startNano) / 1_000_000;

        // --- 4. TRACE VÀ TRẢ VỀ DỮ LIỆU ---
        List<NodeColorAction> trace = new ArrayList<>();
        // 🔒 CHỐNG LỖI BROKEN PIPE: Đồ thị lớn thì không gửi DetailedTrace qua mạng
        if (numNodes < 300) {
            for (int i = 0; i < sortedNodes.size(); i++) {
                int nid = sortedNodes.get(i).getId();
                trace.add(new NodeColorAction(nid, solution[nid], i));
            }
        }

        System.out.println(">>> [SIMPLEGREEDY] Execution: " + durationMs + "ms | Colors: " + countUniqueColors(solution));

        // Tính Conflicts thật tế dựa trên Colors Pool ông đã thiết lập trên Web
        int actualConflicts = calculateTotalConflicts(solution, validNodes);

        return new SimulationResponse(
                countUniqueColors(solution),    // 1. bestQuality
                solution,                       // 2. bestSolution
                actualConflicts,                // 3. conflicts (Dành cho R&D)
                new ArrayList<>(),              // 4. history (Tham lam không có lịch sử tiến hóa)
                trace,                          // 5. detailedTrace
                durationMs,                     // 6. executionTimeMs
                validNodes                      // 7. nodes (🔴 Để Frontend vẽ map Edges)
            );
    }
    
    private int calculateTotalConflicts(int[] solution, List<Node> nodes) {
        int conflicts = 0;
        for (Node u : nodes) {
            int uId = u.getId();
            for (int vId : u.getNeighbors()) {
                if (solution[uId] != -1 && solution[uId] == solution[vId]) conflicts++;
            }
        }
        return conflicts / 2;
    }

    private int countUniqueColors(int[] solution) {
        Set<Integer> unique = new HashSet<>();
        for (int c : solution) {
            if (c != -1) unique.add(c);
        }
        return unique.size();
    }
}