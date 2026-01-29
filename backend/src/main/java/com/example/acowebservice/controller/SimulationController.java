package com.example.acowebservice.controller;

import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.example.acowebservice.*;

@RestController
@CrossOrigin(origins = "*")
public class SimulationController {

    public SimulationController() {
        System.out.println("====================================================");
        System.out.println(">>> 🚀 [SERVER] Controller Ready with Task Management!");
        System.out.println("====================================================");
    }

    @Autowired private TaskControlService taskControlService;
    @Autowired private GAService gaService;
    @Autowired private AntSystemService asService;
    @Autowired private ACSService acsService;
    @Autowired private MMASService mmasService;
    @Autowired private SimpleGreedyService simpleGreedyService;
    @Autowired private DimacsService dimacsService;

    /**
     * API 1: Chạy mô phỏng qua dữ liệu JSON (đồ thị vẽ tay)
     */
    @PostMapping("/api/simulate")
    public Object runSimulation(@RequestBody SimulationRequest request) {
        // --- QUY TRÌNH QUẢN LÝ LUỒNG ---
        taskControlService.interruptExistingTask();         // Dừng task cũ nếu có
        taskControlService.registerTask(Thread.currentThread()); // Đăng ký task hiện tại

        String algo = (request.getAlgorithm() != null) ? request.getAlgorithm() : "AS";
        System.out.println("\n>>> [INPUT] UI REQUEST: " + algo.toUpperCase());

        SolverStrategy solver = selectSolver(algo);

        if (request.getNumberOfRuns() != null && request.getNumberOfRuns() > 1) {
            System.out.println(">>> Chế độ: Thực nghiệm (" + request.getNumberOfRuns() + " lần)");
            return Collections.singletonMap("experimentReport", solver.runExperiment(request));
        } else {
            SimulationResponse response = solver.solve(request);
            
            System.out.println(">>> ✅ [COMPLETE] Best: " + response.getBestQuality() + " colors");
            System.out.println(">>> ⏱️ Performance: " + response.getExecutionTimeMs() + " ms");
            return response;
        }
    }

    /**
     * API 2: Giải bài toán qua file DIMACS (.col)
     */
    @PostMapping("/api/aco/solve-dimacs")
    public ResponseEntity<SimulationResponse> solveWithDimacs(
            @RequestParam("file") MultipartFile file,
            @RequestParam("algorithm") String algorithm,
            @RequestParam("numAnts") int numAnts,
            @RequestParam("maxIterations") int maxIterations,
            @RequestParam("numColors") int numColors,
            @RequestParam("alpha") double alpha,
            @RequestParam("beta") double beta,
            @RequestParam("evaporation") double evaporation
    ) {
        // --- QUY TRÌNH QUẢN LÝ LUỒNG ---
        taskControlService.interruptExistingTask();
        taskControlService.registerTask(Thread.currentThread());

        System.out.println("\n>>> 📁 [DIMACS] Processing file: " + file.getOriginalFilename());
        
        try {
            List<Node> nodes = dimacsService.parseDimacsFile(file);
            System.out.println(">>> Nodes parsed: " + nodes.size());

            // Build request object
            SimulationRequest request = new SimulationRequest();
            request.setNodes(nodes);
            request.setAlgorithm(algorithm);
            request.setNumAnts(numAnts);
            request.setMaxIterations(maxIterations);
            request.setNumColors(numColors);
            request.setAlpha(alpha);
            request.setBeta(beta);
            request.setEvaporation(evaporation);

            SolverStrategy solver = selectSolver(algorithm);
            System.out.println(">>> 🧠 Algorithm in Progress: " + algorithm.toUpperCase());
            
            SimulationResponse response = solver.solve(request);

            System.out.println(">>> 🎯 [FINAL] Success! Best: " + response.getBestQuality() + " colors");
            System.out.println(">>> 🕒 Execution Time: " + response.getExecutionTimeMs() + " ms");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println(">>> ❌ Error processing DIMACS: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * API 3: Dọn dẹp khẩn cấp (Dùng khi Browser Refresh/Close)
     */
    @GetMapping("/api/aco/stop")
    public ResponseEntity<String> stopComputation() {
        System.out.println(">>> 🛑 [SIGNAL] Nhận lệnh dừng từ hệ thống...");
        taskControlService.interruptExistingTask();
        return ResponseEntity.ok("All background tasks have been flagged for termination.");
    }

    /**
     * Lựa chọn chiến thuật dựa trên algorithm name
     */
    private SolverStrategy selectSolver(String algoType) {
        if (algoType == null) return asService;
        
        switch (algoType.toUpperCase()) {
            case "ACS": return acsService;
            case "GA": return gaService;
            case "MMAS": return mmasService;
            case "SIMPLEGREEDY":
            case "GREEDY": return simpleGreedyService;
            case "AS":
            default: return asService;
        }
    }
}