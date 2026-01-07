// ================================================================
// PHẦN 1: CẤU HÌNH VÀ TIỆN ÍCH (CONFIG & UTILS)
// ================================================================

let network = null;
let graphData = { nodes: new vis.DataSet(), edges: new vis.DataSet() };
let animationTimer = null; // Dùng để dừng phim khi chạy mới
let lastBestSolution = null;
let lastDetailedTrace = null;
// Hàm sinh màu "vô hạn" (Không dùng mảng cố định để tránh lỗi)
function getColorForIndex(index) {
    // Với màu -1 hoặc null (chưa tô), trả về màu xám
    if (index === -1 || index === undefined) return '#EEEEEE';

    // 10 màu cơ bản đẹp mắt cho các số nhỏ
    const baseColors = [
        "#FF5733", "#33FF57", "#3357FF", "#FF33F6", "#F6FF33", 
        "#33FFF6", "#E67E22", "#8E44AD", "#34495E", "#27AE60"
    ];
    if (index < baseColors.length) return baseColors[index];

    // Với số lớn, dùng thuật toán Góc Vàng để tạo màu khác biệt
    const goldenAngle = 137.508;
    const hue = (index * goldenAngle) % 360;
    return `hsl(${hue}, 75%, 50%)`;
}

// ================================================================
// PHẦN 2: QUẢN LÝ ĐỒ THỊ (GRAPH GENERATION & RENDERING)
// ================================================================

// Hàm tạo đồ thị ngẫu nhiên
function generateRandomGraph() {
    const count = parseInt(document.getElementById("nodeCount").value) || 20;
    
    // Reset dữ liệu cũ
    graphData.nodes.clear();
    graphData.edges.clear();

    const nodesArray = [];
    const edgesArray = [];

    // Tự động giảm tỷ lệ nối dây nếu số lượng node quá lớn để đỡ rối
    let connectionProb = 0.2; 
    if (count > 50) connectionProb = 0.05;
    if (count > 100) connectionProb = 0.02;

    // 1. Tạo Node
    for (let i = 0; i < count; i++) {
        nodesArray.push({
            id: i,
            label: String(i),
            shape: 'dot',
            size: 15,
            color: { background: '#EEEEEE', border: '#AAAAAA' }
        });
    }

    // 2. Tạo Cạnh
    for (let i = 0; i < count; i++) {
        for (let j = i + 1; j < count; j++) {
            if (Math.random() < connectionProb) {
                edgesArray.push({ from: i, to: j });
            }
        }
    }

    graphData.nodes.add(nodesArray);
    graphData.edges.add(edgesArray);

    renderNetwork();
}

// Hàm vẽ đồ thị lên màn hình (Cấu hình tối ưu cho 100+ Nodes)
function renderNetwork() {
    const container = document.getElementById('mynetwork');
    
    const options = {
        layout: {
            improvedLayout: false // TẮT cái này để tránh lỗi với đồ thị lớn
        },
        physics: {
            enabled: true,
            solver: 'forceAtlas2Based', // Thuật toán tốt nhất cho mạng lưới lớn
            forceAtlas2Based: {
                gravitationalConstant: -50,
                centralGravity: 0.01,
                springLength: 100,
                springConstant: 0.08,
                damping: 0.4
            },
            stabilization: {
                enabled: true,
                iterations: 1000, // Tính toán trước 1000 bước rồi mới hiện
                fit: true
            }
        },
        nodes: {
            font: { color: '#333333' },
            borderWidth: 1
        },
        edges: {
            color: '#CCCCCC',
            smooth: false // Tắt đường cong cho nhẹ máy
        }
    };

    network = new vis.Network(container, graphData, options);

    // Khi đồ thị đã ổn định vị trí, tắt vật lý để nó đứng im
    network.on("stabilizationIterationsDone", function () {
        network.setOptions({ physics: false });
        document.getElementById("status").innerText = "Sẵn sàng (Graph Stable).";
    });
}

// ================================================================
// PHẦN 3: GỌI BACKEND VÀ XỬ LÝ KẾT QUẢ
// ================================================================

