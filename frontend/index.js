/**
 * AI LAB - ACO GRAPH COLORING FRONTEND (VERSION 3.0 FINAL)
 * Môi trường: Localhost 8081 | Tailscale Funnel Port 80
 */

// ==========================================
// 1. CẤU HÌNH CỔNG KẾT NỐI (BACKEND 8081)
// ==========================================
// Gửi tín hiệu dọn rác ngay khi vừa load web/refresh
window.onload = function() {
    fetch(API_BASE + "/api/aco/stop").then(() => {
        console.log("Hệ thống đã dọn dẹp session cũ.");
        generateRandomGraph();
    });
};
const IS_LAPTOP = window.location.port === "5500" || window.location.port === "5501" || window.location.hostname === "127.0.0.1";
const API_BASE = IS_LAPTOP ? "http://localhost:8081" : ""; 

console.log(`>>> [HỆ THỐNG] Khởi chạy - DevMode: ${IS_LAPTOP}`);

// Tự động hiện/ẩn các ô tham số đặc thù khi chọn Algorithm
document.addEventListener('DOMContentLoaded', () => {
    const algoSelect = document.getElementById('algorithm');
    if (algoSelect) {
        algoSelect.addEventListener('change', function() {
            const algo = this.value;
            // Ẩn hiện div theo id (Hãy đảm bảo HTML có các ID này)
            if (document.getElementById('extra-params-acs')) 
                document.getElementById('extra-params-acs').style.display = (algo === 'ACS') ? 'block' : 'none';
            if (document.getElementById('extra-params-ga')) 
                document.getElementById('extra-params-ga').style.display = (algo === 'GA') ? 'block' : 'none';
        });
    }
});

// ==========================================
// 2. BIẾN TOÀN CỤC (GLOBAL STATE)
// ==========================================
let network = null;
let graphData = {
    nodes: new vis.DataSet(),
    edges: new vis.DataSet()
};
let lastBestSolution = null;
let lastDetailedTrace = null;

// ==========================================
// 3. TIỆN ÍCH MÀU SẮC (VISUAL UTILS)
// ==========================================

function getColorForIndex(index) {
    if (index === -1 || index === undefined) return '#EEEEEE';
    const palette = [
        "#FF5733", "#27AE60", "#2980B9", "#E67E22", "#8E44AD", 
        "#F1C40F", "#1ABC9C", "#C0392B", "#7F8C8D", "#D35400"
    ];
    if (index < palette.length) return palette[index];
    const hue = (index * 137.508) % 360; 
    return `hsl(${hue}, 70%, 50%)`;
}

