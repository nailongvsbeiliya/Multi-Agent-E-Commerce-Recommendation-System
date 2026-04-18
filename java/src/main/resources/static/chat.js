/*
const chatForm = document.getElementById("chatForm");
const queryInput = document.getElementById("queryInput");
const userIdInput = document.getElementById("userIdInput");
const numItemsInput = document.getElementById("numItemsInput");
const sendButton = document.getElementById("sendButton");
const chatFeed = document.getElementById("chatFeed");
const statusBadge = document.getElementById("statusBadge");

const requestIdEl = document.getElementById("requestId");
const responseUserIdEl = document.getElementById("responseUserId");
const experimentGroupEl = document.getElementById("experimentGroup");
const latencyEl = document.getElementById("latency");
const productsEl = document.getElementById("products");
const copiesEl = document.getElementById("copies");
const agentJsonEl = document.getElementById("agentJson");

const STATUS_TEXT = {
    idle: "空闲",
    loading: "生成中",
    success: "完成",
    error: "失败"
};

function setStatus(type) {
    statusBadge.className = `status ${type}`;
    statusBadge.textContent = STATUS_TEXT[type] || type;
}

function escapeHtml(raw) {
    return String(raw ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

function addBubble(role, text, type = role) {
    const node = document.createElement("article");
    node.className = `bubble ${type}`;
    node.innerHTML = `
        <p class="role">${escapeHtml(role)}</p>
        <p class="text">${escapeHtml(text)}</p>
    `;
    chatFeed.appendChild(node);
    chatFeed.scrollTop = chatFeed.scrollHeight;
    return node;
}

function prettyJson(value) {
    return JSON.stringify(value ?? {}, null, 2);
}

function renderProducts(products = []) {
    if (!products.length) {
        productsEl.className = "stack empty";
        productsEl.textContent = "暂无推荐";
        return;
    }

    productsEl.className = "stack";
    productsEl.innerHTML = products.map((p) => `
        <article class="item">
            <h3>${escapeHtml(p.name || p.productId || "-")}</h3>
            <p>${escapeHtml(p.description || "no description")}</p>
            <div class="pill-row">
                <span class="pill">${escapeHtml(p.productId || "-")}</span>
                <span class="pill">${escapeHtml(p.category || "-")}</span>
                <span class="pill">price ${p.price ?? "-"}</span>
                <span class="pill">stock ${p.stock ?? "-"}</span>
            </div>
        </article>
    `).join("");
}

function renderCopies(copies = []) {
    if (!copies.length) {
        copiesEl.className = "stack empty";
        copiesEl.textContent = "暂无文案";
        return;
    }

    copiesEl.className = "stack";
    copiesEl.innerHTML = copies.map((c) => `
        <article class="item">
            <h3>${escapeHtml(c.product_id || "-")}</h3>
            <p>${escapeHtml(c.copy || "-")}</p>
        </article>
    `).join("");
}

function renderMeta(data) {
    requestIdEl.textContent = data.requestId || "-";
    responseUserIdEl.textContent = data.userId || "-";
    experimentGroupEl.textContent = data.experimentGroup || "-";
    latencyEl.textContent = typeof data.totalLatencyMs === "number"
        ? `${data.totalLatencyMs.toFixed(1)} ms`
        : "-";
    agentJsonEl.textContent = prettyJson(data.agentResults);
}

function buildAssistantSummary(data) {
    const names = (data.products || []).slice(0, 3).map((p) => p.name || p.productId).filter(Boolean);
    const best = names.length ? names.join(" / ") : "暂无可推荐商品";
    const group = data.experimentGroup || "control";
    const latency = typeof data.totalLatencyMs === "number"
        ? `${data.totalLatencyMs.toFixed(1)}ms`
        : "unknown";
    return `我给你找到了这些候选：${best}。实验组: ${group}，本次耗时: ${latency}。`;
}

async function requestRecommendation(payload) {
    const response = await fetch("/api/v1/recommend/chat", {
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

chatForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const query = queryInput.value.trim();
    if (!query) {
        return;
    }

    const payload = {
        userId: userIdInput.value.trim() || "u001",
        query,
        numItems: Number(numItemsInput.value) || 5
    };

    addBubble("User", query, "user");
    queryInput.value = "";
    queryInput.focus();

    setStatus("loading");
    sendButton.disabled = true;
    const thinkingNode = addBubble("Assistant", "正在分析你的需求并生成推荐...", "system");

    try {
        const data = await requestRecommendation(payload);
        thinkingNode.remove();
        renderMeta(data);
        renderProducts(data.products);
        renderCopies(data.marketingCopies);
        addBubble("Assistant", buildAssistantSummary(data), "assistant");
        setStatus("success");
    } catch (error) {
        thinkingNode.remove();
        addBubble("Assistant", `请求失败：${error.message}`, "assistant");
        setStatus("error");
    } finally {
        sendButton.disabled = false;
    }
});

setStatus("idle");
*/

