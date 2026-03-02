/**
 * AI LAB - ACO GRAPH COLORING FRONTEND (ULTIMATE VERSION)
 * Hệ thống điều phối: Tailscale Port 80 | Backend 8081
 */

// ==========================================
// 1. CẤU HÌNH ĐỊA CHỈ & DỌN RÁC SESSION
// ==========================================
const IS_LAPTOP = window.location.port === "5500" || window.location.port === "5501" || window.location.hostname === "127.0.0.1";
const API_BASE = IS_LAPTOP ? "http://localhost:8081" : ""; 

// Khi load trang: Reset session cũ ở Java & Tạo đồ thị mẫu
window.onload = function() {
    fetch(API_BASE + "/api/aco/stop").catch(e => console.log("Init clean skipped"));
    generateRandomGraph();
};

// ==========================================
// 2. BIẾN TRẠNG THÁI TOÀN CỤC
// ==========================================
let network = null;
let graphData = { nodes: new vis.DataSet(), edges: new vis.DataSet() };
let lastBestSolution = null;
let lastDetailedTrace = null;

// Tự động hiện/ẩn ô tham số dựa trên Algorithm chọn trong HTML
document.addEventListener('DOMContentLoaded', () => {
    const algoSelect = document.getElementById('algorithm');
    if (algoSelect) {
        algoSelect.addEventListener('change', function() {
            const algo = this.value;
            const acsBox = document.getElementById('extra-params-acs');
            const gaBox = document.getElementById('extra-params-ga');
            if (acsBox) acsBox.style.display = (algo === 'ACS') ? 'block' : 'none';
            if (gaBox) gaBox.style.display = (algo === 'GA') ? 'block' : 'none';
        });
    }
});

// ==========================================
// 3. TIỆN ÍCH MÀU SẮC & XỬ LÝ SỐ
// ==========================================
function getColorForIndex(index) {
    if (index === -1 || index === undefined) return '#EEEEEE';
    const p = ["#FF5733", "#27AE60", "#2980B9", "#E67E22", "#8E44AD", "#F1C40F", "#1ABC9C", "#C0392B", "#7F8C8D", "#D35400"];
    return index < p.length ? p[index] : `hsl(${(index * 137.5) % 360}, 75%, 50%)`;
}

