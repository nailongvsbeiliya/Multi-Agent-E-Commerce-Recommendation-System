package com.ecommerce.agent;

import com.ecommerce.model.AgentResult;
import com.ecommerce.model.Product;
import com.ecommerce.model.UserProfile;
import com.ecommerce.service.ProductCatalogService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;
import java.util.stream.Collectors;

class ProductRecAgentLegacyBackup extends BaseAgent {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProductCatalogService productCatalogService;

    ProductRecAgentLegacyBackup(ChatClient.Builder chatClientBuilder, ProductCatalogService productCatalogService) {
        super("product_rec", 8.0, 2);
        this.chatClient = chatClientBuilder.build();
        this.productCatalogService = productCatalogService;
    }

    @Override
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        UserProfile profile = (UserProfile) params.get("userProfile");
        int numItems = (int) params.getOrDefault("numItems", 10);
        String mode = String.valueOf(params.getOrDefault("mode", "full"));
        String strategy = String.valueOf(params.getOrDefault("strategy", "llm_rerank"));
        String userQuery = String.valueOf(params.getOrDefault("userQuery", "")).trim();
        List<String> queryTokens = castStringList(params.get("queryTokens"));
        List<Product> candidates = "rerank".equals(mode)
                ? castProducts(params.get("candidates"))
                : productCatalogService.recallProducts(profile, Math.max(1, numItems * 2), strategy);
        candidates = applyQueryPreference(candidates, userQuery, queryTokens, numItems);

        RerankResult rerankResult = rerank(profile, candidates, numItems, strategy);
        List<String> rankedIds = rerankResult.ids();
        Map<String, Product> idMap = candidates.stream()
                .collect(Collectors.toMap(Product::getProductId, p -> p, (left, right) -> left));

        List<Product> finalProducts = rankedIds.stream()
                .filter(idMap::containsKey)
                .map(idMap::get)
                .limit(numItems)
                .collect(Collectors.toList());

        if (finalProducts.size() < numItems) {
            candidates.stream()
                    .filter(p -> finalProducts.stream().noneMatch(selected -> selected.getProductId().equals(p.getProductId())))
                    .limit(numItems - finalProducts.size())
                    .forEach(finalProducts::add);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("mode", mode);
        data.put("products", finalProducts);
        data.put("candidates", candidates);
        data.put("query", userQuery);
        data.put("query_tokens", queryTokens);
        data.put("ranked_ids", rankedIds);
        data.put("llm_used", rerankResult.llmUsed());
        data.put("recall_strategy", "explore_diversity".equals(strategy)
                ? "collaborative_filter+vector+hot+new_arrival"
                : "collaborative_filter+vector+hot");
        data.put("candidate_count", candidates.size());

        return AgentResult.builder()
                .agentName(name)
                .success(true)
                .data(data)
                .confidence(0.82)
                .build();
    }