const form = document.getElementById("chatForm");
const queryInputSafe = document.getElementById("queryInput");
const userIdInputSafe = document.getElementById("userIdInput");
const numItemsInputSafe = document.getElementById("numItemsInput");
const sendButtonSafe = document.getElementById("sendButton");
const chatFeedSafe = document.getElementById("chatFeed");
const statusBadgeSafe = document.getElementById("statusBadge");

const requestIdSafe = document.getElementById("requestId");
const responseUserIdSafe = document.getElementById("responseUserId");
const experimentGroupSafe = document.getElementById("experimentGroup");
const latencySafe = document.getElementById("latency");
const productsSafe = document.getElementById("products");
const copiesSafe = document.getElementById("copies");
const agentJsonSafe = document.getElementById("agentJson");
const guessListSafe = document.getElementById("guessList");
const guessStrategySafe = document.getElementById("guessStrategy");
let currentSessionId = null;

const STATUS_LABELS = {
    idle: "Idle",
    loading: "Loading",
    success: "Done",
    error: "Error"
};

function setStatusSafe(type) {
    statusBadgeSafe.className = `status ${type}`;
    statusBadgeSafe.textContent = STATUS_LABELS[type] || type;
}

function esc(text) {
    return String(text ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

function addBubbleSafe(role, text, type = "assistant") {
    const node = document.createElement("article");
    node.className = `bubble ${type}`;
    node.innerHTML = `
        <p class="role">${esc(role)}</p>
        <p class="text">${esc(text)}</p>
    `;
    chatFeedSafe.appendChild(node);
    chatFeedSafe.scrollTop = chatFeedSafe.scrollHeight;
    return node;
}

function renderProductsSafe(products = []) {
    if (!products.length) {
        productsSafe.className = "stack empty";
        productsSafe.textContent = "No recommendation yet.";
        return;
    }

    productsSafe.className = "stack";
    productsSafe.innerHTML = products.map((item) => `
        <article class="item">
            <h3>${esc(item.name || item.productId || "-")}</h3>
            <p>${esc(item.description || "No description.")}</p>
            <div class="pill-row">
                <span class="pill">${esc(item.productId || "-")}</span>
                <span class="pill">${esc(item.category || "-")}</span>
                <span class="pill">price ${item.price ?? "-"}</span>
                <span class="pill">stock ${item.stock ?? "-"}</span>
            </div>
        </article>
    `).join("");
}

function renderCopiesSafe(copies = []) {
    if (!copies.length) {
        copiesSafe.className = "stack empty";
        copiesSafe.textContent = "No copy generated yet.";
        return;
    }

    copiesSafe.className = "stack";
    copiesSafe.innerHTML = copies.map((item) => `
        <article class="item">
            <h3>${esc(item.product_id || "-")}</h3>
            <p>${esc(item.copy || "-")}</p>
        </article>
    `).join("");
}

function renderMetaSafe(data) {
    requestIdSafe.textContent = data.requestId || "-";
    responseUserIdSafe.textContent = data.userId || "-";
    experimentGroupSafe.textContent = data.experimentGroup || "-";
    latencySafe.textContent = typeof data.totalLatencyMs === "number"
        ? `${data.totalLatencyMs.toFixed(1)} ms`
        : "-";
    agentJsonSafe.textContent = JSON.stringify(data.agentResults ?? {}, null, 2);
}

function renderGuessYouLikeSafe(items = []) {
    if (!guessListSafe) {
        return;
    }
    if (!items.length) {
        guessListSafe.className = "guess-list empty";
        guessListSafe.textContent = "暂无猜你喜欢商品。";
        return;
    }

    guessListSafe.className = "guess-list";
    guessListSafe.innerHTML = items.map((item) => `
        <article class="guess-item">
            <h3>${esc(item.name || item.productId || "-")}</h3>
            <p class="meta">${esc(item.category || "-")} · ${esc(item.brand || "-")} · ¥${item.price ?? "-"}</p>
            <p class="reason">${esc(item.reason || "根据你的偏好推荐")}</p>
        </article>
    `).join("");
}

function fallbackPitchSafe(product, rank) {
    const name = product?.name || product?.productId || "这款商品";
    const pricePart = typeof product?.price === "number" ? `，当前参考价 ¥${product.price}` : "";
    const angle = rank === 0
        ? "综合体验和口碑都比较均衡"
        : rank === 1
            ? "性能与价格的平衡做得不错"
            : "作为同类目备选，性价比表现也很有竞争力";
    return `${name}${angle}${pricePart}。`;
}

function summaryTextSafe(data) {
    const products = (data.products || []).slice(0, 3);
    if (!products.length) {
        return "这次暂时没有匹配到合适商品。你可以告诉我预算、品牌偏好或使用场景，我会再给你一版更精准的推荐。";
    }

    const copyMap = new Map(
        (data.marketingCopies || [])
            .filter((item) => item && item.product_id)
            .map((item) => [String(item.product_id), String(item.copy || "")])
    );

    const highlights = products.map((product, index) => {
        const name = product?.name || product?.productId || "推荐商品";
        const copy = copyMap.get(String(product?.productId)) || fallbackPitchSafe(product, index);
        return `【${name}】${copy}`;
    }).join("；");

    const hasLowStock = products.some((product) => typeof product?.stock === "number" && product.stock <= 100);
    const actionHint = hasLowStock
        ? "部分商品库存偏紧，建议优先下单或先加入购物车锁定。"
        : "如果你愿意，我还可以继续按预算/品牌/功能再细分一轮，帮你收敛到最终1款。";

    return `为你精选了更值得入手的方案：${highlights} ${actionHint}`;
}

async function requestRecommendationSafe(payload) {
    const response = await fetch("/api/v1/recommend/chat", {
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

async function requestGuessYouLikeSafe(params) {
    const query = new URLSearchParams();
    query.set("userId", params.userId || "u001");
    query.set("numItems", String(params.numItems || 6));
    if (params.sessionId) {
        query.set("sessionId", params.sessionId);
    }
    const response = await fetch(`/api/v1/recommend/guess-you-like?${query.toString()}`);
    if (!response.ok) {
        const text = await response.text();
        throw new Error(text || `HTTP ${response.status}`);
    }
    return response.json();
}

async function refreshGuessYouLikeSafe() {
    if (!guessListSafe) {
        return;
    }
    guessListSafe.className = "guess-list empty";
    guessListSafe.textContent = "正在刷新猜你喜欢...";
    try {
        const data = await requestGuessYouLikeSafe({
            userId: userIdInputSafe.value.trim() || "u001",
            sessionId: currentSessionId,
            numItems: Number(numItemsInputSafe.value) || 6
        });
        guessStrategySafe.textContent = data.strategy || "profile+catalog";
        renderGuessYouLikeSafe(data.items || []);
    } catch (error) {
        guessListSafe.className = "guess-list empty";
        guessListSafe.textContent = `猜你喜欢加载失败: ${error.message}`;
    }
}

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const query = queryInputSafe.value.trim();
    if (!query) {
        return;
    }

    const payload = {
        userId: userIdInputSafe.value.trim() || "u001",
        query,
        numItems: Number(numItemsInputSafe.value) || 5
    };
    if (currentSessionId) {
        payload.sessionId = currentSessionId;
    }

    addBubbleSafe("User", query, "user");
    queryInputSafe.value = "";
    queryInputSafe.focus();

    setStatusSafe("loading");
    sendButtonSafe.disabled = true;
    const pendingNode = addBubbleSafe("Assistant", "Analyzing your request...", "system");

    try {
        const data = await requestRecommendationSafe(payload);
        if (data.sessionId) {
            currentSessionId = data.sessionId;
        }
        pendingNode.remove();
        renderMetaSafe(data);
        renderProductsSafe(data.products);
        renderCopiesSafe(data.marketingCopies);
        addBubbleSafe("Assistant", summaryTextSafe(data), "assistant");
        await refreshGuessYouLikeSafe();
        setStatusSafe("success");
    } catch (error) {
        pendingNode.remove();
        addBubbleSafe("Assistant", `Request failed: ${error.message}`, "assistant");
        setStatusSafe("error");
    } finally {
        sendButtonSafe.disabled = false;
    }
});

setStatusSafe("idle");
refreshGuessYouLikeSafe();

userIdInputSafe?.addEventListener("change", () => {
    currentSessionId = null;
    refreshGuessYouLikeSafe();
});
