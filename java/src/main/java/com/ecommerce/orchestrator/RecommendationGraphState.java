package com.ecommerce.orchestrator;

import com.ecommerce.model.AgentResult;
import com.ecommerce.model.ExperimentAssignment;
import com.ecommerce.model.Product;
import com.ecommerce.model.RecommendationRequest;
import com.ecommerce.model.UserProfile;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class RecommendationGraphState extends AgentState {

    public static final String KEY_USER_ID = "userId";
    public static final String KEY_SCENE = "scene";
    public static final String KEY_NUM_ITEMS = "numItems";
    public static final String KEY_USER_QUERY = "userQuery";
    public static final String KEY_CONTEXT = "context";
    public static final String KEY_REQUEST_ID = "requestId";
    public static final String KEY_START_NANOS = "startNanos";
    public static final String KEY_EXPERIMENT = "experimentAssignment";
    public static final String KEY_USER_PROFILE = "userProfile";
    public static final String KEY_RECALLED_PRODUCTS = "recalledProducts";
    public static final String KEY_RANKED_PRODUCTS = "rankedProducts";
    public static final String KEY_AVAILABLE_IDS = "availableIds";
    public static final String KEY_PURCHASE_LIMITS = "purchaseLimits";
    public static final String KEY_LOW_STOCK_ALERTS = "lowStockAlerts";
    public static final String KEY_FINAL_PRODUCTS = "finalProducts";
    public static final String KEY_MARKETING_COPIES = "marketingCopies";
    public static final String KEY_AGENT_RESULTS = "agentResults";
    public static final String KEY_TOTAL_LATENCY_MS = "totalLatencyMs";

    public static final Map<String, Channel<?>> SCHEMA = Map.ofEntries(
            Map.entry(KEY_USER_ID, Channels.base((Supplier<String>) () -> "")),
            Map.entry(KEY_SCENE, Channels.base((Supplier<String>) () -> "homepage")),
            Map.entry(KEY_NUM_ITEMS, Channels.base((Supplier<Integer>) () -> 10)),
            Map.entry(KEY_USER_QUERY, Channels.base((Supplier<String>) () -> "")),
            Map.entry(KEY_CONTEXT, Channels.base((Supplier<Map<String, Object>>) HashMap::new)),
            Map.entry(KEY_REQUEST_ID, Channels.base((Supplier<String>) () -> "")),
            Map.entry(KEY_START_NANOS, Channels.base((Supplier<Long>) () -> 0L)),
            Map.entry(KEY_EXPERIMENT, Channels.base((Supplier<ExperimentAssignment>) ExperimentAssignment::new)),
            Map.entry(KEY_USER_PROFILE, Channels.base((Supplier<UserProfile>) UserProfile::new)),
            Map.entry(KEY_RECALLED_PRODUCTS, Channels.base((Supplier<List<Product>>) ArrayList::new)),
            Map.entry(KEY_RANKED_PRODUCTS, Channels.base((Supplier<List<Product>>) ArrayList::new)),
            Map.entry(KEY_AVAILABLE_IDS, Channels.base((Supplier<List<String>>) ArrayList::new)),
            Map.entry(KEY_PURCHASE_LIMITS, Channels.base((Supplier<Map<String, Integer>>) HashMap::new)),
            Map.entry(KEY_LOW_STOCK_ALERTS, Channels.base((Supplier<List<Map<String, Object>>>) ArrayList::new)),
            Map.entry(KEY_FINAL_PRODUCTS, Channels.base((Supplier<List<Product>>) ArrayList::new)),
            Map.entry(KEY_MARKETING_COPIES, Channels.base((Supplier<List<Map<String, String>>>) ArrayList::new)),
            Map.entry(KEY_AGENT_RESULTS, Channels.base((Supplier<Map<String, AgentResult>>) HashMap::new)),
            Map.entry(KEY_TOTAL_LATENCY_MS, Channels.base((Supplier<Double>) () -> 0.0))
    );

    public RecommendationGraphState(Map<String, Object> initData) {
        super(initData);
    }

    @SuppressWarnings("unchecked")
    private <T> T getOrDefault(String key, T defaultValue) {
        return (T) value(key).orElse(defaultValue);
    }

    public RecommendationRequest request() {
        return RecommendationRequest.builder()
                .userId(getOrDefault(KEY_USER_ID, ""))
                .scene(getOrDefault(KEY_SCENE, "homepage"))
                .numItems(getOrDefault(KEY_NUM_ITEMS, 10))
                .userQuery(getOrDefault(KEY_USER_QUERY, ""))
                .context(getOrDefault(KEY_CONTEXT, new HashMap<>()))
                .build();
    }

    public String requestId() {
        return getOrDefault(KEY_REQUEST_ID, "");
    }

    public long startNanos() {
        Number value = getOrDefault(KEY_START_NANOS, 0L);
        return value.longValue();
    }

    public ExperimentAssignment experiment() {
        return getOrDefault(KEY_EXPERIMENT, (ExperimentAssignment) null);
    }

    public UserProfile userProfile() {
        return getOrDefault(KEY_USER_PROFILE, (UserProfile) null);
    }

    public List<Product> recalledProducts() {
        return getOrDefault(KEY_RECALLED_PRODUCTS, List.of());
    }

    public List<Product> rankedProducts() {
        return getOrDefault(KEY_RANKED_PRODUCTS, List.of());
    }

    public List<String> availableIds() {
        return getOrDefault(KEY_AVAILABLE_IDS, List.of());
    }

    public Map<String, Integer> purchaseLimits() {
        return getOrDefault(KEY_PURCHASE_LIMITS, Map.of());
    }

    public List<Map<String, Object>> lowStockAlerts() {
        return getOrDefault(KEY_LOW_STOCK_ALERTS, List.of());
    }

    public List<Product> finalProducts() {
        return getOrDefault(KEY_FINAL_PRODUCTS, List.of());
    }

    public List<Map<String, String>> marketingCopies() {
        return getOrDefault(KEY_MARKETING_COPIES, List.of());
    }

    public Map<String, AgentResult> agentResults() {
        return getOrDefault(KEY_AGENT_RESULTS, Map.of());
    }

    public double totalLatencyMs() {
        Number value = getOrDefault(KEY_TOTAL_LATENCY_MS, 0.0);
        return value.doubleValue();
    }
}