function hexToRgba(hex, alpha) {
    if (hex.startsWith("hsl")) return hex;
    let r=0, g=0, b=0;
    if(hex.length === 7) {
        r = parseInt(hex.slice(1,3), 16); g = parseInt(hex.slice(3,5), 16); b = parseInt(hex.slice(5,7), 16);
    }
    return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

const getVal = (id, def) => {
    const el = document.getElementById(id);
    if (!el) return def;
    const val = parseFloat(el.value);
    return isNaN(val) ? def : val;
};

// ==========================================
// 4. QUẢN LÝ ĐỒ THỊ (VIS.JS NETWORK)
// ==========================================

function generateRandomGraph() {
    const count = parseInt(document.getElementById("nodeCount").value) || 20;
    graphData.nodes.clear(); graphData.edges.clear();
    const ns = [], es = [];
    const prob = count > 50 ? (count > 100 ? 0.02 : 0.05) : 0.15;
    for (let i = 0; i < count; i++) ns.push({ id: i, label: `ID:${i}`, shape: 'dot', size: 15, color: { background: '#eee' } });
    for (let i = 0; i < count; i++) {
        for (let j = i + 1; j < count; j++) { if (Math.random() < prob) es.push({ from: i, to: j }); }
    }
    graphData.nodes.add(ns); graphData.edges.add(es);
    renderNetwork();
}

function renderNetwork() {
    const container = document.getElementById('mynetwork');
    const options = {
        physics: { 
            enabled: true, 
            solver: 'forceAtlas2Based', 
            stabilization: { iterations: 1000 },
            forceAtlas2Based: { gravitationalConstant: -100, springLength: 80 }
        },
        edges: { smooth: false, color: { opacity: 0.15 } }
    };
    network = new vis.Network(container, graphData, options);

    // ✅ Khóa vị trí khi đã lắc xong đồ thị
    network.on("stabilizationIterationsDone", () => {
        network.setOptions({ physics: false });
        document.getElementById("status").innerText = "🔒 Bản đồ đã khóa vị trí.";
    });

    // Sự kiện click để bảo vệ đồ án
    network.on("click", (params) => {
        if (params.nodes.length > 0) {
            const id = params.nodes[0];
            const msg = lastBestSolution ? `Màu Code: ${lastBestSolution[id]}` : "Chưa có dữ liệu";
            document.getElementById("status").innerHTML = `📍 <b>Soi Node:</b> ${id} | ${msg}`;
        }
    });
}

function freezeGraph() {
    if (network) {
        network.setOptions({ physics: false });
        document.getElementById("status").innerText = "🛑 Cưỡng chế dừng di chuyển.";
    }
}

// ==========================================
// 5. GỌI SERVER JAVA
// ==========================================

async function callSolver(endpoint, options, isLarge = false) {
    const status = document.getElementById("status");
    const bench = document.getElementById("benchmark-card");
    const btn = document.getElementById("btnRun");
    const url = API_BASE + endpoint;

    status.innerText = "⏳ Bầy kiến Java đang tính toán (Pentium)...";
    btn.disabled = true;

    try {
        const response = await fetch(url, options);
        if (response.status === 204) return; // Nếu bị dừng task thì thôi

        if (!response.ok) {
            const err = await response.text();
            throw new Error(`Server Error: ${err}`);
        }
        
        const data = await response.json();

        // HIỂN THỊ KẾT QUẢ PHÂN TÍCH (Analysis)
        bench.style.display = "block";
        document.getElementById("res-colors").innerText = data.bestQuality;
        document.getElementById("res-conflicts").innerText = data.conflicts; // Cột này cực quan trọng nãy ta mới fix
        document.getElementById("res-time").innerText = (data.executionTimeMs === 0) ? "< 1" : data.executionTimeMs;
        
        lastBestSolution = data.bestSolution;
        lastDetailedTrace = data.detailedTrace;

        // VẼ ĐỒ THỊ LẠI NẾU LÀ DỮ LIỆU TỪ FILE (ĐỂ HIỆN MAP)
        if (data.nodes && data.nodes.length > 0) {
            drawFromNodes(data.nodes, data.bestSolution);
        }

        // CHỌN CHẾ ĐỘ HIỂN THỊ: Vẽ ngay nếu đồ thị to, chạy phim nếu đồ thị bé
        if (isLarge || (data.nodes && data.nodes.length > 250)) {
            updateColorsImmediate(data.bestSolution);
            status.innerText = "✅ Xong! Hiện kết quả tức thì cho đồ thị lớn.";
        } else if (data.history && data.history.length > 0) {
            status.innerText = "🎬 Tái hiện quá trình tiến hóa...";
            await playHistoryAnimation(data.history);
        } else {
            updateColorsImmediate(data.bestSolution);
        }

    } catch (e) {
        status.innerText = "❌ Lỗi kết nối!";
        console.error(e);
    } finally {
        btn.disabled = false;
    }
}

// ==========================================
// 6. CÁC HÀM XỬ LÝ CHIẾN DỊCH
// ==========================================

// GIẢI ĐỒ THỊ VẼ TRÊN WEB
function runSimulation() {
    const allNodes = graphData.nodes.get();
    if (allNodes.length === 0) return alert("Bấm Generate Graph trước nhé!");

    const javaNodes = allNodes.map(node => {
        const pos = network.getPositions([node.id])[node.id] || {x: 0, y: 0};
        return {
            id: Number(node.id),
            x: pos.x, y: pos.y, // Gửi toạ độ thực để ko bị bay nốt
            neighbors: graphData.edges.get()
                .filter(e => e.from == node.id || e.to == node.id)
                .map(e => Number(e.from == node.id ? e.to : e.from))
        };
    });

    const payload = {
        algorithm: document.getElementById("algorithm").value,
        nodes: javaNodes,
        numAnts: Math.floor(getVal("numAnts", 30)),
        maxIterations: Math.floor(getVal("iterations", 100)),
        numColors: Math.floor(getVal("numColors", 15)),
        alpha: getVal("alpha", 1.0),
        beta: getVal("beta", 2.0),
        evaporation: getVal("evaporation", 0.1),
        // Đặc thù
        q0: getVal("q0", 0.9),
        mutationRate: getVal("mutationRate", 0.05),
        tournamentSize: Math.floor(getVal("tournamentSize", 5)),
        numberOfRuns: 1
    };

    callSolver("/api/simulate", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
    }, javaNodes.length > 250);
}

