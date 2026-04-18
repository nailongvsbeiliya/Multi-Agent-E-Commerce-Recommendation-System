package com.ecommerce.service;

import com.ecommerce.model.Product;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ChatSessionMemoryService {

    private static final int MAX_QUERY_HISTORY = 12;

    private final ConcurrentMap<String, SessionState> sessions = new ConcurrentHashMap<>();

    public String resolveSessionId(String sessionId, String userId) {
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionId.trim();
        }
        String safeUserId = userId == null || userId.isBlank() ? "anonymous" : userId.trim();
        return "sess-" + safeUserId + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public void reset(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        sessions.remove(sessionId.trim());
    }

    public MemoryHints buildHints(String sessionId, String query, Integer numItems) {
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            return MemoryHints.empty();
        }

        String safeQuery = query == null ? "" : query.trim();
        int expectedNumItems = numItems == null || numItems <= 0 ? 5 : numItems;
        boolean refinement = isRefinementQuery(safeQuery);
        List<String> detectedCategories = detectCategoryHints(safeQuery);
        List<String> hardCategories = detectedCategories.isEmpty() && refinement
                ? state.lastRecommendedCategories().stream().limit(1).toList()
                : detectedCategories;

        List<String> excludeProductIds = new ArrayList<>();
        if (wantsDifferentItems(safeQuery)) {
            excludeProductIds.addAll(state.lastRecommendedProductIds());
        } else if (isCheaperQuery(safeQuery) && !state.lastRecommendedProductIds().isEmpty()) {
            excludeProductIds.add(state.lastRecommendedProductIds().get(0));
        }

        Double memoryPriceMax = null;
        if (isCheaperQuery(safeQuery) && state.lastAveragePrice() != null) {
            memoryPriceMax = Math.max(199.0, Math.round(state.lastAveragePrice() * 0.80));
        }

        List<String> recentQueries = new ArrayList<>(state.recentQueries());
        List<String> lastIds = new ArrayList<>(state.lastRecommendedProductIds());
        List<String> lastCategories = new ArrayList<>(state.lastRecommendedCategories());

        if (hardCategories.isEmpty() && expectedNumItems > 0 && !lastCategories.isEmpty() && refinement) {
            hardCategories = lastCategories.stream().limit(1).toList();
        }

        return new MemoryHints(hardCategories, excludeProductIds, memoryPriceMax, recentQueries, lastIds, lastCategories);
    }

    public void recordTurn(String sessionId, String query, List<Product> products) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        String safeSessionId = sessionId.trim();
        SessionState state = sessions.computeIfAbsent(safeSessionId, key -> new SessionState());
        synchronized (state) {
            state.appendQuery(query);
            state.updateRecommendation(products);
        }
    }

    private boolean isRefinementQuery(String query) {
        String raw = query == null ? "" : query.toLowerCase();
        return containsAny(raw, "便宜", "贵", "预算", "白色", "黑色", "再来", "换一批", "其他", "别的", "不要", "不喜欢", "上一批", "刚才");
    }

    private boolean isCheaperQuery(String query) {
        String raw = query == null ? "" : query.toLowerCase();
        return containsAny(raw, "便宜", "更便宜", "有点贵", "太贵", "预算", "省钱");
    }

    private boolean wantsDifferentItems(String query) {
        String raw = query == null ? "" : query.toLowerCase();
        return containsAny(raw, "换一批", "再来", "其他", "别的", "不要这些", "不喜欢", "换几个");
    }

    private List<String> detectCategoryHints(String query) {
        String raw = query == null ? "" : query.toLowerCase();
        List<String> categories = new ArrayList<>();
        if (containsAny(raw, "耳机", "headphone", "earbuds")) {
            categories.add("耳机");
        }
        if (containsAny(raw, "手机", "phone", "smartphone")) {
            categories.add("手机");
        }
        if (containsAny(raw, "平板", "tablet", "ipad")) {
            categories.add("平板");
        }
        if (containsAny(raw, "笔记本", "laptop", "notebook")) {
            categories.add("笔记本");
        }
        if (containsAny(raw, "显示器", "monitor")) {
            categories.add("显示器");
        }
        if (containsAny(raw, "相机", "camera", "微单")) {
            categories.add("相机");
        }
        if (containsAny(raw, "手表", "穿戴", "watch")) {
            categories.add("穿戴");
        }
        if (containsAny(raw, "充电器", "charger")) {
            categories.add("充电器");
        }
        return categories;
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

    public record MemoryHints(
            List<String> hardCategoryIntents,
            List<String> excludeProductIds,
            Double memoryPriceMax,
            List<String> recentQueries,
            List<String> lastRecommendedProductIds,
            List<String> lastRecommendedCategories) {

        public static MemoryHints empty() {
            return new MemoryHints(List.of(), List.of(), null, List.of(), List.of(), List.of());
        }
    }

    private static class SessionState {
        private final Deque<String> recentQueries = new ArrayDeque<>();
        private List<String> lastRecommendedProductIds = new ArrayList<>();
        private List<String> lastRecommendedCategories = new ArrayList<>();
        private Double lastAveragePrice;

        private SessionState() {
        }

        private void appendQuery(String query) {
            String safe = query == null ? "" : query.trim();
            if (!safe.isBlank()) {
                recentQueries.addLast(safe);
                while (recentQueries.size() > MAX_QUERY_HISTORY) {
                    recentQueries.removeFirst();
                }
            }
        }

        private void updateRecommendation(List<Product> products) {
            if (products == null || products.isEmpty()) {
                return;
            }
            List<String> ids = new ArrayList<>();
            Set<String> categories = new LinkedHashSet<>();
            double sum = 0.0;
            int count = 0;
            for (Product product : products) {
                if (product == null) {
                    continue;
                }
                if (product.getProductId() != null && !product.getProductId().isBlank()) {
                    ids.add(product.getProductId());
                }
                if (product.getCategory() != null && !product.getCategory().isBlank()) {
                    categories.add(product.getCategory());
                }
                if (product.getPrice() > 0) {
                    sum += product.getPrice();
                    count++;
                }
            }
            if (!ids.isEmpty()) {
                lastRecommendedProductIds = ids;
            }
            if (!categories.isEmpty()) {
                lastRecommendedCategories = new ArrayList<>(categories);
            }
            if (count > 0) {
                lastAveragePrice = sum / count;
            }
        }

        private List<String> recentQueries() {
            return new ArrayList<>(recentQueries);
        }

        private List<String> lastRecommendedProductIds() {
            return new ArrayList<>(lastRecommendedProductIds);
        }

        private List<String> lastRecommendedCategories() {
            return new ArrayList<>(lastRecommendedCategories);
        }

        private Double lastAveragePrice() {
            return lastAveragePrice;
        }
    }
}
