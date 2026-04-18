const form = document.getElementById("chatForm");
const queryInputSafe = document.getElementById("queryInput");
const userIdInputSafe = document.getElementById("userIdInput");
const numItemsInputSafe = document.getElementById("numItemsInput");
const sendButtonSafe = document.getElementById("sendButton");
const chatFeedSafe = document.getElementById("chatFeed");
const statusBadgeSafe = document.getElementById("statusBadge");
const newChatButtonSafe = document.getElementById("newChatButton");
const conversationSelectSafe = document.getElementById("conversationSelect");

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

const STORE_PREFIX = "multi-agent-chat-store:v2:";
const LEGACY_STATE_PREFIX = "multi-agent-chat-state:v1:";
const MAX_STORED_BUBBLES = 120;
const DEFAULT_ASSISTANT_TEXT = "Tell me what you want to buy. I will run profile, retrieval, rerank and inventory decisions for you.";

let activeUserIdSafe = "u001";
let currentStoreSafe = { activeConversationId: null, conversations: [] };
let currentConversationIdSafe = null;
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
    return `${STORE_PREFIX}${userId || "u001"}`;
}

function legacyStorageKeySafe(userId) {
    return `${LEGACY_STATE_PREFIX}${userId || "u001"}`;
}

function canUseStorageSafe() {
    try {
        return typeof window !== "undefined" && !!window.localStorage;
    } catch (error) {
        return false;
    }
}

function nowTsSafe() {
    return Date.now();
}

