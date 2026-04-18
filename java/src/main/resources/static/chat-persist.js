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

const STATUS_LABELS = {
    idle: "Idle",
    loading: "Loading",
    success: "Done",
    error: "Error"
};

const CHAT_STORAGE_PREFIX = "multi-agent-chat-state:v1:";
const MAX_STORED_BUBBLES = 120;
const DEFAULT_ASSISTANT_TEXT = "Tell me what you want to buy. I will run profile, retrieval, rerank and inventory decisions for you.";

let currentSessionId = null;
let bubbleHistorySafe = [];
let latestProductsSafe = [];
let latestCopiesSafe = [];
let latestMetaSafe = {};
let latestGuessSafe = { strategy: "profile+catalog", items: [] };

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

function currentUserIdSafe() {
    return userIdInputSafe?.value?.trim() || "u001";
}

function storageKeySafe(userId) {
    return `${CHAT_STORAGE_PREFIX}${userId || "u001"}`;
}

function canUseStorageSafe() {
    try {
        return typeof window !== "undefined" && !!window.localStorage;
    } catch (error) {
        return false;
    }
}

function normalizeBubbleSafe(item) {
    if (!item || typeof item !== "object") {
        return null;
    }
    const role = String(item.role || "Assistant");
    const text = String(item.text || "");
    const type = String(item.type || "assistant");
    if (!text.trim()) {
        return null;
    }
    return { role, text, type };
}