    private List<Product> castProducts(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(Product.class::isInstance)
                    .map(Product.class::cast)
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private List<String> castStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private List<Product> applyQueryPreference(List<Product> candidates,
                                               String userQuery,
                                               List<String> queryTokens,
                                               int numItems) {
        List<String> tokens = buildQueryTokens(userQuery, queryTokens);
        if (tokens.isEmpty()) {
            return candidates;
        }

        Map<String, Product> byId = new HashMap<>();
        for (Product candidate : candidates) {
            byId.put(candidate.getProductId(), candidate);
        }
        for (Product product : productCatalogService.getAllProducts()) {
            byId.putIfAbsent(product.getProductId(), product);
        }

        Map<String, Integer> originalRank = new HashMap<>();
        for (int i = 0; i < candidates.size(); i++) {
            originalRank.put(candidates.get(i).getProductId(), i);
        }

        List<Product> ranked = new ArrayList<>(byId.values());
        ranked.sort(Comparator
                .comparingInt((Product product) -> queryMatchScore(product, tokens)).reversed()
                .thenComparingInt(product -> originalRank.getOrDefault(product.getProductId(), Integer.MAX_VALUE))
                .thenComparing(Product::getProductId));

        int maxScore = ranked.stream()
                .mapToInt(product -> queryMatchScore(product, tokens))
                .max()
                .orElse(0);
        if (maxScore <= 0) {
            return candidates;
        }

        int targetSize = Math.max(candidates.size(), Math.max(numItems * 2, numItems));
        return ranked.stream().limit(targetSize).collect(Collectors.toList());
    }

    private List<String> buildQueryTokens(String userQuery, List<String> queryTokens) {
        Set<String> tokens = new LinkedHashSet<>();
        if (queryTokens != null) {
            for (String token : queryTokens) {
                if (token != null && token.length() > 1) {
                    tokens.add(token.toLowerCase(Locale.ROOT));
                }
            }
        }

        String normalized = userQuery == null ? "" : userQuery.toLowerCase(Locale.ROOT);
        for (String part : normalized.split("[\\s,，。!！?？;；]+")) {
            String token = part.trim();
            if (token.length() > 1) {
                tokens.add(token);
            }
        }

        if (containsAny(normalized, "耳机", "降噪", "蓝牙")) {
            tokens.add("airpods");
            tokens.add("sony");
            tokens.add("headphone");
            tokens.add("pods");
        }
        if (containsAny(normalized, "手机", "iphone", "苹果", "华为", "mate")) {
            tokens.add("iphone");
            tokens.add("apple");
            tokens.add("huawei");
            tokens.add("mate");
        }
        if (containsAny(normalized, "平板", "ipad", "tablet")) {
            tokens.add("ipad");
            tokens.add("tablet");
        }
        if (containsAny(normalized, "充电", "快充", "充电器")) {
            tokens.add("anker");
            tokens.add("charger");
            tokens.add("140w");
        }
        if (containsAny(normalized, "鼠标")) {
            tokens.add("logitech");
            tokens.add("mouse");
            tokens.add("mx");
        }
        if (containsAny(normalized, "显示器")) {
            tokens.add("dell");
            tokens.add("u2724");
            tokens.add("monitor");
        }
        if (containsAny(normalized, "笔记本", "游戏本", "laptop")) {
            tokens.add("laptop");
            tokens.add("notebook");
        }
        return new ArrayList<>(tokens);
    }

    private int queryMatchScore(Product product, List<String> tokens) {
        String text = searchableText(product);
        int score = 0;
        for (String token : tokens) {
            if (text.contains(token)) {
                score += 1;
            }
        }
        return score;
    }

    private String searchableText(Product product) {
        String tags = product.getTags() == null ? "" : String.join(" ", product.getTags());
        return (safeLower(product.getName())
                + " " + safeLower(product.getBrand())
                + " " + safeLower(product.getCategory())
                + " " + safeLower(product.getDescription())
                + " " + safeLower(tags))
                .trim();
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
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

    private RerankResult rerank(UserProfile profile, List<Product> candidates, int numItems, String strategy) {
        if (candidates.isEmpty()) {
            return new RerankResult(List.of(), false);
        }
        if (profile == null || profile.getPriceRange() == null || profile.getPriceRange().length < 2) {
            List<String> ids = candidates.stream().map(Product::getProductId).limit(numItems).collect(Collectors.toList());
            return new RerankResult(ids, false);
        }
        try {
            String prompt = String.format(
                    "请根据用户偏好类目%s、价格范围%.0f-%.0f、实验策略%s，对候选商品进行重排，并返回最优的%d个商品ID的JSON数组。\n候选商品:\n%s",
                    profile.getPreferredCategories(),
                    profile.getPriceRange()[0],
                    profile.getPriceRange()[1],
                    strategy,
                    numItems,
                    candidates.stream()
                            .map(p -> p.getProductId() + ":" + p.getName() + "(" + p.getCategory() + ",￥" + p.getPrice() + "," + p.getTags() + ")")
                            .collect(Collectors.joining("\n"))
            );
            String response = chatClient.prompt().user(prompt).call().content();
            String cleaned = stripCodeFence(response);
            List<String> ids = objectMapper.readValue(cleaned, new TypeReference<>() {});
            return new RerankResult(ids, true);
        } catch (Exception e) {
            log.warn("LLM rerank failed, using catalog order: {}", e.getMessage());
            List<String> ids = candidates.stream().map(Product::getProductId).limit(numItems).collect(Collectors.toList());
            return new RerankResult(ids, false);
        }
    }

    private String stripCodeFence(String raw) {
        String cleaned = raw == null ? "" : raw.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(cleaned.indexOf('\n') + 1);
            cleaned = cleaned.substring(0, cleaned.lastIndexOf("```"));
        }
        return cleaned;
    }

    private record RerankResult(List<String> ids, boolean llmUsed) {
    }
}