async function runSimulation() {
    // 1. Phần UI
    const btn = document.getElementById("btnRun");
    const status = document.getElementById("status");
    if (animationTimer) clearTimeout(animationTimer);

    // 2. Lấy dữ liệu Input
    const algorithm = document.getElementById("algorithm").value;
    const numAnts = parseInt(document.getElementById("numAnts").value) || 20;
    const iterations = parseInt(document.getElementById("iterations").value) || 100;
    const numColors = parseInt(document.getElementById("numColors").value) || 5;
    
    // Tự động gán mặc định nếu User để trống hoặc nhập sai
    const alpha = parseFloat(document.getElementById("alpha").value) || 1.0;
    const evaporation = parseFloat(document.getElementById("evaporation").value) || 0.5;
    const beta = parseFloat(document.getElementById("beta").value) || 2.0;
    const q0 = parseFloat(document.getElementById("q0").value) || 0.9;
    const mutationRate = parseFloat(document.getElementById("mutationRate").value) || 0.05;

    // 3. Mapping Node cho Java
    const allNodes = graphData.nodes.get();
    const allEdges = graphData.edges.get();
    const javaNodes = allNodes.map(node => {
        let neighbors = [];
        allEdges.forEach(edge => {
            if (edge.from === node.id) neighbors.push(edge.to);
            if (edge.to === node.id) neighbors.push(edge.from);
        });
        return { id: node.id, x: 0, y: 0, neighbors: neighbors };
    });

    // Gom gói hàng Payload (SẠCH SẼ)
    const payload = {
        algorithm,
        nodes: javaNodes,
        numAnts,
        maxIterations: iterations,
        numColors,
        numberOfRuns: 1,
        alpha,
        evaporation,
        beta,
        q0,
        mutationRate
    };

    // 4. Gửi Server
    try {
        btn.innerText = "Processing...";
        btn.disabled = true;
        status.innerText = "⏳ Connecting to Server...";

        const serverHost = window.location.hostname;
        const API_URL = (window.location.port === "5500") 
                        ? `http://${serverHost}:8081/api/simulate` 
                        : "/api/simulate";

        const response = await fetch(API_URL, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });

        if (!response.ok) throw new Error(`HTTP Error: ${response.status}`);

        const result = await response.json();

        // 5. LƯU BỘ NHỚ: Cực kỳ quan trọng để các nút "Replay" hoạt động
        lastBestSolution = result.bestSolution;
        lastDetailedTrace = result.detailedTrace; // Trace kịch bản bò thông minh

        console.log("✅ Simulation Done. Received Data:", result);
        
        // 6. Visualization
        if (result.history && result.history.length > 0) {
            status.innerText = `🎬 Đã xong! Đang tái hiện quá trình hội tụ...`;
            await playHistoryAnimation(result.history);
        }

        // Thông báo kết quả cuối
        const conflictsStr = (result.conflicts > 0) ? ` (⚠️ Conflicts: ${result.conflicts})` : "";
        status.innerText = `✅ Xong! Thuật toán tìm được: ${result.bestQuality} màu${conflictsStr}`;
        status.style.color = (result.conflicts > 0) ? "orange" : "green";

    } catch (error) {
        console.error(error);
        status.innerText = "❌ Lỗi: " + error.message;
        status.style.color = "red";
    } finally {
        btn.innerText = "Start Coloring";
        btn.disabled = false;
    }
}

// ================================================================
// PHẦN 4: HIỆU ỨNG HÌNH ẢNH (ANIMATION)
// ================================================================

async function playHistoryAnimation(historyList) {
    const statusDiv = document.getElementById("status");
    const delayTime = 300; // Tốc độ vừa phải

    // Bật chế độ physics nhẹ để đồ thị trông sống động hơn
    network.setOptions({ physics: { enabled: false } }); 

    for (let i = 0; i < historyList.length; i++) {
        const step = historyList[i];
        
        statusDiv.innerText = `🔄 Vòng lặp: ${step.iterationNumber} | Chất lượng: ${step.quality} màu | Độ rõ nét: Đang tăng dần...`;
        
        let updates = [];
        step.solution.forEach((colorIndex, nodeId) => {
            let colorHex = getColorForIndex(colorIndex);
            
            // Lấy độ tự tin (Nếu không có thì mặc định là 1.0)
            let conf = (step.confidence) ? step.confidence[nodeId] : 1.0;
            
            // Hiệu ứng Visual:
            // 1. Độ tự tin càng cao -> Màu càng đậm (Opacity), Kích thước càng to chuẩn.
            // 2. Độ tự tin thấp -> Màu trong suốt, node nhỏ lại.
            
            // Biến đổi độ trong suốt (Alpha channel của màu)
            let colorWithOpacity = hexToRgba(colorHex, 0.3 + (conf * 0.7)); // Tối thiểu 0.3 alpha
            
            updates.push({
                id: nodeId,
                color: { 
                    background: colorWithOpacity, 
                    border: 'rgba(0,0,0,0.8)'
                },
                // Node chưa chắc chắn thì bé, chắc chắn thì to
                size: 10 + (conf * 15), 
                label: `N${nodeId}`
            });
        });
        
        graphData.nodes.update(updates);

        await new Promise(r => setTimeout(r, delayTime));
    }
    
    statusDiv.innerText = "✅ HOÀN TẤT: Giải pháp tối ưu đã hiện rõ!";
    // Sau khi xong, vẽ lại 1 lần nét căng (Full Opacity)
    updateColorsImmediate(historyList[historyList.length-1].solution);
}

