const form = document.getElementById("recommendForm");
const statusBadge = document.getElementById("statusBadge");

const statusMap = {
    idle: "待发起",
    loading: "请求中",
    success: "已完成",
    error: "请求失败"
};

function setStatus(type) {
    statusBadge.className = `status-badge ${type}`;
    statusBadge.textContent = statusMap[type] || type;
}

function pretty(value) {
    return JSON.stringify(value ?? {}, null, 2);
}

function renderProducts(products = []) {
    const grid = document.getElementById("productsGrid");
    if (!products.length) {
        grid.className = "products-grid empty-state";
        grid.textContent = "当前没有推荐商品";
        return;
    }

    grid.className = "products-grid";
    grid.innerHTML = products.map((product) => `
        <article class="product-card">
            <div class="product-topline">
                <span class="pill">${product.productId || "-"}</span>
                <span class="pill">${product.category || "-"}</span>
            </div>
            <h3>${product.name || "-"}</h3>
            <p>${product.description || "暂无描述"}</p>
            <div class="product-tags">
                <span class="pill">¥${product.price ?? 0}</span>
                <span class="pill">库存 ${product.stock ?? 0}</span>
                <span class="pill">评分 ${product.score ?? 0}</span>
            </div>
        </article>
    `).join("");
}

function renderCopies(copies = []) {
    const list = document.getElementById("copyList");
    if (!copies.length) {
        list.className = "copy-list empty-state";
        list.textContent = "当前没有营销文案";
        return;
    }

    list.className = "copy-list";
    list.innerHTML = copies.map((copy) => `
        <article class="copy-card">
            <div class="copy-meta">
                <span class="pill">${copy.product_id || "-"}</span>
            </div>
            <p>${copy.copy || "-"}</p>
        </article>
    `).join("");
}

function renderAgents(agentResults = {}) {
    const grid = document.getElementById("agentGrid");
    const entries = Object.entries(agentResults);

    if (!entries.length) {
        grid.className = "agent-grid empty-state";
        grid.textContent = "当前没有 Agent 数据";
        return;
    }

    grid.className = "agent-grid";
    grid.innerHTML = entries.map(([key, value]) => {
        const latency = typeof value.latencyMs === "number" ? value.latencyMs.toFixed(1) : (value.latencyMs ?? 0);
        const confidence = typeof value.confidence === "number" ? value.confidence.toFixed(2) : (value.confidence ?? "-");
        return `
            <article class="agent-card ${value.success ? "success" : "failed"}">
                <div class="agent-meta">
                    <h3>${key}</h3>
                    <span class="pill">${latency} ms</span>
                </div>
                <p>${value.success ? "执行成功" : `执行失败: ${value.error || "unknown"}`}</p>
                <div class="agent-tags">
                    <span class="pill">confidence ${confidence}</span>
                    <span class="pill">${value.agentName || "-"}</span>
                </div>
            </article>
        `;
    }).join("");
}

function applyResponse(data) {
    document.getElementById("experimentGroup").textContent = data.experimentGroup || "-";
    document.getElementById("experimentStrategy").textContent = data.experimentInfo?.strategy || "无策略信息";
    document.getElementById("latency").textContent = typeof data.totalLatencyMs === "number" ? data.totalLatencyMs.toFixed(1) : "-";
    document.getElementById("productCount").textContent = data.products?.length ?? 0;
    document.getElementById("requestId").textContent = data.requestId || "-";
    document.getElementById("responseUserId").textContent = data.userId || "-";
    document.getElementById("purchaseLimits").textContent = pretty(data.purchaseLimits);
    document.getElementById("stockAlerts").textContent = pretty(data.lowStockAlerts);
    document.getElementById("experimentInfo").textContent = pretty(data.experimentInfo);

    renderProducts(data.products);
    renderCopies(data.marketingCopies);
    renderAgents(data.agentResults);
}

async function requestRecommendation(payload) {
    const response = await fetch("/api/v1/recommend", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
    });

    if (!response.ok) {
        const text = await response.text();
        throw new Error(text || `HTTP ${response.status}`);
    }
    return response.json();
}

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    setStatus("loading");

    const payload = {
        userId: document.getElementById("userId").value.trim(),
        scene: document.getElementById("scene").value,
        numItems: Number(document.getElementById("numItems").value),
        context: { scene: document.getElementById("scene").value }
    };

    try {
        const data = await requestRecommendation(payload);
        applyResponse(data);
        setStatus("success");
    } catch (error) {
        console.error(error);
        setStatus("error");
        document.getElementById("experimentInfo").textContent = `请求失败:\n${error.message}`;
    }
});

setStatus("idle");
