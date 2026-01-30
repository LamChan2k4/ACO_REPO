package com.example.acowebservice.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.example.acowebservice.*;
import java.util.Collections;
import java.util.List;

@RestController
@CrossOrigin(origins = "*")
public class SimulationController {

    @Autowired private TaskControlService taskControlService;
    @Autowired private GAService gaService;
    @Autowired private AntSystemService asService;
    @Autowired private ACSService acsService;
    @Autowired private MMASService mmasService;
    @Autowired private SimpleGreedyService simpleGreedyService;
    @Autowired private DimacsService dimacsService;

    @PostMapping("/api/simulate")
    public ResponseEntity<?> runSimulation(@RequestBody SimulationRequest request) {
        taskControlService.interruptExistingTask();
        taskControlService.registerTask(Thread.currentThread());

        try {
            SolverStrategy solver = selectSolver(request.getAlgorithm());
            SimulationResponse response = solver.solve(request);

            if (Thread.currentThread().isInterrupted()) return ResponseEntity.status(204).build();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return handleException(e);
        }
    }

    @PostMapping("/api/aco/solve-dimacs")
    public ResponseEntity<?> solveWithDimacs(
            @RequestParam("file") MultipartFile file,
            @RequestParam("algorithm") String algorithm,
            @RequestParam("numAnts") int numAnts,
            @RequestParam("maxIterations") int maxIterations,
            @RequestParam("numColors") int numColors,
            @RequestParam("alpha") double alpha,
            @RequestParam("beta") double beta,
            @RequestParam("evaporation") double evaporation,
            @RequestParam(value = "q0", defaultValue = "0.9") Double q0,
            @RequestParam(value = "mutationRate", defaultValue = "0.05") Double mutationRate,
            @RequestParam(value = "tournamentSize", defaultValue = "5") Integer tournamentSize
    ) {
        taskControlService.interruptExistingTask();
        taskControlService.registerTask(Thread.currentThread());

        try {
            List<Node> nodes = dimacsService.parseDimacsFile(file);
            SimulationRequest request = new SimulationRequest();
            request.setNodes(nodes);
            request.setAlgorithm(algorithm);
            request.setNumAnts(numAnts);
            request.setMaxIterations(maxIterations);
            request.setNumColors(numColors);
            request.setAlpha(alpha);
            request.setBeta(beta);
            request.setEvaporation(evaporation);
            request.setQ0(q0);
            request.setMutationRate(mutationRate);
            request.setTournamentSize(tournamentSize);

            SolverStrategy solver = selectSolver(algorithm);
            SimulationResponse response = solver.solve(request);

            if (Thread.currentThread().isInterrupted()) return ResponseEntity.status(204).build();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return handleException(e);
        }
    }

    @GetMapping("/api/aco/stop")
    public ResponseEntity<String> stopAll() {
        taskControlService.interruptExistingTask();
        return ResponseEntity.ok("Cleared");
    }

    private ResponseEntity<?> handleException(Exception e) {
        if (Thread.currentThread().isInterrupted()) {
            System.out.println(">>> [INFO] Luồng đã dừng thành công, hủy bỏ phản hồi.");
            return ResponseEntity.status(204).build();
        }
        e.printStackTrace();
        return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
    }

    private SolverStrategy selectSolver(String algo) {
        switch (algo.toUpperCase()) {
            case "ACS": return acsService;
            case "MMAS": return mmasService;
            case "GA": return gaService;
            case "SIMPLEGREEDY": return simpleGreedyService;
            default: return asService;
        }
    }
}