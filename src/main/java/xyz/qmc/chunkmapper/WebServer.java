package xyz.qmc.chunkmapper;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class WebServer {
    private HttpServer server;
    private final ChunkDataCollector dataCollector;

    public WebServer(int port, ChunkDataCollector dataCollector) throws IOException {
        this.dataCollector = dataCollector;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", new IndexHandler());
        server.createContext("/api/chunks", new ChunksApiHandler());
        server.setExecutor(null);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    private class IndexHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = getHtmlPage();
            byte[] response = html.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }

    private class ChunksApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String json = dataCollector.getChunkDataAsJson();
            byte[] response = json.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, response.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }

    private String getHtmlPage() {
        return """
<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Minecraft 3D Block Mapper</title>
    <style>
        body { 
            margin: 0; 
            overflow: hidden; 
            font-family: 'Segoe UI', Arial, sans-serif; 
            background: #0a0a0a; 
            color: white; 
        }
        #info {
            position: absolute;
            top: 15px;
            left: 15px;
            background: rgba(0,0,0,0.85);
            padding: 20px;
            border-radius: 8px;
            font-size: 14px;
            z-index: 100;
            border: 1px solid rgba(255,255,255,0.1);
            backdrop-filter: blur(10px);
        }
        #info h3 {
            margin: 0 0 15px 0;
            font-size: 18px;
            color: #4a9eff;
        }
        #settings {
            position: absolute;
            bottom: 15px;
            right: 15px;
            background: rgba(0,0,0,0.85);
            padding: 15px;
            border-radius: 8px;
            z-index: 100;
            border: 1px solid rgba(255,255,255,0.1);
            font-size: 12px;
        }
        .setting-item {
            margin: 8px 0;
        }
        .setting-item label {
            margin-bottom: 0;
        }
        input[type="range"] {
            width: 150px;
        }
        input[type="number"] {
            width: 60px;
            background: #2a2a2a;
            color: white;
            border: 1px solid #444;
            padding: 5px;
            border-radius: 3px;
        }
        .range-inputs {
            display: flex;
            gap: 10px;
            align-items: center;
            margin-top: 5px;
        }
        #controls {
            position: absolute;
            top: 15px;
            right: 15px;
            background: rgba(0,0,0,0.85);
            padding: 20px;
            border-radius: 8px;
            z-index: 100;
            border: 1px solid rgba(255,255,255,0.1);
            backdrop-filter: blur(10px);
        }
        #loading {
            position: absolute;
            top: 50%;
            left: 50%;
            transform: translate(-50%, -50%);
            background: rgba(0,0,0,0.9);
            padding: 30px 50px;
            border-radius: 10px;
            font-size: 18px;
            z-index: 200;
            border: 2px solid #4a9eff;
        }
        .control-group {
            margin: 10px 0;
        }
        label {
            display: block;
            margin-bottom: 5px;
            color: #aaa;
            font-size: 12px;
            text-transform: uppercase;
        }
        button {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            border: none;
            padding: 10px 20px;
            margin: 5px;
            cursor: pointer;
            border-radius: 5px;
            font-size: 14px;
            transition: all 0.3s;
            width: 100%;
        }
        button:hover { 
            transform: translateY(-2px);
            box-shadow: 0 5px 15px rgba(102, 126, 234, 0.4);
        }
        select {
            background: #2a2a2a;
            color: white;
            border: 1px solid #444;
            padding: 10px;
            margin: 5px 0;
            border-radius: 5px;
            width: 100%;
            font-size: 14px;
        }
        .stat {
            margin: 8px 0;
            padding: 8px;
            background: rgba(74, 158, 255, 0.1);
            border-radius: 4px;
            border-left: 3px solid #4a9eff;
        }
        .stat-value {
            color: #4a9eff;
            font-weight: bold;
        }
        #progressBar {
            width: 100%;
            height: 4px;
            background: rgba(255,255,255,0.1);
            margin-top: 10px;
            border-radius: 2px;
            overflow: hidden;
        }
        #progressFill {
            height: 100%;
            background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
            width: 0%;
            transition: width 0.3s;
        }
    </style>
</head>
<body>
    <div id="loading">
        <div>マップを読み込み中...</div>
        <div id="progressBar"><div id="progressFill"></div></div>
    </div>
    <div id="info">
        <h3>🗺️ Minecraft 3D Mapper</h3>
        <div class="stat">チャンク数: <span class="stat-value" id="chunkCount">0</span></div>
        <div class="stat">次元: <span class="stat-value" id="dimension">-</span></div>
        <div class="stat">FPS: <span class="stat-value" id="fps">0</span></div>
    </div>
    <div id="controls">
        <div class="control-group">
            <label>次元</label>
            <select id="dimensionSelect">
                <option value="overworld">オーバーワールド</option>
                <option value="nether">ネザー</option>
                <option value="end">エンド</option>
            </select>
        </div>
        <div class="control-group">
            <button id="resetCamera">📷 カメラリセット</button>
        </div>
    </div>
    <div id="settings">
        <h4 style="margin-top: 0;">⚙️ 設定</h4>
        <div class="setting-item">
            <label>描画品質: <span id="qualityValue">中</span></label>
            <input type="range" id="qualitySlider" min="1" max="3" value="2">
        </div>
        <div class="setting-item">
            <label>視野距離: <span id="viewDistValue">300</span></label>
            <input type="range" id="viewDistSlider" min="100" max="1000" value="300" step="50">
        </div>
        <div class="setting-item">
            <label>表示高度範囲</label>
            <div class="range-inputs">
                <input type="number" id="minHeight" value="50" min="-64" max="319" step="1">
                <span>～</span>
                <input type="number" id="maxHeight" value="100" min="-64" max="319" step="1">
            </div>
        </div>
        <div class="setting-item">
            <button id="applyHeight" style="margin: 5px 0; padding: 8px;">高度適用</button>
        </div>
    </div>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js"></script>
    <script>
        let scene, camera, renderer;
        let currentDimension = 'overworld';
        let chunkMap = new Map();
        let lastTime = performance.now();
        let frameCount = 0;
        let renderQuality = 2; // 1=低, 2=中, 3=高
        let viewDistance = 300;
        let minHeight = 50;
        let maxHeight = 100;
        
        // ブロックタイプID to 色マッピング
        const blockColors = {
            0: 0x808080,  // unknown
            1: 0x7cbd6b,  // grass_block
            2: 0x8b6f47,  // dirt
            3: 0x7f7f7f,  // stone
            4: 0xdcd3a8,  // sand
            5: 0x3f76e4,  // water
            6: 0x6f5436,  // oak_log
            7: 0x6ba82c,  // oak_leaves
            8: 0xffffff,  // snow
            9: 0xb0e0e6,  // ice
            10: 0x723232, // netherrack
            11: 0xe3e8a0, // end_stone
            12: 0x8b8378, // gravel
            13: 0x7a7a7a, // cobblestone
            14: 0x565656, // bedrock
            15: 0xa0a0a0, // clay
            16: 0x5e4830, // soul_sand
            17: 0x6f5f5f, // mycelium
            18: 0x5c4c2c, // podzol
            19: 0x7a5c3a, // coarse_dirt
            20: 0xe0c9a0  // sandstone
        };
        
        function init() {
            scene = new THREE.Scene();
            scene.background = new THREE.Color(0x87ceeb);
            scene.fog = new THREE.Fog(0x87ceeb, viewDistance * 0.5, viewDistance);
            
            camera = new THREE.PerspectiveCamera(75, window.innerWidth / window.innerHeight, 0.1, viewDistance * 2);
            camera.position.set(50, 100, 50);
            camera.lookAt(0, 50, 0);
            
            renderer = new THREE.WebGLRenderer({ 
                antialias: renderQuality >= 2,
                powerPreference: "high-performance"
            });
            renderer.setSize(window.innerWidth, window.innerHeight);
            renderer.setPixelRatio(renderQuality >= 3 ? window.devicePixelRatio : 1);
            document.body.appendChild(renderer.domElement);
            
            // 照明
            const ambientLight = new THREE.AmbientLight(0xffffff, 0.7);
            scene.add(ambientLight);
            
            const directionalLight = new THREE.DirectionalLight(0xffffff, 0.5);
            directionalLight.position.set(50, 100, 50);
            scene.add(directionalLight);
            
            // イベントリスナー
            window.addEventListener('resize', onWindowResize);
            
            document.getElementById('dimensionSelect').addEventListener('change', (e) => {
                currentDimension = e.target.value;
                updateChunks();
            });
            
            document.getElementById('resetCamera').addEventListener('click', () => {
                camera.position.set(50, 100, 50);
                camera.lookAt(0, 50, 0);
            });
            
            document.getElementById('qualitySlider').addEventListener('input', (e) => {
                renderQuality = parseInt(e.target.value);
                const labels = ['低', '中', '高'];
                document.getElementById('qualityValue').textContent = labels[renderQuality - 1];
                renderer.setPixelRatio(renderQuality >= 3 ? window.devicePixelRatio : 1);
                renderer.antialias = renderQuality >= 2;
            });
            
            document.getElementById('viewDistSlider').addEventListener('input', (e) => {
                viewDistance = parseInt(e.target.value);
                document.getElementById('viewDistValue').textContent = viewDistance;
                scene.fog.near = viewDistance * 0.5;
                scene.fog.far = viewDistance;
                camera.far = viewDistance * 2;
                camera.updateProjectionMatrix();
            });
            
            document.getElementById('applyHeight').addEventListener('click', () => {
                minHeight = parseInt(document.getElementById('minHeight').value);
                maxHeight = parseInt(document.getElementById('maxHeight').value);
                
                if (minHeight >= maxHeight) {
                    alert('最低高度は最高高度より低くしてください');
                    return;
                }
                
                // 既存のチャンクを削除して再描画
                chunkMap.forEach(mesh => scene.remove(mesh));
                chunkMap.clear();
                fetchChunks();
            });
            
            setupControls();
            
            fetchChunks();
            setInterval(fetchChunks, 3000); // 3秒ごとに更新
            animate();
        }
        
        function setupControls() {
            let isDragging = false;
            let previousMousePosition = { x: 0, y: 0 };
            let velocity = { x: 0, y: 0 };
            
            renderer.domElement.addEventListener('mousedown', (e) => { 
                isDragging = true;
                velocity = { x: 0, y: 0 };
            });
            
            renderer.domElement.addEventListener('mouseup', () => { 
                isDragging = false; 
            });
            
            renderer.domElement.addEventListener('mousemove', (e) => {
                const deltaX = e.clientX - previousMousePosition.x;
                const deltaY = e.clientY - previousMousePosition.y;
                
                if (isDragging) {
                    const rotationSpeed = 0.005;
                    
                    const offset = new THREE.Vector3();
                    offset.copy(camera.position);
                    
                    const distance = offset.length();
                    const theta = Math.atan2(offset.x, offset.z);
                    const phi = Math.acos(offset.y / distance);
                    
                    const newTheta = theta - deltaX * rotationSpeed;
                    const newPhi = Math.max(0.1, Math.min(Math.PI - 0.1, phi - deltaY * rotationSpeed));
                    
                    camera.position.x = distance * Math.sin(newPhi) * Math.sin(newTheta);
                    camera.position.y = distance * Math.cos(newPhi);
                    camera.position.z = distance * Math.sin(newPhi) * Math.cos(newTheta);
                    camera.lookAt(0, 50, 0);
                }
                
                previousMousePosition = { x: e.clientX, y: e.clientY };
            });
            
            renderer.domElement.addEventListener('wheel', (e) => {
                e.preventDefault();
                const zoomSpeed = 0.1;
                const distance = camera.position.length();
                const newDistance = Math.max(10, Math.min(500, distance + e.deltaY * zoomSpeed));
                camera.position.multiplyScalar(newDistance / distance);
            });
        }
        
        function fetchChunks() {
            fetch('/api/chunks')
                .then(res => res.json())
                .then(data => {
                    updateChunks(data);
                    document.getElementById('loading').style.display = 'none';
                })
                .catch(err => {
                    console.error('Failed to fetch chunks:', err);
                });
        }
        
        function updateChunks(data) {
            if (!data) return;
            
            const filteredData = data.filter(d => d.dimension === currentDimension);
            
            // 新しいチャンクのみ追加（圧縮データから展開）
            filteredData.forEach(chunkData => {
                const key = `${chunkData.chunkX}_${chunkData.chunkZ}`;
                
                if (!chunkMap.has(key)) {
                    // チャンク単位でメッシュを作成（インスタンシング使用）
                    const geometry = new THREE.PlaneGeometry(16, 16, 15, 15);
                    const vertices = geometry.attributes.position.array;
                    const colors = new Float32Array(256 * 3);
                    
                    let visibleVertices = 0;
                    
                    // 高さマップとカラーを設定
                    for (let i = 0; i < 256; i++) {
                        const height = chunkData.heightMap[i];
                        const blockId = chunkData.blockIds[i];
                        
                        // 高度フィルタリング
                        if (height >= minHeight && height <= maxHeight) {
                            vertices[i * 3 + 2] = height / 10; // Z軸を高さに
                            
                            const color = new THREE.Color(blockColors[blockId] || 0x808080);
                            colors[i * 3] = color.r;
                            colors[i * 3 + 1] = color.g;
                            colors[i * 3 + 2] = color.b;
                            
                            visibleVertices++;
                        } else {
                            // 範囲外のブロックは地下に沈める
                            vertices[i * 3 + 2] = -1000;
                            colors[i * 3] = 0;
                            colors[i * 3 + 1] = 0;
                            colors[i * 3 + 2] = 0;
                        }
                    }
                    
                    // 表示する頂点がない場合はスキップ
                    if (visibleVertices === 0) {
                        return;
                    }
                    
                    geometry.attributes.position.needsUpdate = true;
                    geometry.setAttribute('color', new THREE.BufferAttribute(colors, 3));
                    
                    if (renderQuality >= 2) {
                        geometry.computeVertexNormals();
                    }
                    
                    const material = new THREE.MeshLambertMaterial({
                        vertexColors: true,
                        side: THREE.DoubleSide,
                        flatShading: renderQuality < 3
                    });
                    
                    const mesh = new THREE.Mesh(geometry, material);
                    mesh.rotation.x = -Math.PI / 2;
                    mesh.position.set(chunkData.chunkX * 16, 0, chunkData.chunkZ * 16);
                    
                    scene.add(mesh);
                    chunkMap.set(key, mesh);
                }
            });
            
            document.getElementById('chunkCount').textContent = chunkMap.size.toLocaleString();
            document.getElementById('dimension').textContent = currentDimension;
        }
        
        function onWindowResize() {
            camera.aspect = window.innerWidth / window.innerHeight;
            camera.updateProjectionMatrix();
            renderer.setSize(window.innerWidth, window.innerHeight);
        }
        
        function animate() {
            requestAnimationFrame(animate);
            
            // FPS計算
            frameCount++;
            const currentTime = performance.now();
            if (currentTime >= lastTime + 1000) {
                document.getElementById('fps').textContent = frameCount;
                frameCount = 0;
                lastTime = currentTime;
            }
            
            renderer.render(scene, camera);
        }
        
        init();
    </script>
</body>
</html>
        """;
    }
}