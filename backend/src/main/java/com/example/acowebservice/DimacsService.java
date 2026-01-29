package com.example.acowebservice;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Service
public class DimacsService {

    /**
     * Chuyển đổi file .col (DIMACS) thành List<Node>
     * Lưu ý: File DIMACS đếm ID từ 1, chúng ta sẽ chuyển về đếm từ 0 để khớp mảng.
     */
    public List<Node> parseDimacsFile(MultipartFile file) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()));
        String line;
        List<Node> nodes = new ArrayList<>();
        int numNodes = 0;

        while ((line = reader.readLine()) != null) {
            line = line.trim();
            // Bỏ qua dòng trống hoặc dòng chú thích bắt đầu bằng 'c'
            if (line.isEmpty() || line.startsWith("c")) continue;

            String[] parts = line.split("\\s+");

            // 1. Dòng thông số: p edge [số nodes] [số cạnh]
            if (line.startsWith("p")) {
                numNodes = Integer.parseInt(parts[2]);
                for (int i = 0; i < numNodes; i++) {
                    // Dùng constructor của bạn: Node(x, y, id)
                    // Vì DIMACS không có tọa độ nên để mặc định 0.0, 0.0
                    nodes.add(new Node(0.0, 0.0, i)); 
                }
            } 
            // 2. Dòng định nghĩa cạnh: e [node u] [node v]
            else if (line.startsWith("e")) {
                // Ép kiểu ID về index 0-based
                int u = Integer.parseInt(parts[1]) - 1;
                int v = Integer.parseInt(parts[2]) - 1;

                if (u >= 0 && u < numNodes && v >= 0 && v < numNodes) {
                    // Dùng method addNeighbor đã có trong class Node của bạn
                    nodes.get(u).addNeighbor(v);
                    nodes.get(v).addNeighbor(u);
                }
            }
        }
        reader.close();
        return nodes;
    }
}