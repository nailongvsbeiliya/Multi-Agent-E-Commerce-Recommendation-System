package com.ecommerce.service;

import com.ecommerce.model.GuessYouLikeResponse;
import com.ecommerce.model.Product;
import com.ecommerce.model.UserProfile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GuessYouLikeService {

    private final ProductCatalogService productCatalogService;
    private final UserFeatureStoreService userFeatureStoreService;
    private final ChatSessionMemoryService chatSessionMemoryService;

    public GuessYouLikeService(ProductCatalogService productCatalogService,
                               UserFeatureStoreService userFeatureStoreService,
                               ChatSessionMemoryService chatSessionMemoryService) {
        this.productCatalogService = productCatalogService;
        this.userFeatureStoreService = userFeatureStoreService;
        this.chatSessionMemoryService = chatSessionMemoryService;
    }

    public GuessYouLikeResponse guessYouLike(String userId, String sessionId, Integer numItems) {
        String safeUserId = safeUserId(userId);
        String safeSessionId = sessionId == null || sessionId.isBlank() ? null : sessionId.trim();
        int limit = safeLimit(numItems);

        Map<String, Object> context = new HashMap<>();
        context.put("scene", "guess_you_like");

        Map<String, Object> features = userFeatureStoreService.getRealtimeFeatures(safeUserId, context);
        Map<String, Double> rfm = userFeatureStoreService.computeRfm(features);
        List<String> segments = userFeatureStoreService.segment(features, rfm);
        UserProfile profile = userFeatureStoreService.buildProfile(safeUserId, features, rfm, segments);

        List<Product> recall = new ArrayList<>(productCatalogService.recallProducts(profile, 80, "default"));
        ChatSessionMemoryService.MemoryHints hints = safeSessionId == null
                ? ChatSessionMemoryService.MemoryHints.empty()
                : chatSessionMemoryService.buildHints(safeSessionId, "", limit);

        Set<String> recentIds = new HashSet<>(hints.lastRecommendedProductIds());
        Set<String> recentCategories = new HashSet<>(hints.lastRecommendedCategories());
        Set<String> preferredCategories = profile.getPreferredCategories() == null
                ? Set.of()
                : new HashSet<>(profile.getPreferredCategories());

        List<GuessYouLikeResponse.GuessItem> ranked = recall.stream()
                .map(product -> toGuessItem(product, preferredCategories, recentCategories, recentIds))
                .sorted(Comparator
                        .comparingDouble(GuessYouLikeResponse.GuessItem::getScore)
                        .reversed()
                        .thenComparing(GuessYouLikeResponse.GuessItem::getProductId))
                .limit(limit)
                .collect(Collectors.toList());

        String strategy = recentCategories.isEmpty()
                ? "profile+catalog"
                : "profile+session-memory";

        return GuessYouLikeResponse.builder()
                .userId(safeUserId)
                .sessionId(safeSessionId)
                .strategy(strategy)
                .items(ranked)
                .build();
    }

    private GuessYouLikeResponse.GuessItem toGuessItem(Product product,
                                                       Set<String> preferredCategories,
                                                       Set<String> recentCategories,
                                                       Set<String> recentIds) {
        double score = product.getScore();
        String category = product.getCategory() == null ? "" : product.getCategory();
        if (recentCategories.contains(category)) {
            score += 0.55;
        }
        if (preferredCategories.contains(category)) {
            score += 0.25;
        }
        if (recentIds.contains(product.getProductId())) {
            score -= 0.30;
        }
        if (product.getStock() > 300) {
            score += 0.08;
        }

        return GuessYouLikeResponse.GuessItem.builder()
                .productId(product.getProductId())
                .name(product.getName())
                .category(product.getCategory())
                .brand(product.getBrand())
                .price(product.getPrice())
                .stock(product.getStock())
                .score(round3(score))
                .reason(buildReason(product, preferredCategories, recentCategories))
                .build();
    }

    private String buildReason(Product product,
                               Set<String> preferredCategories,
                               Set<String> recentCategories) {
        if (recentCategories.contains(product.getCategory())) {
            return "基于你刚刚的对话偏好延展推荐";
        }
        if (preferredCategories.contains(product.getCategory())) {
            return "基于你的长期兴趣偏好推荐";
        }
        if (product.getSalesHeat() >= 0.78) {
            return "当前热度较高，口碑和关注度都不错";
        }
        return "综合销量、库存和价格区间筛选";
    }

    private String safeUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return "u001";
        }
        return userId.trim();
    }

    private int safeLimit(Integer numItems) {
        if (numItems == null || numItems <= 0) {
            return 6;
        }
        return Math.max(3, Math.min(numItems, 12));
    }

    private double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