function hexToRgba(hex, alpha) {
    if (hex.startsWith("hsl")) return hex;
    let r = 0, g = 0, b = 0;
    if (hex.length === 7) {
        r = parseInt(hex.slice(1, 3), 16);
        g = parseInt(hex.slice(3, 5), 16);
        b = parseInt(hex.slice(5, 7), 16);
    }
    return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

// ==========================================
// 4. QUẢN LÝ ĐỒ THỊ (VIS.JS)
// ==========================================

function generateRandomGraph() {
    const count = parseInt(document.getElementById("nodeCount").value) || 20;
    graphData.nodes.clear();
    graphData.edges.clear();
    const nodes = []; const edges = [];
    let prob = count > 50 ? (count > 100 ? 0.02 : 0.05) : 0.15;

    for (let i = 0; i < count; i++) {
        nodes.push({ id: i, label: `${i}`, shape: 'dot', size: 15, color: { background: '#eee' } });
    }
    for (let i = 0; i < count; i++) {
        for (let j = i + 1; j < count; j++) {
            if (Math.random() < prob) edges.push({ from: i, to: j });
        }
    }
    graphData.nodes.add(nodes); graphData.edges.add(edges);
    renderNetwork();
}

function renderNetwork() {
    const container = document.getElementById('mynetwork');
    
    const options = {
        nodes: {
            borderWidth: 1,
            size: 15,
            font: { size: 10, color: '#333' }
        },
        edges: {
            smooth: false, // Phải tắt mượt dây để ko bị giật
            color: { opacity: 0.15 } // Làm mờ dây
        },
        physics: {
            enabled: true, // Lúc đầu cho nó bật để dàn trải...
            solver: 'forceAtlas2Based',
            forceAtlas2Based: {
                gravitationalConstant: -100, // Đẩy nhẹ hơn để ko bị văng xa
                springLength: 80,
                avoidOverlap: 1
            },
            // ✅ QUAN TRỌNG: Cài đặt Stabilization (Ổn định hóa)
            stabilization: {
                enabled: true,
                iterations: 1000, // Thử lắc 1000 lần trước khi hiện ra
                updateInterval: 25,
                onlyDynamicEdges: false,
                fit: true
            }
        },
        interaction: {
            hover: true,
            tooltipDelay: 200
        }
    };

    network = new vis.Network(container, graphData, options);

    // ✅ TRÍ TUỆ GIỮ ỔN ĐỊNH 1: Khi máy vừa lắc xong -> TẮT VẬT LÝ NGAY
    network.on("stabilizationIterationsDone", function () {
        console.log(">>> [LOG] Đã ổn định! Khóa bản đồ.");
        network.setOptions({ physics: false }); 
        document.getElementById("status").innerText = "🔒 Đã khóa vị trí. Có thể kiểm tra Node.";
    });

    // ✅ TRÍ TUỆ GIỮ ỔN ĐỊNH 2: Cứ sau khi kéo thả Node -> Dừng vật lý tiếp
    network.on("dragEnd", function (params) {
        network.setOptions({ physics: false });
    });
    network.on("click", function (params) {
        // Chỉ xử lý khi click vào Node (không click vào khoảng trắng)
        if (params.nodes.length > 0) {
            const nodeId = params.nodes[0];
            const statusDiv = document.getElementById("status");

            // Kiểm tra kỹ biến chứa kết quả
            if (lastBestSolution && Array.isArray(lastBestSolution) && lastBestSolution[nodeId] !== undefined) {
                const colorCode = lastBestSolution[nodeId];
                status.innerHTML = 
                    `📍 <b>INFO:</b> Node ${nodeId} <span style="color:gray">|</span> Màu: <b style="color:${getColorForIndex(colorCode)}; text-shadow: 1px 1px 0 #000;">Code ${colorCode}</b>`;
            } else {
                // Trường hợp chưa chạy thuật toán hoặc biến bị null
                status.innerText = `📍 Node ${nodeId}: Chưa có dữ liệu màu (Vui lòng chạy thuật toán)`;
                console.warn("Chưa có lastBestSolution hoặc ID không khớp:", nodeId);
            }
        }
    });
}

function updateColorsImmediate(solution) {
    if (!solution) return;
    const updates = solution.map((color, nodeId) => ({
        id: nodeId,
        color: { background: getColorForIndex(color), border: '#222' },
        label: `ID:${nodeId}\nC:${color}`
    }));
    graphData.nodes.update(updates);
}

// ==========================================
// 5. GỌI BACKEND JAVA 8081
// ==========================================

async function callSolver(endpoint, options, isLarge = false) {
    const status = document.getElementById("status");
    const bench = document.getElementById("benchmark-card");
    const btn = document.getElementById("btnRun");
    const url = API_BASE + endpoint; 

    status.innerText = "⏳ Bầy kiến Java đang tính toán...";
    btn.disabled = true;

    try {
        const response = await fetch(url, options);
        if (!response.ok) {
            const txt = await response.text();
            throw new Error(`Server ${response.status}: ${txt}`);
        }
        
        const data = await response.json();

        // HIỂN THỊ THỜI GIAN THẬT (Fix lỗi 0ms)
        if (bench) bench.style.display = "block";
        document.getElementById("res-colors").innerText = data.bestQuality;
        
        // Nếu là 0, ghi là < 1ms để người dùng biết máy quá nhanh
        document.getElementById("res-time").innerText = (data.executionTimeMs === 0) ? "< 1" : data.executionTimeMs;
        
        lastBestSolution = data.bestSolution;
        lastDetailedTrace = data.detailedTrace;

        if (isLarge || !data.history || data.history.length === 0) {
            updateColorsImmediate(data.bestSolution);
            status.innerText = "✅ Kết quả hiển thị tức thì cho dữ liệu lớn.";
        } else {
            status.innerText = "🎬 Đang tái hiện quá trình hội tụ...";
            await playHistoryAnimation(data.history);
        }

    } catch (e) {
        status.innerText = "❌ Lỗi: " + e.message;
        console.error(e);
    } finally {
        btn.disabled = false;
    }
}

// --- 1. CHẠY CHO ĐỒ THỊ VẼ TAY ---
// --- [BỔ SUNG VÀO INDEX.JS] ---

async function runDimacsSimulation() {
    const fileInput = document.getElementById("dimacsFile");
    if (!fileInput.files[0]) return alert("Vui lòng chọn file .col!");

    const status = document.getElementById("status");
    const bench = document.getElementById("benchmark-card");
    
    // ✅ 1. XÓA SẠCH ĐỒ THỊ CŨ ĐỂ KHÔNG BỊ CHỒNG HÌNH
    graphData.nodes.clear();
    graphData.edges.clear();
    if (network) network.setData(graphData); // Đưa về trạng thái trắng

    status.innerText = "🚀 Đang gửi file lên Java Backend xử lý...";

    const formData = new FormData();
    formData.append("file", fileInput.files[0]);
    formData.append("algorithm", document.getElementById("algorithm").value);
    // ... lấy các params ants, iter như cũ của ông ...
    formData.append("numAnts", document.getElementById("numAnts").value);
    formData.append("maxIterations", document.getElementById("iterations").value);
    formData.append("numColors", document.getElementById("numColors").value);
    formData.append("alpha", document.getElementById("alpha").value);
    formData.append("beta", document.getElementById("beta").value);
    formData.append("evaporation", document.getElementById("evaporation").value);

    try {
        const response = await fetch(API_BASE + "/api/aco/solve-dimacs", {
            method: "POST",
            body: formData
        });

        if (!response.ok) throw new Error("Backend sập!");

        const result = await response.json();
        console.log(">>> [LOG] DIMACS Result:", result);

        // ✅ 2. TẠO LẠI CÁC NỐT TỪ KẾT QUẢ JAVA TRẢ VỀ
        // Vì File nạp ở Backend nên Frontend nãy giờ chưa biết nốt nào nối với nốt nào
        // Lưu ý: Nếu Response của bạn chưa trả về cấu hình Node, 
        // ta sẽ tạo nốt dựa trên solution mảng trả về.
        
        const solution = result.bestSolution; 
        const nodesToAdd = [];
        solution.forEach((colorCode, nodeId) => {
            nodesToAdd.push({
                id: nodeId,
                label: `N:${nodeId}`,
                shape: 'dot',
                size: 15,
                color: { background: getColorForIndex(colorCode), border: '#000' }
            });
        });
        graphData.nodes.add(nodesToAdd);

        // ⚠️ Lưu ý về Edges (Cạnh): 
        // DIMACS nạp file trên server nên server đang giữ Edges. 
        // Để UI hiện cạnh nối, bạn cần Java trả về List Edges hoặc vẽ chay nốt. 
        // Hiện tại ta cứ vẽ nốt trước để thấy kết quả.

        // ✅ 3. ÉP VIS-NETWORK PHẢI LẮC LẠI (TỰ ĐỘNG BUNG VÒNG TRÒN)
        network.setOptions({
            physics: {
                enabled: true,
                solver: 'forceAtlas2Based',
                stabilization: { iterations: 1000 }
            }
        });
        
        // Cập nhật Benchmark
        bench.style.display = "block";
        document.getElementById("res-colors").innerText = result.bestQuality;
        document.getElementById("res-time").innerText = result.executionTimeMs;

        lastDetailedTrace = result.detailedTrace;
        status.innerText = "✅ Giải DIMACS hoàn tất. Nốt đã được rải đều!";

    } catch (e) {
        status.innerText = "❌ Lỗi upload file: " + e.message;
    }
}
// --- [MỚI] Hàm hiện tên file khi chọn ---
function displayFileName() {
    const fileInput = document.getElementById("dimacsFile");
    const label = document.getElementById("fileNameLabel");
    if (fileInput.files.length > 0) {
        label.innerText = "📁 " + fileInput.files[0].name;
        label.style.color = "#27ae60";
    }
}

// --- [BẢN FIX] Hàm giải file DIMACS và Tự động vẽ ---
async function runDimacsSimulation() {
    const fileInput = document.getElementById("dimacsFile");
    if (!fileInput.files[0]) return alert("Ông chưa chọn file mà Lam ơi!");

    const status = document.getElementById("status");
    const bench = document.getElementById("benchmark-card");
    const btnSolve = document.getElementById("btnSolveFile");

    // 1. XÓA SẠCH đồ thị cũ để không bị lag/lẫn lộn
    graphData.nodes.clear();
    graphData.edges.clear();
    status.innerText = "🧹 Đang làm sạch Canvas...";

    const formData = new FormData();
    formData.append("file", fileInput.files[0]);
    formData.append("algorithm", document.getElementById("algorithm").value);
    formData.append("numAnts", document.getElementById("numAnts").value || 30);
    formData.append("maxIterations", document.getElementById("iterations").value || 100);
    formData.append("numColors", document.getElementById("numColors").value || 20);
    formData.append("alpha", document.getElementById("alpha").value || 1.0);
    formData.append("beta", document.getElementById("beta").value || 2.0);
    formData.append("evaporation", document.getElementById("evaporation").value || 0.1);

    try {
        btnSolve.disabled = true;
        status.innerText = "🚀 Java Backend đang giải bài toán...";

        const response = await fetch(API_BASE + "/api/aco/solve-dimacs", { 
            method: "POST", 
            body: formData 
        });
        
        if (!response.ok) throw new Error("Backend sập!");
        const data = await response.json();

        // 🟢 QUAN TRỌNG: Lấy dữ liệu từ trường 'nodes' mà em vừa thêm vào SimulationResponse.java
        const nodesFromBackend = data.nodes; 
        const solutionColors = data.bestSolution;

        if (!nodesFromBackend || nodesFromBackend.length === 0) {
            throw new Error("Backend không trả về danh sách Node!");
        }

        const newNodes = [];
        const newEdges = [];
        const seenEdges = new Set(); // Chống vẽ lặp (1-2 và 2-1)

        status.innerText = "🏗️ Đang tái tạo cấu trúc đồ thị...";

        // 2. DUYỆT TỪNG NODE TRONG DANH SÁCH JAVA TRẢ VỀ
        nodesFromBackend.forEach(node => {
            const nodeId = node.id;
            const nodeColor = solutionColors[nodeId];

            // Tạo nốt
            newNodes.push({
                id: nodeId,
                label: `ID:${nodeId}`,
                shape: 'dot',
                size: 15,
                color: { background: getColorForIndex(nodeColor), border: '#000' }
            });

            // Tạo các dây nối (Edges)
            if (node.neighbors) {
                node.neighbors.forEach(neighborId => {
                    // Sắp xếp ID để check trùng cạnh (ví dụ 1-2 và 2-1 là một)
                    const edgeKey = nodeId < neighborId ? `${nodeId}-${neighborId}` : `${neighborId}-${nodeId}`;
                    if (!seenEdges.has(edgeKey)) {
                        newEdges.push({ from: nodeId, to: neighborId });
                        seenEdges.add(edgeKey);
                    }
                });
            }
        });

        // 3. ĐẨY DỮ LIỆU MỚI VÀO VIS-NETWORK
        graphData.nodes.add(newNodes);
        graphData.edges.add(newEdges);

        // 4. CÀI ĐẶT PHYSICS ĐỂ NÓ BUNG RA ĐẸP
        const isLarge = nodesFromBackend.length > 100;

network.setOptions({
    nodes: {
        size: isLarge ? 8 : 15,          // Nếu nhiều node thì vẽ bé lại cho đỡ chật
        font: { size: isLarge ? 8 : 12 } // Chữ bé lại
    },
    edges: {
        color: { opacity: 0.1 },        // ✅ QUAN TRỌNG: Làm mờ tịt dây nối đi (0.1 thôi) 
                                        // Chỉ khi nhìn kỹ mới thấy dây, sẽ đỡ bị "giẻ lau"
        width: 0.5,
        smooth: false                   // Tắt dây cong (đường thẳng nhìn sẽ "sắc" hơn)
    },
    physics: {
        enabled: true,
        solver: 'forceAtlas2Based',     // Thuật toán vật lý này xịn nhất cho đồ thị dày
        stabilization: {
            enabled: true,
            iterations: 1000,   // Chỉ cho phép lắc tối đa 1000 lần
            updateInterval: 25 // Cập nhật màn hình sau mỗi 25 nhịp để không bị giật
        },
        forceAtlas2Based: {
            gravitationalConstant: -150,
            springLength: 100,
            avoidOverlap: 1
        }
    }
});
        network.on("stabilizationIterationsDone", function () {
            console.log(">>> [LOG] Đồ thị đã ổn định. Đang đóng băng vị trí...");
            network.setOptions({ physics: false }); // TẮT VẬT LÝ TUYỆT ĐỐI
            document.getElementById("status").innerText = "🔒 Đồ thị đã khóa. Bạn có thể nhấn vào Node để kiểm tra.";
        });

        // ✅ TRÍ TUỆ BẢO VỆ ĐỒ ÁN 2: Click vào nốt nào, nốt đó to lên cho thầy cô nhìn
        network.on("click", function (params) {
            if (params.nodes.length > 0) {
                const selectedId = params.nodes[0];
                status.innerHTML = `📍 Đang soi Node: <b>${selectedId}</b> | Màu: <b>${lastBestSolution[selectedId]}</b>`;
            }
        });
        // Tự động thu phóng để thấy toàn cảnh
        setTimeout(() => network.fit(), 500);

        // Hiển thị Benchmark
        bench.style.display = "block";
        document.getElementById("res-colors").innerText = data.bestQuality;
        document.getElementById("res-time").innerText = data.executionTimeMs;

        lastDetailedTrace = data.detailedTrace;
        status.innerText = `🎉 Xong! Đồ thị file có ${nodesFromBackend.length} nốt và ${newEdges.length} cạnh.`;

    } catch (e) {
        status.innerText = "❌ Thất bại: " + e.message;
        console.error(e);
    } finally {
        btnSolve.disabled = false;
    }
}

// ==========================================
// 6. ANIMATION & TRACE REPLAY
// ==========================================

async function playHistoryAnimation(history) {
    const delay = 150; // Tốc độ vừa phải cho visualization
    const status = document.getElementById("status");

    for (let step of history) {
        status.innerText = `🎬 Loop ${step.iterationNumber}: Quality ${step.quality}`;
        let updates = step.solution.map((c, i) => {
            const conf = step.confidence ? step.confidence[i] : 1.0;
            return {
                id: i,
                color: { background: hexToRgba(getColorForIndex(c), 0.3 + (conf * 0.7)) }
            };
        });
        graphData.nodes.update(updates);
        await new Promise(r => setTimeout(r, delay));
    }
    status.innerText = "✅ Done!";
    updateColorsImmediate(lastBestSolution);
}

async function replayWithAntMovement() {
    if (!lastDetailedTrace) return alert("Bấm Start trước nhé!");
    
    const status = document.getElementById("status");
    const antImg = "https://img.icons8.com/color/48/ant.png";

    // 1. Tẩy trắng đồ thị về màu xám (Giữ nguyên vị trí)
    const resetUpdates = graphData.nodes.getIds().map(id => ({ 
        id: id, 
        color: { background: '#eee' }, 
        shape: 'dot',
        size: 15
    }));
    graphData.nodes.update(resetUpdates);

    // 2. Tắt vật lý để nó không nhảy tưng tưng khi thay hình dạng
    network.setOptions({ physics: { enabled: false } });

    for (let action of lastDetailedTrace) {
        let nid = action.nodeId;
        status.innerHTML = `🐜 Kiến đang xử lý Node: <b>${nid}</b>`;

        // --- MẸO HAY: Lấy toạ độ hiện tại để khoá vị trí ---
        const currentPos = network.getPositions([nid])[nid];

        // Biến node thành con kiến (Truyền lại toạ độ để không bị bay về 0,0)
        graphData.nodes.update({
            id: nid,
            shape: 'image',
            image: antImg,
            size: 40,
            x: currentPos.x,
            y: currentPos.y
        });
        
        await new Promise(r => setTimeout(r, 600));

        // Chốt màu và trả về hình tròn
        graphData.nodes.update({
            id: nid,
            shape: 'dot',
            size: 25,
            color: { background: getColorForIndex(action.colorCode), border: '#000' },
            x: currentPos.x,
            y: currentPos.y
        });
        
        await new Promise(r => setTimeout(r, 100));
    }
    status.innerText = "🎉 Replay hoàn tất!";
}
function freezeGraph() {
    if (network) {
        network.setOptions({ physics: false });
        document.getElementById("status").innerText = "🛑 Đã cưỡng chế dừng di chuyển.";
    }
}
async function replayWithAntMovement() {
    if (!lastDetailedTrace) return alert("Bấm Start trước nhé!");
    
    const status = document.getElementById("status");
    const antImg = "https://img.icons8.com/color/48/ant.png";

    // 1. Tẩy trắng đồ thị về màu xám (Giữ nguyên vị trí)
    const resetUpdates = graphData.nodes.getIds().map(id => ({ 
        id: id, 
        color: { background: '#eee' }, 
        shape: 'dot',
        size: 15
    }));
    graphData.nodes.update(resetUpdates);

    // 2. Tắt vật lý để nó không nhảy tưng tưng khi thay hình dạng
    network.setOptions({ physics: { enabled: false } });

    for (let action of lastDetailedTrace) {
        let nid = action.nodeId;
        status.innerHTML = `🐜 Kiến đang xử lý Node: <b>${nid}</b>`;

        // --- MẸO HAY: Lấy toạ độ hiện tại để khoá vị trí ---
        const currentPos = network.getPositions([nid])[nid];

        // Biến node thành con kiến (Truyền lại toạ độ để không bị bay về 0,0)
        graphData.nodes.update({
            id: nid,
            shape: 'image',
            image: antImg,
            size: 40,
            x: currentPos.x,
            y: currentPos.y
        });
        
        await new Promise(r => setTimeout(r, 600));

        // Chốt màu và trả về hình tròn
        graphData.nodes.update({
            id: nid,
            shape: 'dot',
            size: 25,
            color: { background: getColorForIndex(action.colorCode), border: '#000' },
            x: currentPos.x,
            y: currentPos.y
        });
        
        await new Promise(r => setTimeout(r, 100));
    }
    status.innerText = "🎉 Replay hoàn tất!";
}

// Khởi tạo đồ thị mặc định
window.onload = generateRandomGraph;