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

@Component
public class ProductRecAgent extends BaseAgent {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProductCatalogService productCatalogService;

    public ProductRecAgent(ChatClient.Builder chatClientBuilder, ProductCatalogService productCatalogService) {
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
        List<String> negativeQueryTokens = castStringList(params.get("negativeQueryTokens"));
        List<String> hardCategoryIntents = castStringList(params.get("hardCategoryIntents"));
        List<String> excludeProductIds = castStringList(params.get("excludeProductIds"));
        Double memoryPriceMax = castDouble(params.get("memoryPriceMax"));
        List<String> allQueryTokens = buildQueryTokensV2(userQuery, queryTokens);
        List<Product> candidates = "rerank".equals(mode)
                ? castProducts(params.get("candidates"))
                : productCatalogService.recallProducts(profile, Math.max(1, numItems * 2), strategy);
        candidates = filterExcludedProducts(candidates, excludeProductIds);
        candidates = applyMemoryPriceCap(candidates, memoryPriceMax, numItems);
        candidates = applyQueryPreference(
                candidates, userQuery, queryTokens, negativeQueryTokens, hardCategoryIntents, excludeProductIds, memoryPriceMax, numItems);
        List<Product> rerankCandidates = applyIntentLock(
                candidates, userQuery, queryTokens, hardCategoryIntents, memoryPriceMax, numItems);

        RerankResult rerankResult = rerank(profile, rerankCandidates, numItems, strategy, userQuery, queryTokens);
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
        data.put("negative_query_tokens", negativeQueryTokens);
        data.put("rerank_candidate_count", rerankCandidates.size());
        data.put("query_match_scores", buildQueryMatchScores(finalProducts, allQueryTokens));
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

    private Double castDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            String raw = String.valueOf(value).trim();
            if (raw.isEmpty()) {
                return null;
            }
            return Double.parseDouble(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<Product> applyQueryPreference(List<Product> candidates,
                                               String userQuery,
                                               List<String> queryTokens,
                                               List<String> negativeQueryTokens,
                                               List<String> hardCategoryIntents,
                                               List<String> excludeProductIds,
                                               Double memoryPriceMax,
                                               int numItems) {
        List<String> tokens = buildQueryTokensV2(userQuery, queryTokens);
        List<String> negativeTokens = buildQueryTokensV2("", negativeQueryTokens);
        Set<String> categoryIntents = mergeCategoryIntents(detectCategoryIntents(userQuery, tokens), hardCategoryIntents);

        List<Product> filteredCandidates = filterByNegativeTokens(candidates, negativeTokens);
        filteredCandidates = filterExcludedProducts(filteredCandidates, excludeProductIds);
        filteredCandidates = applyCategoryIntentFilter(filteredCandidates, categoryIntents, numItems);
        filteredCandidates = applyMemoryPriceCap(filteredCandidates, memoryPriceMax, numItems);
        if (tokens.isEmpty()) {
            return filteredCandidates;
        }

        Map<String, Product> byId = new HashMap<>();
        for (Product candidate : filteredCandidates) {
            byId.put(candidate.getProductId(), candidate);
        }
        for (Product product : productCatalogService.getAllProducts()) {
            if (!matchesAnyToken(product, negativeTokens)
                    && !isExcludedProduct(product, excludeProductIds)
                    && (categoryIntents.isEmpty() || matchesCategoryIntent(product, categoryIntents))) {
                byId.putIfAbsent(product.getProductId(), product);
            }
        }

        Map<String, Integer> originalRank = new HashMap<>();
        for (int i = 0; i < filteredCandidates.size(); i++) {
            originalRank.put(filteredCandidates.get(i).getProductId(), i);
        }

        List<Product> ranked = new ArrayList<>(byId.values());
        ranked.sort(Comparator
                .comparingInt((Product product) -> queryMatchScore(product, tokens)).reversed()
                .thenComparing(Comparator.comparingDouble((Product product) -> intentFitScore(product, tokens)).reversed())
                .thenComparingInt(product -> originalRank.getOrDefault(product.getProductId(), Integer.MAX_VALUE))
                .thenComparing(Product::getProductId));

        ranked = applyBudgetOrPremiumGuard(ranked, tokens, numItems);
        ranked = applyMemoryPriceCap(ranked, memoryPriceMax, numItems);

        int maxScore = ranked.stream()
                .mapToInt(product -> queryMatchScore(product, tokens))
                .max()
                .orElse(0);
        if (maxScore <= 0) {
            return filteredCandidates;
        }

        int targetSize = Math.max(filteredCandidates.size(), Math.max(numItems * 2, numItems));
        return ranked.stream().limit(targetSize).collect(Collectors.toList());
    }

    private List<Product> applyIntentLock(List<Product> candidates,
                                          String userQuery,
                                          List<String> queryTokens,
                                          List<String> hardCategoryIntents,
                                          Double memoryPriceMax,
                                          int numItems) {
        List<String> tokens = buildQueryTokensV2(userQuery, queryTokens);
        Set<String> categoryIntents = mergeCategoryIntents(detectCategoryIntents(userQuery, tokens), hardCategoryIntents);
        List<Product> scoped = applyCategoryIntentFilter(candidates, categoryIntents, numItems);
        scoped = applyMemoryPriceCap(scoped, memoryPriceMax, numItems);
        if (tokens.isEmpty()) {
            return scoped;
        }

        List<Product> matched = scoped.stream()
                .filter(product -> queryMatchScore(product, tokens) > 0)
                .collect(Collectors.toList());

        // If query already matches enough items, lock rerank to query-matched set.
        if (matched.size() >= Math.min(numItems, 2)) {
            return matched;
        }
        return scoped;
    }

    private Set<String> mergeCategoryIntents(Set<String> detected, List<String> hardCategoryIntents) {
        Set<String> merged = new LinkedHashSet<>();
        if (detected != null) {
            merged.addAll(detected);
        }
        if (hardCategoryIntents != null) {
            for (String intent : hardCategoryIntents) {
                if (intent != null && !intent.isBlank()) {
                    merged.add(intent.trim());
                }
            }
        }
        return merged;
    }

    private List<String> buildQueryTokens(String userQuery, List<String> queryTokens) {
        return buildQueryTokensV2(userQuery, queryTokens);
    }

    private List<String> buildQueryTokensBroken(String userQuery, List<String> queryTokens) {
        /*
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
        */
        return List.of();
    }

    private List<String> buildQueryTokensV2(String userQuery, List<String> queryTokens) {
        Set<String> tokens = new LinkedHashSet<>();
        if (queryTokens != null) {
            for (String token : queryTokens) {
                if (token != null && token.length() > 1) {
                    tokens.add(token.toLowerCase(Locale.ROOT));
                }
            }
        }

        String normalized = userQuery == null ? "" : userQuery.toLowerCase(Locale.ROOT);
        for (String part : normalized.split("[\\s,.;!?]+")) {
            String token = part.trim();
            if (token.length() > 1) {
                tokens.add(token);
            }
        }
        return new ArrayList<>(tokens);
    }

    private List<Product> filterByNegativeTokens(List<Product> products, List<String> negativeTokens) {
        if (negativeTokens == null || negativeTokens.isEmpty()) {
            return products;
        }
        List<Product> filtered = products.stream()
                .filter(product -> !matchesAnyToken(product, negativeTokens))
                .collect(Collectors.toList());
        return filtered.isEmpty() ? products : filtered;
    }

    private List<Product> filterExcludedProducts(List<Product> products, List<String> excludeProductIds) {
        if (products == null || products.isEmpty() || excludeProductIds == null || excludeProductIds.isEmpty()) {
            return products;
        }
        Set<String> excludeSet = excludeProductIds.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(Collectors.toSet());
        if (excludeSet.isEmpty()) {
            return products;
        }
        List<Product> filtered = products.stream()
                .filter(product -> !excludeSet.contains(product.getProductId()))
                .collect(Collectors.toList());
        return filtered.isEmpty() ? products : filtered;
    }

    private boolean isExcludedProduct(Product product, List<String> excludeProductIds) {
        if (product == null || excludeProductIds == null || excludeProductIds.isEmpty()) {
            return false;
        }
        for (String excludedId : excludeProductIds) {
            if (excludedId != null && excludedId.trim().equals(product.getProductId())) {
                return true;
            }
        }
        return false;
    }

    private List<Product> applyMemoryPriceCap(List<Product> products, Double memoryPriceMax, int numItems) {
        if (products == null || products.isEmpty() || memoryPriceMax == null || memoryPriceMax <= 0) {
            return products;
        }
        List<Product> capped = products.stream()
                .filter(product -> product.getPrice() <= memoryPriceMax)
                .collect(Collectors.toList());
        if (capped.size() >= Math.min(numItems, 2)) {
            return capped;
        }
        return products;
    }

    private boolean matchesAnyToken(Product product, List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return false;
        }
        String text = searchableText(product);
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Integer> buildQueryMatchScores(List<Product> products, List<String> tokens) {
        Map<String, Integer> scores = new HashMap<>();
        for (Product product : products) {
            scores.put(product.getProductId(), queryMatchScore(product, tokens));
        }
        return scores;
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

    private double intentFitScore(Product product, List<String> tokens) {
        double score = 0.0;
        if (tokens == null || tokens.isEmpty()) {
            return score;
        }
        boolean budgetIntent = containsIntent(tokens, "入门", "性价比", "便宜", "平价", "学生", "预算", "省钱");
        boolean premiumIntent = containsIntent(tokens, "高端", "旗舰", "顶级", "专业", "ultra", "pro max");

        if (budgetIntent) {
            if (product.getPrice() <= 1000) {
                score += 1.0;
            } else if (product.getPrice() <= 3000) {
                score += 0.8;
            } else if (product.getPrice() >= 7000) {
                score -= 1.1;
            }
            if (hasTag(product, "性价比") || hasTag(product, "入门")) {
                score += 0.8;
            }
        }

        if (premiumIntent) {
            if (product.getPrice() >= 7000) {
                score += 1.0;
            } else if (product.getPrice() <= 2000) {
                score -= 0.8;
            }
            if (hasTag(product, "旗舰") || hasTag(product, "高端")) {
                score += 0.9;
            }
        }

        return score;
    }

    private boolean containsIntent(List<String> tokens, String... intentWords) {
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            for (String intentWord : intentWords) {
                if (token.contains(intentWord)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasTag(Product product, String tag) {
        return product.getTags() != null && product.getTags().contains(tag);
    }

    private List<Product> applyBudgetOrPremiumGuard(List<Product> ranked, List<String> tokens, int numItems) {
        if (ranked == null || ranked.isEmpty() || tokens == null || tokens.isEmpty()) {
            return ranked;
        }
        boolean budgetIntent = containsIntent(tokens, "入门", "性价比", "便宜", "平价", "学生", "预算", "省钱");
        boolean premiumIntent = containsIntent(tokens, "高端", "旗舰", "顶级", "专业", "ultra", "pro max");

        if (budgetIntent && !premiumIntent) {
            List<Product> budgetPreferred = ranked.stream()
                    .filter(product -> product.getPrice() <= 4500 || hasTag(product, "性价比") || hasTag(product, "入门"))
                    .collect(Collectors.toList());
            if (budgetPreferred.size() >= Math.min(numItems, 2)) {
                return budgetPreferred;
            }
        }

        if (premiumIntent && !budgetIntent) {
            List<Product> premiumPreferred = ranked.stream()
                    .filter(product -> product.getPrice() >= 5000 || hasTag(product, "旗舰") || hasTag(product, "高端"))
                    .collect(Collectors.toList());
            if (premiumPreferred.size() >= Math.min(numItems, 2)) {
                return premiumPreferred;
            }
        }
        return ranked;
    }

    private Set<String> detectCategoryIntents(String userQuery, List<String> tokens) {
        String raw = safeLower(userQuery);
        Set<String> explicitIntents = new LinkedHashSet<>();
        if (rawContainsAny(raw, "耳机")) {
            explicitIntents.add("耳机");
        }
        if (rawContainsAny(raw, "手机")) {
            explicitIntents.add("手机");
        }
        if (rawContainsAny(raw, "平板")) {
            explicitIntents.add("平板");
        }
        if (rawContainsAny(raw, "笔记本")) {
            explicitIntents.add("笔记本");
        }
        if (rawContainsAny(raw, "显示器")) {
            explicitIntents.add("显示器");
        }
        if (rawContainsAny(raw, "相机", "微单")) {
            explicitIntents.add("相机");
        }
        if (rawContainsAny(raw, "穿戴", "手表")) {
            explicitIntents.add("穿戴");
        }
        if (rawContainsAny(raw, "充电器", "充电头")) {
            explicitIntents.add("充电器");
        }
        if (rawContainsAny(raw, "路由器")) {
            explicitIntents.add("路由器");
        }
        if (!explicitIntents.isEmpty()) {
            return explicitIntents;
        }

        Set<String> intents = new LinkedHashSet<>();
        Set<String> normalizedTokens = new LinkedHashSet<>();
        for (String token : tokens) {
            normalizedTokens.add(safeLower(token));
        }

        if (containsAnyIntent(raw, normalizedTokens, "耳机", "headphone", "earbud", "earbuds", "airpods", "buds", "freebuds")) {
            intents.add("耳机");
        }
        if (containsAnyIntent(raw, normalizedTokens, "手机", "phone", "smartphone", "mobile", "iphone", "huawei", "galaxy", "oppo", "vivo", "iqoo", "redmi", "oneplus")) {
            intents.add("手机");
        }
        if (containsAnyIntent(raw, normalizedTokens, "平板", "tablet", "ipad", "matepad", "tab")) {
            intents.add("平板");
        }
        if (containsAnyIntent(raw, normalizedTokens, "笔记本", "laptop", "notebook", "macbook", "thinkpad")) {
            intents.add("笔记本");
        }
        if (containsAnyIntent(raw, normalizedTokens, "显示器", "monitor", "display", "screen")) {
            intents.add("显示器");
        }
        if (containsAnyIntent(raw, normalizedTokens, "相机", "camera", "微单", "vlog", "eos", "zv")) {
            intents.add("相机");
        }
        if (containsAnyIntent(raw, normalizedTokens, "穿戴", "手表", "watch", "band")) {
            intents.add("穿戴");
        }
        if (containsAnyIntent(raw, normalizedTokens, "充电器", "charger", "power-adapter", "fast-charge")) {
            intents.add("充电器");
        }
        if (containsAnyIntent(raw, normalizedTokens, "路由器", "router", "wifi")) {
            intents.add("路由器");
        }
        return intents;
    }

    private boolean rawContainsAny(String raw, String... keywords) {
        for (String keyword : keywords) {
            if (raw.contains(safeLower(keyword))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAnyIntent(String raw, Set<String> tokens, String... keywords) {
        for (String keyword : keywords) {
            String normalized = safeLower(keyword);
            if (raw.contains(normalized)) {
                return true;
            }
            for (String token : tokens) {
                if (token.contains(normalized)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<Product> applyCategoryIntentFilter(List<Product> products, Set<String> categoryIntents, int numItems) {
        if (products == null || products.isEmpty() || categoryIntents == null || categoryIntents.isEmpty()) {
            return products;
        }
        List<Product> matched = products.stream()
                .filter(product -> matchesCategoryIntent(product, categoryIntents))
                .collect(Collectors.toList());

        if (matched.size() >= Math.min(numItems, 2)) {
            return matched;
        }
        return products;
    }

    private boolean matchesCategoryIntent(Product product, Set<String> categoryIntents) {
        String category = safeLower(product.getCategory());
        String searchable = searchableText(product);
        for (String intent : categoryIntents) {
            String normalizedIntent = safeLower(intent);
            if (category.contains(normalizedIntent)) {
                return true;
            }
            if ("相机".equals(normalizedIntent) && category.contains("运动相机")) {
                return true;
            }
            if ("充电器".equals(normalizedIntent)
                    && (category.contains("配件") || searchable.contains("充电器") || searchable.contains("charger"))) {
                return true;
            }
            if ("耳机".equals(normalizedIntent)
                    && (category.contains("配件") && (searchable.contains("耳机") || searchable.contains("headphone") || searchable.contains("buds")))) {
                return true;
            }
        }
        return false;
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

    private RerankResult rerank(UserProfile profile,
                                List<Product> candidates,
                                int numItems,
                                String strategy,
                                String userQuery,
                                List<String> queryTokens) {
        if (candidates.isEmpty()) {
            return new RerankResult(List.of(), false);
        }
        if (profile == null || profile.getPriceRange() == null || profile.getPriceRange().length < 2) {
            List<String> ids = candidates.stream().map(Product::getProductId).limit(numItems).collect(Collectors.toList());
            return new RerankResult(ids, false);
        }
        try {
            String queryHints = queryTokens == null || queryTokens.isEmpty() ? "[]" : queryTokens.toString();
            String prompt = String.format(
                    "你是电商重排助手。请优先满足用户当前需求，其次再参考历史偏好。\n"
                            + "用户当前需求: %s\n"
                            + "需求关键词: %s\n"
                            + "用户偏好类目: %s\n"
                            + "价格范围: %.0f-%.0f\n"
                            + "实验策略: %s\n"
                            + "请对候选商品重排，返回最优的%d个商品ID JSON数组。"
                            + "如果当前需求与历史偏好冲突，以当前需求为主。\n"
                            + "候选商品:\n%s",
                    userQuery,
                    queryHints,
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