function createConversationIdSafe() {
    return `c-${nowTsSafe().toString(36)}-${Math.random().toString(36).slice(2, 7)}`;
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

function normalizeConversationSafe(raw) {
    const fallback = createNewConversationSafe();
    if (!raw || typeof raw !== "object") {
        return fallback;
    }

    const bubbles = Array.isArray(raw.bubbles)
        ? raw.bubbles.map(normalizeBubbleSafe).filter(Boolean).slice(-MAX_STORED_BUBBLES)
        : [];

    return {
        id: raw.id ? String(raw.id) : fallback.id,
        title: raw.title ? String(raw.title) : "New Chat",
        updatedAt: Number(raw.updatedAt) || nowTsSafe(),
        sessionId: raw.sessionId ? String(raw.sessionId) : null,
        numItems: Number(raw.numItems) > 0 ? Number(raw.numItems) : 5,
        bubbles: bubbles.length ? bubbles : fallback.bubbles,
        products: Array.isArray(raw.products) ? raw.products : [],
        copies: Array.isArray(raw.copies) ? raw.copies : [],
        meta: raw.meta && typeof raw.meta === "object" ? raw.meta : {},
        guess: raw.guess && typeof raw.guess === "object"
            ? {
                strategy: raw.guess.strategy ? String(raw.guess.strategy) : "profile+catalog",
                items: Array.isArray(raw.guess.items) ? raw.guess.items : []
            }
            : { strategy: "profile+catalog", items: [] }
    };
}

function createNewConversationSafe() {
    return {
        id: createConversationIdSafe(),
        title: "New Chat",
        updatedAt: nowTsSafe(),
        sessionId: null,
        numItems: Number(numItemsInputSafe?.value) || 5,
        bubbles: [{ role: "Assistant", text: DEFAULT_ASSISTANT_TEXT, type: "assistant" }],
        products: [],
        copies: [],
        meta: {},
        guess: { strategy: "profile+catalog", items: [] }
    };
}

function loadLegacyConversationSafe(userId) {
    if (!canUseStorageSafe()) {
        return null;
    }
    try {
        const raw = localStorage.getItem(legacyStorageKeySafe(userId));
        if (!raw) {
            return null;
        }
        const parsed = JSON.parse(raw);
        const candidate = {
            id: createConversationIdSafe(),
            title: "Legacy Chat",
            updatedAt: Number(parsed?.updatedAt) || nowTsSafe(),
            sessionId: parsed?.sessionId || null,
            numItems: Number(parsed?.numItems) > 0 ? Number(parsed.numItems) : 5,
            bubbles: Array.isArray(parsed?.bubbles) ? parsed.bubbles : [],
            products: Array.isArray(parsed?.products) ? parsed.products : [],
            copies: Array.isArray(parsed?.copies) ? parsed.copies : [],
            meta: parsed?.meta && typeof parsed.meta === "object" ? parsed.meta : {},
            guess: parsed?.guess && typeof parsed.guess === "object"
                ? parsed.guess
                : { strategy: "profile+catalog", items: [] }
        };
        return normalizeConversationSafe(candidate);
    } catch (error) {
        return null;
    }
}

function loadStoreSafe(userId) {
    const fallback = { activeConversationId: null, conversations: [createNewConversationSafe()] };
    if (!canUseStorageSafe()) {
        fallback.activeConversationId = fallback.conversations[0].id;
        return fallback;
    }

    try {
        const raw = localStorage.getItem(storageKeySafe(userId));
        if (!raw) {
            const legacy = loadLegacyConversationSafe(userId);
            if (legacy) {
                return { activeConversationId: legacy.id, conversations: [legacy] };
            }
            fallback.activeConversationId = fallback.conversations[0].id;
            return fallback;
        }
        const parsed = JSON.parse(raw);
        const conversations = Array.isArray(parsed?.conversations)
            ? parsed.conversations.map(normalizeConversationSafe)
            : [];
        if (!conversations.length) {
            fallback.activeConversationId = fallback.conversations[0].id;
            return fallback;
        }
        const activeId = parsed?.activeConversationId
            && conversations.some((item) => item.id === parsed.activeConversationId)
            ? parsed.activeConversationId
            : conversations[0].id;
        return { activeConversationId: activeId, conversations };
    } catch (error) {
        fallback.activeConversationId = fallback.conversations[0].id;
        return fallback;
    }
}

function saveStoreSafe(userId, store) {
    if (!canUseStorageSafe()) {
        return;
    }
    try {
        localStorage.setItem(storageKeySafe(userId), JSON.stringify(store));
    } catch (error) {
        // Ignore local storage errors.
    }
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

function addBubbleSafe(role, text, type = "assistant", persist = true, record = true) {
    const node = appendBubbleNodeSafe(role, text, type);
    if (record) {
        bubbleHistorySafe.push({ role, text, type });
        if (bubbleHistorySafe.length > MAX_STORED_BUBBLES) {
            bubbleHistorySafe = bubbleHistorySafe.slice(-MAX_STORED_BUBBLES);
        }
    }
    if (persist) {
        persistCurrentConversationSafe();
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
        persistCurrentConversationSafe();
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
        persistCurrentConversationSafe();
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
        persistCurrentConversationSafe();
    }
}

function renderGuessYouLikeSafe(items = [], strategy = "profile+catalog", persist = true) {
    latestGuessSafe = {
        strategy: strategy || "profile+catalog",
        items: Array.isArray(items) ? items : []
    };
    if (guessStrategySafe) {
        guessStrategySafe.textContent = latestGuessSafe.strategy;
    }
    if (!guessListSafe) {
        return;
    }
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
        persistCurrentConversationSafe();
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

function deriveConversationTitleSafe() {
    const firstUser = bubbleHistorySafe.find((bubble) => bubble.type === "user");
    if (!firstUser || !firstUser.text) {
        return "New Chat";
    }
    const title = firstUser.text.trim().replace(/\s+/g, " ");
    return title.length > 24 ? `${title.slice(0, 24)}...` : title;
}

function snapshotConversationSafe() {
    return {
        id: currentConversationIdSafe || createConversationIdSafe(),
        title: deriveConversationTitleSafe(),
        updatedAt: nowTsSafe(),
        sessionId: currentSessionId,
        numItems: Number(numItemsInputSafe?.value) || 5,
        bubbles: bubbleHistorySafe.slice(-MAX_STORED_BUBBLES),
        products: latestProductsSafe,
        copies: latestCopiesSafe,
        meta: latestMetaSafe,
        guess: latestGuessSafe
    };
}

function upsertConversationSafe(conversation) {
    const normalized = normalizeConversationSafe(conversation);
    const index = currentStoreSafe.conversations.findIndex((item) => item.id === normalized.id);
    if (index >= 0) {
        currentStoreSafe.conversations[index] = normalized;
    } else {
        currentStoreSafe.conversations.push(normalized);
    }
    currentStoreSafe.conversations.sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0));
    currentStoreSafe.activeConversationId = normalized.id;
    currentConversationIdSafe = normalized.id;
}

function renderConversationSelectorSafe() {
    if (!conversationSelectSafe) {
        return;
    }
    const activeId = currentConversationIdSafe;
    conversationSelectSafe.innerHTML = currentStoreSafe.conversations.map((item) => {
        const label = `${item.title || "New Chat"} (${new Date(item.updatedAt || nowTsSafe()).toLocaleString()})`;
        return `<option value="${esc(item.id)}">${esc(label)}</option>`;
    }).join("");
    conversationSelectSafe.value = activeId || "";
}

function persistCurrentConversationSafe(userIdOverride) {
    const targetUser = userIdOverride || activeUserIdSafe || currentUserIdSafe();
    const snapshot = snapshotConversationSafe();
    upsertConversationSafe(snapshot);
    saveStoreSafe(targetUser, currentStoreSafe);
    renderConversationSelectorSafe();
}

function applyConversationSafe(conversation) {
    const normalized = normalizeConversationSafe(conversation);
    currentConversationIdSafe = normalized.id;
    currentSessionId = normalized.sessionId;
    bubbleHistorySafe = normalized.bubbles;
    latestProductsSafe = normalized.products;
    latestCopiesSafe = normalized.copies;
    latestMetaSafe = normalized.meta;
    latestGuessSafe = normalized.guess;
    numItemsInputSafe.value = String(normalized.numItems || 5);

    renderBubbleHistorySafe();
    renderProductsSafe(latestProductsSafe, false);
    renderCopiesSafe(latestCopiesSafe, false);
    renderMetaSafe(latestMetaSafe, false);
    renderGuessYouLikeSafe(latestGuessSafe.items, latestGuessSafe.strategy, false);
    renderConversationSelectorSafe();
}

function getActiveConversationSafe() {
    const active = currentStoreSafe.conversations.find((item) => item.id === currentStoreSafe.activeConversationId);
    if (active) {
        return active;
    }
    return currentStoreSafe.conversations[0] || createNewConversationSafe();
}

function loadStoreForUserSafe(userId) {
    currentStoreSafe = loadStoreSafe(userId);
    currentStoreSafe.conversations.sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0));
    const active = getActiveConversationSafe();
    currentStoreSafe.activeConversationId = active.id;
    applyConversationSafe(active);
    saveStoreSafe(userId, currentStoreSafe);
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
        renderGuessYouLikeSafe(data.items || [], data.strategy || "profile+catalog", true);
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
        renderMetaSafe(data, true);
        renderProductsSafe(data.products, true);
        renderCopiesSafe(data.marketingCopies, true);
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

