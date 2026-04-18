package com.ecommerce.orchestrator;

import com.ecommerce.agent.InventoryAgent;
import com.ecommerce.agent.MarketingCopyAgent;
import com.ecommerce.agent.ProductRecAgent;
import com.ecommerce.agent.UserProfileAgent;
import com.ecommerce.model.AgentResult;
import com.ecommerce.model.ExperimentAssignment;
import com.ecommerce.model.Product;
import com.ecommerce.model.RecommendationRequest;
import com.ecommerce.model.RecommendationResponse;
import com.ecommerce.model.UserProfile;
import com.ecommerce.service.ABTestService;
import com.ecommerce.service.RecommendationAggregator;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphDefinition;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class SupervisorOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SupervisorOrchestrator.class);

    private final UserProfileAgent userProfileAgent;
    private final ProductRecAgent productRecAgent;
    private final MarketingCopyAgent marketingCopyAgent;
    private final InventoryAgent inventoryAgent;
    private final ABTestService abTestService;
    private final RecommendationAggregator recommendationAggregator;
    private final CompiledGraph<RecommendationGraphState> recommendationGraph;

    public SupervisorOrchestrator(UserProfileAgent userProfileAgent,
                                  ProductRecAgent productRecAgent,
                                  MarketingCopyAgent marketingCopyAgent,
                                  InventoryAgent inventoryAgent,
                                  ABTestService abTestService,
                                  RecommendationAggregator recommendationAggregator) {
        this.userProfileAgent = userProfileAgent;
        this.productRecAgent = productRecAgent;
        this.marketingCopyAgent = marketingCopyAgent;
        this.inventoryAgent = inventoryAgent;
        this.abTestService = abTestService;
        this.recommendationAggregator = recommendationAggregator;
        this.recommendationGraph = buildGraph();
    }

    public RecommendationResponse recommend(RecommendationRequest request) {
        String requestId = UUID.randomUUID().toString();
        long start = System.nanoTime();
        log.info("[Supervisor] start request={} user={}", requestId, request.getUserId());

        RecommendationGraphState finalState = runGraph(request, requestId, start);
        log.info("[Supervisor] complete request={} latency={}ms products={}",
                requestId, String.format("%.1f", finalState.totalLatencyMs()), finalState.finalProducts().size());

        ExperimentAssignment experiment = finalState.experiment();
        return RecommendationResponse.builder()
                .requestId(requestId)
                .userId(request.getUserId())
                .products(finalState.finalProducts())
                .marketingCopies(finalState.marketingCopies())
                .experimentGroup(experiment != null ? experiment.getGroup() : "control")
                .experimentInfo(Map.of(
                        "experiment_id", experiment != null ? experiment.getExperimentId() : "rec_strategy",
                        "bucket", experiment != null ? experiment.getBucket() : -1,
                        "strategy", experiment != null ? experiment.getStrategy() : "rule_based",
                        "posterior_means", experiment != null ? experiment.getPosteriorMeans() : Map.of()
                ))
                .purchaseLimits(finalState.purchaseLimits())
                .lowStockAlerts(finalState.lowStockAlerts())
                .agentResults(finalState.agentResults())
                .totalLatencyMs(finalState.totalLatencyMs())
                .build();
    }

    private CompiledGraph<RecommendationGraphState> buildGraph() {
        try {
            return new StateGraph<>(RecommendationGraphState.SCHEMA, RecommendationGraphState::new)
                    .addNode("assign_experiment", this::assignExperimentNode)
                    .addNode("phase1_parallel", this::phase1ParallelNode)
                    .addNode("phase2_parallel", this::phase2ParallelNode)
                    .addNode("aggregate", this::aggregateNode)
                    .addNode("marketing_copy", this::marketingCopyNode)
                    .addNode("finalize", this::finalizeNode)
                    .addEdge(GraphDefinition.START, "assign_experiment")
                    .addEdge("assign_experiment", "phase1_parallel")
                    .addEdge("phase1_parallel", "phase2_parallel")
                    .addEdge("phase2_parallel", "aggregate")
                    .addEdge("aggregate", "marketing_copy")
                    .addEdge("marketing_copy", "finalize")
                    .addEdge("finalize", GraphDefinition.END)
                    .compile();
        } catch (GraphStateException e) {
            throw new IllegalStateException("Failed to build recommendation graph", e);
        }
    }

    private RecommendationGraphState runGraph(RecommendationRequest request, String requestId, long startNanos) {
        String userId = request.getUserId() != null ? request.getUserId() : "anonymous";
        String scene = request.getScene() != null ? request.getScene() : "homepage";
        int numItems = request.getNumItems() > 0 ? request.getNumItems() : 10;
        String userQuery = request.getUserQuery() != null ? request.getUserQuery() : "";
        try {
            return recommendationGraph.invoke(
                            Map.of(
                                    RecommendationGraphState.KEY_USER_ID, userId,
                                    RecommendationGraphState.KEY_SCENE, scene,
                                    RecommendationGraphState.KEY_NUM_ITEMS, numItems,
                                    RecommendationGraphState.KEY_USER_QUERY, userQuery,
                                    RecommendationGraphState.KEY_CONTEXT, request.getContext() != null ? request.getContext() : Map.of(),
                                    RecommendationGraphState.KEY_REQUEST_ID, requestId,
                                    RecommendationGraphState.KEY_START_NANOS, startNanos))
                    .orElseThrow(() -> new IllegalStateException("Graph execution returned no state"));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to execute recommendation graph", e);
        }
    }

    private CompletableFuture<Map<String, Object>> assignExperimentNode(RecommendationGraphState state) {
        ExperimentAssignment experiment = abTestService.assign(state.request().getUserId());
        return CompletableFuture.completedFuture(Map.of(RecommendationGraphState.KEY_EXPERIMENT, experiment));
    }

    private CompletableFuture<Map<String, Object>> phase1ParallelNode(RecommendationGraphState state) {
        RecommendationRequest request = state.request();
        ExperimentAssignment experiment = state.experiment();
        Map<String, Object> requestContext = request.getContext() != null ? request.getContext() : Map.of();

        CompletableFuture<AgentResult> profileFuture = userProfileAgent.runAsync(
                Map.of(
                        "userId", request.getUserId(),
                        "context", requestContext.isEmpty() ? Map.of("scene", request.getScene()) : requestContext
                ));

        Map<String, Object> recallParams = new java.util.HashMap<>();
        recallParams.put("mode", "recall");
        recallParams.put("numItems", request.getNumItems() * 2);
        recallParams.put("strategy", experiment != null ? experiment.getStrategy() : "rule_based");
        recallParams.put("userQuery", request.getUserQuery());
        recallParams.put("queryTokens", requestContext.getOrDefault("query_tokens", List.of()));
        recallParams.put("negativeQueryTokens", requestContext.getOrDefault("negative_query_tokens", List.of()));
        recallParams.put("hardCategoryIntents", requestContext.getOrDefault("hard_category_intents", List.of()));
        recallParams.put("excludeProductIds", requestContext.getOrDefault("exclude_product_ids", List.of()));
        if (requestContext.get("memory_price_max") != null) {
            recallParams.put("memoryPriceMax", requestContext.get("memory_price_max"));
        }
        CompletableFuture<AgentResult> recFuture = productRecAgent.runAsync(recallParams);

        AgentResult profileResult = profileFuture.join();
        AgentResult recResult = recFuture.join();
        Map<String, AgentResult> agentResults = new java.util.HashMap<>(state.agentResults());
        agentResults.put("user_profile", profileResult);
        agentResults.put("product_recall", recResult);

        UserProfile profile = profileResult.getData() != null
                ? (UserProfile) profileResult.getData().get("profile") : null;
        @SuppressWarnings("unchecked")
        List<Product> recalledProducts = recResult.getData() != null
                ? (List<Product>) recResult.getData().get("products") : List.of();
        return CompletableFuture.completedFuture(Map.of(
                RecommendationGraphState.KEY_AGENT_RESULTS, agentResults,
                RecommendationGraphState.KEY_USER_PROFILE, profile,
                RecommendationGraphState.KEY_RECALLED_PRODUCTS, recalledProducts
        ));
    }

    private CompletableFuture<Map<String, Object>> phase2ParallelNode(RecommendationGraphState state) {
        RecommendationRequest request = state.request();
        ExperimentAssignment experiment = state.experiment();
        UserProfile profile = state.userProfile() != null ? state.userProfile() : new UserProfile();
        List<Product> recalledProducts = state.recalledProducts();
        Map<String, Object> requestContext = request.getContext() != null ? request.getContext() : Map.of();

        Map<String, Object> rerankParams = new java.util.HashMap<>();
        rerankParams.put("mode", "rerank");
        rerankParams.put("userProfile", profile);
        rerankParams.put("numItems", request.getNumItems());
        rerankParams.put("strategy", experiment != null ? experiment.getStrategy() : "rule_based");
        rerankParams.put("candidates", recalledProducts);
        rerankParams.put("userQuery", request.getUserQuery());
        rerankParams.put("queryTokens", requestContext.getOrDefault("query_tokens", List.of()));
        rerankParams.put("negativeQueryTokens", requestContext.getOrDefault("negative_query_tokens", List.of()));
        rerankParams.put("hardCategoryIntents", requestContext.getOrDefault("hard_category_intents", List.of()));
        rerankParams.put("excludeProductIds", requestContext.getOrDefault("exclude_product_ids", List.of()));
        if (requestContext.get("memory_price_max") != null) {
            rerankParams.put("memoryPriceMax", requestContext.get("memory_price_max"));
        }
        CompletableFuture<AgentResult> rerankFuture = productRecAgent.runAsync(rerankParams);
        CompletableFuture<AgentResult> inventoryFuture = inventoryAgent.runAsync(Map.of("products", recalledProducts));

        AgentResult rerankResult = rerankFuture.join();
        AgentResult inventoryResult = inventoryFuture.join();
        Map<String, AgentResult> agentResults = new java.util.HashMap<>(state.agentResults());
        agentResults.put("rerank", rerankResult);
        agentResults.put("inventory", inventoryResult);

        @SuppressWarnings("unchecked")
        List<Product> rankedProducts = rerankResult.getData() != null
                ? (List<Product>) rerankResult.getData().get("products") : recalledProducts;
        @SuppressWarnings("unchecked")
        List<String> availableIds = inventoryResult.getData() != null
                ? (List<String>) inventoryResult.getData().get("available_products") : List.of();
        @SuppressWarnings("unchecked")
        Map<String, Integer> purchaseLimits = inventoryResult.getData() != null
                ? (Map<String, Integer>) inventoryResult.getData().get("purchase_limits") : Map.of();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lowStockAlerts = inventoryResult.getData() != null
                ? (List<Map<String, Object>>) inventoryResult.getData().get("low_stock_alerts") : List.of();

        return CompletableFuture.completedFuture(Map.of(
                RecommendationGraphState.KEY_AGENT_RESULTS, agentResults,
                RecommendationGraphState.KEY_RANKED_PRODUCTS, rankedProducts,
                RecommendationGraphState.KEY_AVAILABLE_IDS, availableIds,
                RecommendationGraphState.KEY_PURCHASE_LIMITS, purchaseLimits,
                RecommendationGraphState.KEY_LOW_STOCK_ALERTS, lowStockAlerts
        ));
    }

    private CompletableFuture<Map<String, Object>> aggregateNode(RecommendationGraphState state) {
        List<Product> finalProducts = recommendationAggregator.aggregate(
                state.rankedProducts(),
                state.recalledProducts(),
                state.availableIds(),
                state.request().getNumItems()
        );
        return CompletableFuture.completedFuture(Map.of(RecommendationGraphState.KEY_FINAL_PRODUCTS, finalProducts));
    }

    private CompletableFuture<Map<String, Object>> marketingCopyNode(RecommendationGraphState state) {
        AgentResult copyResult = marketingCopyAgent.runAsync(
                Map.of(
                        "userProfile", state.userProfile() != null ? state.userProfile() : new UserProfile(),
                        "products", state.finalProducts()
                )).join();

        Map<String, AgentResult> agentResults = new java.util.HashMap<>(state.agentResults());
        agentResults.put("marketing_copy", copyResult);
        @SuppressWarnings("unchecked")
        List<Map<String, String>> copies = copyResult.getData() != null
                ? (List<Map<String, String>>) copyResult.getData().get("copies") : List.of();
        return CompletableFuture.completedFuture(Map.of(
                RecommendationGraphState.KEY_AGENT_RESULTS, agentResults,
                RecommendationGraphState.KEY_MARKETING_COPIES, copies
        ));
    }

    private CompletableFuture<Map<String, Object>> finalizeNode(RecommendationGraphState state) {
        double totalLatency = (System.nanoTime() - state.startNanos()) / 1_000_000.0;
        return CompletableFuture.completedFuture(Map.of(RecommendationGraphState.KEY_TOTAL_LATENCY_MS, totalLatency));
    }
}