// GIẢI ĐỒ THỊ TỪ FILE DIMACS (.COL)
async function runDimacsSimulation() {
    const fileInput = document.getElementById("dimacsFile");
    if (!fileInput.files[0]) return alert("Vui lòng chọn file .col!");

    const status = document.getElementById("status");
    const btnSolve = document.getElementById("btnSolveFile");

    // Xoá trắng màn hình để sẵn sàng vẽ Map mới từ file
    graphData.nodes.clear();
    graphData.edges.clear();
    status.innerText = "🧼 Đang dọn dẹp bộ nhớ...";

    const formData = new FormData();
    formData.append("file", fileInput.files[0]);
    formData.append("algorithm", document.getElementById("algorithm").value);
    formData.append("numAnts", document.getElementById("numAnts").value);
    formData.append("maxIterations", document.getElementById("iterations").value);
    formData.append("numColors", document.getElementById("numColors").value);
    formData.append("alpha", document.getElementById("alpha").value);
    formData.append("beta", document.getElementById("beta").value);
    formData.append("evaporation", document.getElementById("evaporation").value);
    // Đừng quên các biến đặc biệt nếu có
    formData.append("q0", getVal("q0", 0.9));

    callSolver("/api/aco/solve-dimacs", {
        method: "POST",
        body: formData
    }, true); 
}

function drawFromNodes(nodes, solution) {
    const newNodes = [], newEdges = [], seenEdges = new Set();
    nodes.forEach(n => {
        const nodeId = n.id;
        const color = solution[nodeId];
        newNodes.push({ id: nodeId, label: `N:${nodeId}`, shape: 'dot', color: { background: getColorForIndex(color) } });
        if (n.neighbors) {
            n.neighbors.forEach(neighId => {
                const key = nodeId < neighId ? `${nodeId}-${neighId}` : `${neighId}-${nodeId}`;
                if (!seenEdges.has(key)) {
                    newEdges.push({ from: nodeId, to: neighId });
                    seenEdges.add(key);
                }
            });
        }
    });
    graphData.nodes.add(newNodes);
    graphData.edges.add(newEdges);
    network.setOptions({ physics: { enabled: true } }); // Cho nó lắc một chút rồi sẽ tự khóa
}

// ==========================================
// 7. ANIMATION & TRACE (BẦY KIẾN BÒ)
// ==========================================

async function playHistoryAnimation(history) {
    const delay = 150;
    for (let step of history) {
        document.getElementById("status").innerText = `🎬 Loop ${step.iterationNumber}: ${step.quality} colors`;
        const updates = step.solution.map((c, i) => ({ id: i, color: { background: getColorForIndex(c) } }));
        graphData.nodes.update(updates);
        await new Promise(r => setTimeout(r, delay));
    }
    updateColorsImmediate(lastBestSolution);
}

async function replayWithAntMovement() {
    if (!lastDetailedTrace || lastDetailedTrace.length === 0) return alert("Vui lòng giải thuật toán trước để lấy kịch bản!");
    
    // Tẩy màu xám và giữ vị trí cố định (X, Y)
    const resetNodes = graphData.nodes.getIds().map(id => {
        const pos = network.getPositions([id])[id];
        return { id: id, color: { background: '#eee' }, shape: 'dot', x: pos.x, y: pos.y };
    });
    graphData.nodes.update(resetNodes);
    network.setOptions({ physics: { enabled: false } }); // Đảm bảo nốt ko chạy khi hiện icon kiến

    const status = document.getElementById("status");
    const antImg = "https://img.icons8.com/color/48/ant.png";

    for (let action of lastDetailedTrace) {
        const nid = action.nodeId;
        const currentPos = network.getPositions([nid])[nid];
        status.innerHTML = `🐜 Kiến xử lý Node: <b>${nid}</b>`;
        
        graphData.nodes.update({ id: nid, shape: 'image', image: antImg, size: 40, x: currentPos.x, y: currentPos.y });
        await new Promise(r => setTimeout(r, 600));

        graphData.nodes.update({ 
            id: nid, shape: 'dot', size: 25, 
            color: { background: getColorForIndex(action.colorCode), border: '#000' },
            label: `ID:${nid}\nC:${action.colorCode}`,
            x: currentPos.x, y: currentPos.y
        });
    }
}

// Các hàm linh tinh khác
function displayFileName() {
    const fileBox = document.getElementById("dimacsFile");
    if (fileBox.files[0]) document.getElementById("fileNameLabel").innerText = "📁 " + fileBox.files[0].name;
}

function updateColorsImmediate(sol) {
    if(!sol) return;
    graphData.nodes.update(sol.map((c, i) => ({ 
        id: i, 
        color: { background: getColorForIndex(c) },
        label: `ID:${i}\nC:${c}` 
    })));
}