newChatButtonSafe?.addEventListener("click", async () => {
    persistCurrentConversationSafe();
    const newConversation = createNewConversationSafe();
    upsertConversationSafe(newConversation);
    applyConversationSafe(newConversation);
    saveStoreSafe(activeUserIdSafe, currentStoreSafe);
    await refreshGuessYouLikeSafe();
});

conversationSelectSafe?.addEventListener("change", async () => {
    const targetId = conversationSelectSafe.value;
    if (!targetId || targetId === currentConversationIdSafe) {
        return;
    }
    persistCurrentConversationSafe();
    const target = currentStoreSafe.conversations.find((item) => item.id === targetId);
    if (!target) {
        return;
    }
    currentStoreSafe.activeConversationId = targetId;
    applyConversationSafe(target);
    saveStoreSafe(activeUserIdSafe, currentStoreSafe);
    await refreshGuessYouLikeSafe();
});

userIdInputSafe?.addEventListener("change", async () => {
    persistCurrentConversationSafe(activeUserIdSafe);
    activeUserIdSafe = currentUserIdSafe();
    loadStoreForUserSafe(activeUserIdSafe);
    await refreshGuessYouLikeSafe();
});

numItemsInputSafe?.addEventListener("change", () => {
    persistCurrentConversationSafe();
});

setStatusSafe("idle");
activeUserIdSafe = currentUserIdSafe();
loadStoreForUserSafe(activeUserIdSafe);
refreshGuessYouLikeSafe();
