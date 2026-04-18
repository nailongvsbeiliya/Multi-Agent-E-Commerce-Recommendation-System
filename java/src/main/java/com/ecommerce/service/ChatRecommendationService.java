package com.ecommerce.service;

import com.ecommerce.model.RecommendationRequest;
import com.ecommerce.model.RecommendationResponse;
import com.ecommerce.model.TextRecommendationRequest;
import com.ecommerce.orchestrator.SupervisorOrchestrator;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class ChatRecommendationService {

    private final NaturalLanguageRequestService naturalLanguageRequestService;
    private final SupervisorOrchestrator supervisorOrchestrator;
    private final ChatSessionMemoryService chatSessionMemoryService;

    public ChatRecommendationService(NaturalLanguageRequestService naturalLanguageRequestService,
                                     SupervisorOrchestrator supervisorOrchestrator,
                                     ChatSessionMemoryService chatSessionMemoryService) {
        this.naturalLanguageRequestService = naturalLanguageRequestService;
        this.supervisorOrchestrator = supervisorOrchestrator;
        this.chatSessionMemoryService = chatSessionMemoryService;
    }

    public RecommendationResponse recommendByText(TextRecommendationRequest request) {
        TextRecommendationRequest safeRequest = request == null ? new TextRecommendationRequest() : request;
        String userId = safeUserId(safeRequest.getUserId());
        String sessionId = chatSessionMemoryService.resolveSessionId(safeRequest.getSessionId(), userId);

        if (Boolean.TRUE.equals(safeRequest.getResetSession())) {
            chatSessionMemoryService.reset(sessionId);
        }

        ChatSessionMemoryService.MemoryHints hints = chatSessionMemoryService.buildHints(
                sessionId,
                safeValue(safeRequest.getQuery()),
                safeRequest.getNumItems()
        );

        Map<String, Object> mergedContext = new HashMap<>();
        if (safeRequest.getContext() != null) {
            mergedContext.putAll(safeRequest.getContext());
        }
        if (!hints.hardCategoryIntents().isEmpty()) {
            mergedContext.put("hard_category_intents", hints.hardCategoryIntents());
        }
        if (!hints.excludeProductIds().isEmpty()) {
            mergedContext.put("exclude_product_ids", hints.excludeProductIds());
        }
        if (hints.memoryPriceMax() != null) {
            mergedContext.put("memory_price_max", hints.memoryPriceMax());
        }
        if (!hints.recentQueries().isEmpty()) {
            mergedContext.put("session_recent_queries", hints.recentQueries());
        }
        if (!hints.lastRecommendedProductIds().isEmpty()) {
            mergedContext.put("session_last_product_ids", hints.lastRecommendedProductIds());
        }
        if (!hints.lastRecommendedCategories().isEmpty()) {
            mergedContext.put("session_last_categories", hints.lastRecommendedCategories());
        }

        String enrichedQuery = enrichQueryWithSessionHint(safeValue(safeRequest.getQuery()), hints);
        TextRecommendationRequest normalizedInput = TextRecommendationRequest.builder()
                .userId(userId)
                .sessionId(sessionId)
                .resetSession(false)
                .query(enrichedQuery)
                .scene(safeRequest.getScene())
                .numItems(safeRequest.getNumItems())
                .context(mergedContext)
                .build();

        RecommendationRequest normalized = naturalLanguageRequestService.normalize(normalizedInput);
        RecommendationResponse response = supervisorOrchestrator.recommend(normalized);
        response.setSessionId(sessionId);
        response.setSessionMemory(Map.of(
                "session_id", sessionId,
                "recent_queries", hints.recentQueries(),
                "last_recommended_product_ids", hints.lastRecommendedProductIds(),
                "last_recommended_categories", hints.lastRecommendedCategories(),
                "applied_exclude_ids", hints.excludeProductIds(),
                "applied_price_max", hints.memoryPriceMax() == null ? "-" : hints.memoryPriceMax(),
                "enriched_query", enrichedQuery
        ));

        chatSessionMemoryService.recordTurn(sessionId, safeRequest.getQuery(), response.getProducts());
        return response;
    }

    private String enrichQueryWithSessionHint(String query, ChatSessionMemoryService.MemoryHints hints) {
        String safeQuery = safeValue(query).trim();
        if (safeQuery.isBlank()) {
            return hints.lastRecommendedCategories().isEmpty() ? "推荐商品" : hints.lastRecommendedCategories().get(0);
        }

        if (isRefinementQuery(safeQuery) && !containsCategoryWords(safeQuery) && !hints.lastRecommendedCategories().isEmpty()) {
            return safeQuery + " " + hints.lastRecommendedCategories().get(0);
        }
        return safeQuery;
    }

    private boolean isRefinementQuery(String query) {
        String raw = safeValue(query).toLowerCase();
        return containsAny(raw, "便宜", "贵", "预算", "白色", "黑色", "再来", "换一批", "其他", "别的", "不要", "上一批", "刚才");
    }

    private boolean containsCategoryWords(String query) {
        String raw = safeValue(query).toLowerCase();
        return containsAny(raw, "耳机", "手机", "平板", "笔记本", "显示器", "相机", "手表", "穿戴", "充电器", "路由器");
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String safeUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return "u001";
        }
        return userId.trim();
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }
}
