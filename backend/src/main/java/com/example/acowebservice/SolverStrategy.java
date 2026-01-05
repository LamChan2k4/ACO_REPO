package com.example.acowebservice;

import java.util.ArrayList;
import java.util.List;

public interface SolverStrategy {

	 // 1. Sửa hàm trừu tượng: Nhận vào SimulationRequest
    SimulationResponse solve(SimulationRequest request);

    // 2. Sửa hàm thực nghiệm: Cũng nhận vào Request
    default String runExperiment(SimulationRequest request) {
        
        // Lấy số lần chạy từ request (nhớ thêm getter numberOfRuns bên request nếu chưa có)
        int numberOfRuns = request.getNumberOfRuns();
        
        StringBuilder report = new StringBuilder();
        List<Integer> bestResults = new ArrayList<>();
        List<Long> runTimes = new ArrayList<>();
        
        report.append(String.format("🧪 THỰC NGHIỆM: %s (%d lần)\n", this.getClass().getSimpleName(), numberOfRuns));

        for (int run = 1; run <= numberOfRuns; run++) {
            long startTime = System.currentTimeMillis();
            
            // --- ĐÂY LÀ CHỖ QUAN TRỌNG ---
            // Gọi hàm solve với toàn bộ request
            SimulationResponse result = this.solve(request);
            
            long endTime = System.currentTimeMillis();
            bestResults.add(result.getBestQuality());
            runTimes.add(endTime - startTime);
            report.append(String.format("🏃 Run %d: %d colors (%dms)\n", run, result.getBestQuality(), (endTime-startTime)));
        }
        
        // ... (Giữ nguyên đoạn tính toán thống kê Avg, Min, Max phía dưới) ...
        double avg = bestResults.stream().mapToInt(i->i).average().orElse(0);
        report.append(String.format("📊 Average: %.2f colors", avg));
        
        return report.toString();
    }
}
