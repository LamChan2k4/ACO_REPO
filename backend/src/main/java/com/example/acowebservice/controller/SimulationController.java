package com.example.acowebservice.controller;
import java.util.Collections;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.acowebservice.ACSService;
import com.example.acowebservice.AntSystemService;
import com.example.acowebservice.GAService;
import com.example.acowebservice.SimulationRequest;
import com.example.acowebservice.SimulationResponse;
import com.example.acowebservice.SolverStrategy;

@RestController
public class SimulationController {
	public SimulationController() {
        System.out.println(">>> ✅ SPRING DA TIM THAY SimulationController !!");
    }

    @Autowired
    private GAService gaService;
    
    @Autowired
    private AntSystemService asService;
     
    @Autowired
    private ACSService acsService;

    @PostMapping("/api/simulate")
    public Object runSimulation(@RequestBody SimulationRequest request) {
        
        SolverStrategy solver;

        // Xác định thuật toán
        String algoType = (request.getAlgorithm() != null) ? request.getAlgorithm().toUpperCase() : "AS";

        switch (algoType) {
            case "ACS":
                solver = acsService; 
                System.out.println(">>> Đang chạy thuật toán: Ant Colony System (ACS)");
                break;
                
            case "GA":
                solver = gaService; 
                System.out.println(">>> Đang chạy thuật toán: Genetic Algorithm (GA)");
                break;
                
            case "AS":
            default:
                solver = asService; 
                System.out.println(">>> Đang chạy thuật toán: Ant System (AS)");
                break;
        }

        // Chạy Thực Nghiệm hoặc Chạy Thường
        if (request.getNumberOfRuns() > 1) {
            
            // LƯU Ý: Nếu Interface SolverStrategy của bạn đã sửa runExperiment để nhận (request) 
            // thì dùng dòng dưới đây. Nếu chưa sửa thì giữ nguyên cách truyền tham số cũ.
            // (Theo bài hướng dẫn trước là chúng ta đã sửa Interface rồi nên viết gọn thế này):
            
            String report = solver.runExperiment(request);
            
            return Collections.singletonMap("experimentReport", report);
            
        } else {
            // SỬA LỖI Ở ĐÂY: Truyền cả đối tượng request vào hàm solve
            return solver.solve(request);
        }
    }
	}