function appendBubbleNodeSafe(role, text, type = "assistant") {
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

function persistChatStateSafe() {
    if (!canUseStorageSafe()) {
        return;
    }
    const payload = {
        userId: currentUserIdSafe(),
        sessionId: currentSessionId,
        numItems: Number(numItemsInputSafe?.value) || 5,
        bubbles: bubbleHistorySafe,
        products: latestProductsSafe,
        copies: latestCopiesSafe,
        meta: latestMetaSafe,
        guess: latestGuessSafe,
        updatedAt: Date.now()
    };
    try {
        localStorage.setItem(storageKeySafe(payload.userId), JSON.stringify(payload));
    } catch (error) {
        // Ignore storage exceptions to keep runtime behavior unchanged.
    }
}

function readPersistedStateSafe(userId) {
    if (!canUseStorageSafe()) {
        return null;
    }
    try {
        const raw = localStorage.getItem(storageKeySafe(userId));
        if (!raw) {
            return null;
        }
        return JSON.parse(raw);
    } catch (error) {
        return null;
    }
}

function addBubbleSafe(role, text, type = "assistant", persist = true, record = true) {
    const node = appendBubbleNodeSafe(role, text, type);
    if (record) {
        bubbleHistorySafe.push({ role, text, type });
        if (bubbleHistorySafe.length > MAX_STORED_BUBBLES) {
            bubbleHistorySafe = bubbleHistorySafe.slice(-MAX_STORED_BUBBLES);
        }
    }
    if (persist) {
        persistChatStateSafe();
    }
    return node;
}

function renderBubbleHistorySafe() {
    chatFeedSafe.innerHTML = "";
    if (!bubbleHistorySafe.length) {
        bubbleHistorySafe = [{ role: "Assistant", text: DEFAULT_ASSISTANT_TEXT, type: "assistant" }];
    }
    bubbleHistorySafe.forEach((bubble) => {
        appendBubbleNodeSafe(bubble.role, bubble.text, bubble.type);
    });
}

function renderProductsSafe(products = [], persist = true) {
    latestProductsSafe = Array.isArray(products) ? products : [];
    if (!latestProductsSafe.length) {
        productsSafe.className = "stack empty";
        productsSafe.textContent = "No recommendation yet.";
    } else {
        productsSafe.className = "stack";
        productsSafe.innerHTML = latestProductsSafe.map((item) => `
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
    if (persist) {
        persistChatStateSafe();
    }
}

function renderCopiesSafe(copies = [], persist = true) {
    latestCopiesSafe = Array.isArray(copies) ? copies : [];
    if (!latestCopiesSafe.length) {
        copiesSafe.className = "stack empty";
        copiesSafe.textContent = "No copy generated yet.";
    } else {
        copiesSafe.className = "stack";
        copiesSafe.innerHTML = latestCopiesSafe.map((item) => `
            <article class="item">
                <h3>${esc(item.product_id || "-")}</h3>
                <p>${esc(item.copy || "-")}</p>
            </article>
        `).join("");
    }
    if (persist) {
        persistChatStateSafe();
    }
}

function renderMetaSafe(data = {}, persist = true) {
    latestMetaSafe = {
        requestId: data.requestId || "-",
        userId: data.userId || "-",
        experimentGroup: data.experimentGroup || "-",
        totalLatencyMs: typeof data.totalLatencyMs === "number" ? data.totalLatencyMs : null,
        agentResults: data.agentResults ?? {}
    };

    requestIdSafe.textContent = latestMetaSafe.requestId;
    responseUserIdSafe.textContent = latestMetaSafe.userId;
    experimentGroupSafe.textContent = latestMetaSafe.experimentGroup;
    latencySafe.textContent = latestMetaSafe.totalLatencyMs == null
        ? "-"
        : `${latestMetaSafe.totalLatencyMs.toFixed(1)} ms`;
    agentJsonSafe.textContent = JSON.stringify(latestMetaSafe.agentResults, null, 2);
    if (persist) {
        persistChatStateSafe();
    }
}

function renderGuessYouLikeSafe(items = [], strategy = "profile+catalog", persist = true) {
    if (!guessListSafe) {
        return;
    }

    latestGuessSafe = {
        strategy: strategy || "profile+catalog",
        items: Array.isArray(items) ? items : []
    };
    guessStrategySafe.textContent = latestGuessSafe.strategy;

    if (!latestGuessSafe.items.length) {
        guessListSafe.className = "guess-list empty";
        guessListSafe.textContent = "No guess-you-like items yet.";
    } else {
        guessListSafe.className = "guess-list";
        guessListSafe.innerHTML = latestGuessSafe.items.map((item) => `
            <article class="guess-item">
                <h3>${esc(item.name || item.productId || "-")}</h3>
                <p class="meta">${esc(item.category || "-")} / ${esc(item.brand || "-")} / CNY ${item.price ?? "-"}</p>
                <p class="reason">${esc(item.reason || "Recommended from your profile and context.")}</p>
            </article>
        `).join("");
    }

    if (persist) {
        persistChatStateSafe();
    }
}

function fallbackPitchSafe(product, rank) {
    const name = product?.name || product?.productId || "this item";
    const pricePart = typeof product?.price === "number" ? `, price CNY ${product.price}` : "";
    const angle = rank === 0
        ? "is strong on overall experience"
        : rank === 1
            ? "offers a good balance of price and performance"
            : "is also a competitive option in this category";
    return `${name} ${angle}${pricePart}.`;
}

function summaryTextSafe(data) {
    const products = (data.products || []).slice(0, 3);
    if (!products.length) {
        return "No ideal products matched this time. You can add budget, brand, or usage needs and I will refine it.";
    }

    const copyMap = new Map(
        (data.marketingCopies || [])
            .filter((item) => item && item.product_id)
            .map((item) => [String(item.product_id), String(item.copy || "")])
    );

    const highlights = products.map((product, index) => {
        const name = product?.name || product?.productId || "product";
        const copy = copyMap.get(String(product?.productId)) || fallbackPitchSafe(product, index);
        return `[${name}] ${copy}`;
    }).join(" ; ");

    return `Here are better-fit options for you: ${highlights}`;
}

function resetViewStateSafe() {
    currentSessionId = null;
    bubbleHistorySafe = [{ role: "Assistant", text: DEFAULT_ASSISTANT_TEXT, type: "assistant" }];
    latestProductsSafe = [];
    latestCopiesSafe = [];
    latestMetaSafe = {};
    latestGuessSafe = { strategy: "profile+catalog", items: [] };
    renderBubbleHistorySafe();
    renderProductsSafe([], false);
    renderCopiesSafe([], false);
    renderMetaSafe({}, false);
    renderGuessYouLikeSafe([], "profile+catalog", false);
}

function loadPersistedSessionSafe() {
    const userId = currentUserIdSafe();
    const state = readPersistedStateSafe(userId);
    if (!state) {
        resetViewStateSafe();
        persistChatStateSafe();
        return;
    }

    currentSessionId = state.sessionId || null;
    if (state.numItems && Number(state.numItems) > 0) {
        numItemsInputSafe.value = String(state.numItems);
    }

    bubbleHistorySafe = Array.isArray(state.bubbles)
        ? state.bubbles.map(normalizeBubbleSafe).filter(Boolean)
        : [];
    latestProductsSafe = Array.isArray(state.products) ? state.products : [];
    latestCopiesSafe = Array.isArray(state.copies) ? state.copies : [];
    latestMetaSafe = state.meta && typeof state.meta === "object" ? state.meta : {};
    latestGuessSafe = state.guess && typeof state.guess === "object"
        ? {
            strategy: state.guess.strategy || "profile+catalog",
            items: Array.isArray(state.guess.items) ? state.guess.items : []
        }
        : { strategy: "profile+catalog", items: [] };

    renderBubbleHistorySafe();
    renderProductsSafe(latestProductsSafe, false);
    renderCopiesSafe(latestCopiesSafe, false);
    renderMetaSafe(latestMetaSafe, false);
    renderGuessYouLikeSafe(latestGuessSafe.items, latestGuessSafe.strategy, false);
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
    guessListSafe.textContent = "Refreshing suggestions...";
    try {
        const data = await requestGuessYouLikeSafe({
            userId: currentUserIdSafe(),
            sessionId: currentSessionId,
            numItems: Number(numItemsInputSafe.value) || 6
        });
        renderGuessYouLikeSafe(data.items || [], data.strategy || "profile+catalog");
    } catch (error) {
        guessListSafe.className = "guess-list empty";
        guessListSafe.textContent = `Failed to load suggestions: ${error.message}`;
    }
}

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const query = queryInputSafe.value.trim();
    if (!query) {
        return;
    }

    const payload = {
        userId: currentUserIdSafe(),
        query,
        numItems: Number(numItemsInputSafe.value) || 5
    };
    if (currentSessionId) {
        payload.sessionId = currentSessionId;
    }

    addBubbleSafe("User", query, "user", true, true);
    queryInputSafe.value = "";
    queryInputSafe.focus();

    setStatusSafe("loading");
    sendButtonSafe.disabled = true;
    const pendingNode = addBubbleSafe("Assistant", "Analyzing your request...", "system", false, false);

    try {
        const data = await requestRecommendationSafe(payload);
        if (data.sessionId) {
            currentSessionId = data.sessionId;
        }
        pendingNode.remove();
        renderMetaSafe(data);
        renderProductsSafe(data.products);
        renderCopiesSafe(data.marketingCopies);
        addBubbleSafe("Assistant", summaryTextSafe(data), "assistant", true, true);
        await refreshGuessYouLikeSafe();
        setStatusSafe("success");
    } catch (error) {
        pendingNode.remove();
        addBubbleSafe("Assistant", `Request failed: ${error.message}`, "assistant", true, true);
        setStatusSafe("error");
    } finally {
        sendButtonSafe.disabled = false;
    }
});

setStatusSafe("idle");
loadPersistedSessionSafe();
refreshGuessYouLikeSafe();

userIdInputSafe?.addEventListener("change", () => {
    loadPersistedSessionSafe();
    refreshGuessYouLikeSafe();
});

numItemsInputSafe?.addEventListener("change", () => {
    persistChatStateSafe();
    refreshGuessYouLikeSafe();
});