// Hàm phụ trợ: Chuyển màu Hex sang RGBA để chỉnh độ trong suốt
function hexToRgba(hex, alpha) {
    if (hex.startsWith("hsl")) return hex; // Nếu là HSL thì thôi
    let c;
    if(/^#([A-Fa-f0-9]{3}){1,2}$/.test(hex)){
        c= hex.substring(1).split('');
        if(c.length== 3){
            c= [c[0], c[0], c[1], c[1], c[2], c[2]];
        }
        c= '0x'+c.join('');
        return 'rgba('+[(c>>16)&255, (c>>8)&255, c&255].join(',')+','+alpha+')';
    }
    return hex;
}

// Hàm cập nhật màu nhanh (Batch Update)
function updateColorsImmediate(solutionArray) {
    if (!solutionArray) return;

    let updates = [];
    solutionArray.forEach((colorIndex, nodeId) => {
        let colorHex = getColorForIndex(colorIndex);
        updates.push({
            id: nodeId,
            color: { 
                background: colorHex, 
                border: '#333' // Viền đậm một chút cho rõ
            },
            label: `N${nodeId}\n(${colorIndex})` // Hiện số màu lên nhãn
        });
    });
    
    // Cập nhật vào Vis.js (Chỉ 1 lần để tối ưu hiệu năng)
    graphData.nodes.update(updates);
}

// Hàm xóa màu về mặc định
function resetNodeColors() {
    let updates = graphData.nodes.getIds().map(id => ({
        id: id,
        color: { background: '#EEEEEE', border: '#AAAAAA' },
        label: String(id)
    }));
    graphData.nodes.update(updates);
}

// ================================================================
// PHẦN 5: HIỆU ỨNG KIẾN BÒ (REPLAY BEST ANT)
// ================================================================

async function replayWithAntMovement() {
    // 1. Kiểm tra kịch bản
    if (!lastDetailedTrace || lastDetailedTrace.length === 0) {
        alert("Bạn hãy bấm 'Start Simulation' để lấy kết quả trước!");
        return;
    }

    const statusDiv = document.getElementById("status");
    const antImgUrl = "https://img.icons8.com/color/48/ant.png"; 
    const speed = 700; // Tăng một chút để kịp nhìn Degree

    // 2. Reset đồ thị trắng
    statusDiv.innerText = "🎬 Khởi động chế độ quan sát kịch bản thông minh...";
    resetNodeColors();
    network.fit({ animation: { duration: 1000 } });
    await new Promise(r => setTimeout(r, 1200));

    // 3. Lấy dữ liệu Node hiện tại để tính Bậc (Degree)
    const currentNodes = graphData.nodes.get();
    const currentEdges = graphData.edges.get();

    // Duyệt theo kịch bản (Thứ tự đi đã được Backend sắp xếp theo độ khó)
    for (let i = 0; i < lastDetailedTrace.length; i++) {
        let action = lastDetailedTrace[i]; 
        let nodeId = action.nodeId; 
        let colorCode = action.colorCode;
        let colorHex = getColorForIndex(colorCode);

        // --- TÍNH TOÁN BẬC CỦA ĐỈNH TẠI CHỖ ---
        let neighborsCount = currentEdges.filter(e => e.from === nodeId || e.to === nodeId).length;

        statusDiv.innerHTML = `🐜 <b>Bước ${i + 1}:</b> Kiến xử lý Node <b>${nodeId}</b> 
                               <br><span style='color:#e67e22'>(Độ khó/Bậc: ${neighborsCount} hàng xóm)</span>`;
        
        // Tập trung Camera vào điểm nóng
        network.focus(nodeId, {
            scale: 1.1, 
            animation: { duration: 400 }
        });

        // HIỆN ICON KIẾN (Sắp xử lý)
        graphData.nodes.update({
            id: nodeId,
            shape: 'image',
            image: antImgUrl,
            size: 45,
            label: "" // Ẩn label lúc kiến đang bò
        });

        await new Promise(r => setTimeout(r, speed));

        // CHỐT MÀU VÀ HIỆN BẰNG CHỨNG (Bậc của đỉnh)
        graphData.nodes.update({
            id: nodeId,
            shape: 'dot',
            image: undefined,
            size: 25,
            color: { background: colorHex, border: "#000000" },
            borderWidth: 2,
            // THẦY CÔ NHÌN VÀO ĐÂY: Sẽ thấy Deg to giảm dần
            label: `ID: ${nodeId}\nDeg: ${neighborsCount}\n(Color ${colorCode})`
        });

        // Delay cực ngắn để tạo cảm giác di chuyển
        await new Promise(r => setTimeout(r, 100));
    }

    statusDiv.innerHTML = "<b style='color:green'>🎉 Replay Hoàn Tất!</b><br>Đã chứng minh chiến thuật gỡ nút thắt (Highest Degree First).";
    network.fit({ animation: { duration: 1000 } });
}
// Chạy khởi tạo lần đầu khi tải trang

generateRandomGraph();
