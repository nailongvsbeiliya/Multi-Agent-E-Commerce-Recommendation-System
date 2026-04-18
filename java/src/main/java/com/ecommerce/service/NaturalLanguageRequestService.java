package com.ecommerce.service;

import com.ecommerce.config.NaturalLanguageParsingProperties;
import com.ecommerce.model.RecommendationRequest;
import com.ecommerce.model.TextRecommendationRequest;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class NaturalLanguageRequestService {
    /*

    private static final Pattern COUNT_PATTERN = Pattern.compile("(推荐|来|给我|要)\\s*(\\d{1,2})\\s*(个|款|件|台|部)?");
    private static final Pattern MAX_PRICE_PATTERN = Pattern.compile("(\\d{2,6})\\s*(元|块).{0,4}(以内|以下|不超过)");
    private static final Pattern MIN_PRICE_PATTERN = Pattern.compile("(\\d{2,6})\\s*(元|块).{0,4}(以上|起)");

    public RecommendationRequest normalize(TextRecommendationRequest request) {
        String query = request.getQuery() == null ? "" : request.getQuery().trim();
        String scene = normalizeScene(request.getScene(), query);
        int numItems = normalizeNumItems(request.getNumItems(), query);
        String userId = normalizeUserId(request.getUserId(), query);

        Map<String, Object> context = new HashMap<>();
        if (request.getContext() != null) {
            context.putAll(request.getContext());
        }
        context.put("scene", scene);
        context.put("query", query);
        context.put("query_tokens", extractTokens(query));

        Double maxPrice = extractMaxPrice(query);
        if (maxPrice != null) {
            context.put("price_max", maxPrice);
        }
        Double minPrice = extractMinPrice(query);
        if (minPrice != null) {
            context.put("price_min", minPrice);
        }

        return RecommendationRequest.builder()
                .userId(userId)
                .scene(scene)
                .numItems(numItems)
                .userQuery(query)
                .context(context)
                .build();
    }

    private String normalizeUserId(String userId, String query) {
        if (userId != null && !userId.isBlank()) {
            return userId.trim();
        }
        int hash = Math.abs((query == null ? "" : query).hashCode());
        return "guest-" + (hash % 100000);
    }

    private int normalizeNumItems(Integer numItems, String query) {
        if (numItems != null && numItems > 0) {
            return Math.min(numItems, 20);
        }
        Matcher matcher = COUNT_PATTERN.matcher(query);
        if (matcher.find()) {
            int parsed = Integer.parseInt(matcher.group(2));
            return Math.min(Math.max(parsed, 1), 20);
        }
        return 5;
    }

    private String normalizeScene(String scene, String query) {
        if (scene != null && !scene.isBlank()) {
            return scene.trim();
        }
        if (containsAny(query, "活动", "优惠", "折扣", "促销")) {
            return "campaign";
        }
        if (containsAny(query, "参数", "对比", "详情", "评测")) {
            return "detail";
        }
        return "homepage";
    }

    private List<String> extractTokens(String query) {
        Set<String> tokens = new LinkedHashSet<>();
        String normalized = query == null ? "" : query.toLowerCase();

        for (String part : normalized.split("[\\s,，。!！?？;；]+")) {
            String token = part.trim();
            if (!token.isEmpty() && token.length() > 1) {
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
        if (containsAny(normalized, "充电", "充电器", "快充")) {
            tokens.add("charger");
            tokens.add("anker");
        }
        if (containsAny(normalized, "鼠标")) {
            tokens.add("mouse");
            tokens.add("logitech");
            tokens.add("mx");
        }
        if (containsAny(normalized, "显示器", "monitor")) {
            tokens.add("monitor");
            tokens.add("dell");
            tokens.add("u2724");
        }
        if (containsAny(normalized, "笔记本", "游戏本", "laptop")) {
            tokens.add("laptop");
            tokens.add("notebook");
        }
        return new ArrayList<>(tokens);
    }

    private Double extractMaxPrice(String query) {
        Matcher matcher = MAX_PRICE_PATTERN.matcher(query);
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        return null;
    }

    private Double extractMinPrice(String query) {
        Matcher matcher = MIN_PRICE_PATTERN.matcher(query);
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        return null;
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
    */

    private static final Logger log = LoggerFactory.getLogger(NaturalLanguageRequestService.class);
    private static final Pattern SAFE_COUNT_PATTERN = Pattern.compile("(?:\\u63a8\\u8350|\\u6765|\\u7ed9\\u6211|\\u8981)\\s*(\\d{1,2})\\s*(?:\\u4e2a|\\u6b3e|\\u4ef6|\\u53f0|\\u90e8)?");
    private static final Pattern SAFE_MAX_PRICE_PATTERN = Pattern.compile("(\\d{2,6})\\s*(?:\\u5143|\\u5757).{0,4}(?:\\u4ee5\\u5185|\\u4ee5\\u4e0b|\\u4e0d\\u8d85\\u8fc7)");
    private static final Pattern SAFE_MIN_PRICE_PATTERN = Pattern.compile("(\\d{2,6})\\s*(?:\\u5143|\\u5757).{0,4}(?:\\u4ee5\\u4e0a|\\u8d77)");
    private static final Pattern NEGATED_TERM_PATTERN = Pattern.compile("(?:\\u4e0d\\u8981|\\u4e0d\\u60f3\\u8981|\\u522b|\\u6392\\u9664|\\u4e0d\\u8003\\u8651)\\s*([\\p{IsHan}A-Za-z0-9\\-]{1,24})");

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final NaturalLanguageParsingProperties properties;

    public NaturalLanguageRequestService(ChatClient.Builder chatClientBuilder,
                                         ObjectMapper objectMapper,
                                         NaturalLanguageParsingProperties properties) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public RecommendationRequest normalize(TextRecommendationRequest request) {
        TextRecommendationRequest safeRequest = request == null ? new TextRecommendationRequest() : request;
        String query = safeValue(safeRequest.getQuery());

        RuleIntent fallbackIntent = parseByRules(query, safeRequest.getScene(), safeRequest.getNumItems());
        RuleIntent finalIntent = mergeWithLlmIntent(fallbackIntent, query, safeRequest.getScene(), safeRequest.getNumItems());
        String userId = normalizeUserIdSafe(safeRequest.getUserId(), query);

        Map<String, Object> context = new HashMap<>();
        if (safeRequest.getContext() != null) {
            context.putAll(safeRequest.getContext());
        }
        context.put("scene", finalIntent.scene());
        context.put("query", query);
        context.put("query_tokens", finalIntent.includeTerms());
        context.put("negative_query_tokens", finalIntent.excludeTerms());
        context.put("intent_source", finalIntent.source());
        context.put("intent_confidence", finalIntent.confidence());

        if (finalIntent.priceMax() != null) {
            context.put("price_max", finalIntent.priceMax());
        }
        if (finalIntent.priceMin() != null) {
            context.put("price_min", finalIntent.priceMin());
        }

        return RecommendationRequest.builder()
                .userId(userId)
                .scene(finalIntent.scene())
                .numItems(finalIntent.numItems())
                .userQuery(query)
                .context(context)
                .build();
    }

    private RuleIntent mergeWithLlmIntent(RuleIntent fallbackIntent, String query, String sceneHint, Integer numHint) {
        RuleIntent llmIntent = parseByLlm(query, sceneHint, numHint);
        if (llmIntent == null) {
            return fallbackIntent;
        }

        Set<String> include = new LinkedHashSet<>(llmIntent.includeTerms());
        include.addAll(fallbackIntent.includeTerms());

        Set<String> exclude = new LinkedHashSet<>(llmIntent.excludeTerms());
        exclude.addAll(fallbackIntent.excludeTerms());

        include.removeAll(exclude);
        return new RuleIntent(
                safeValue(llmIntent.scene()).isBlank() ? fallbackIntent.scene() : llmIntent.scene(),
                llmIntent.numItems() > 0 ? llmIntent.numItems() : fallbackIntent.numItems(),
                new ArrayList<>(include),
                new ArrayList<>(exclude),
                llmIntent.priceMin() != null ? llmIntent.priceMin() : fallbackIntent.priceMin(),
                llmIntent.priceMax() != null ? llmIntent.priceMax() : fallbackIntent.priceMax(),
                "llm+fallback",
                llmIntent.confidence()
        );
    }

    private RuleIntent parseByLlm(String query, String sceneHint, Integer numHint) {
        if (!properties.isLlmFirst() || query.isBlank()) {
            return null;
        }
        try {
            String response = chatClient.prompt()
                    .system("""
                            You are an NLU parser for e-commerce recommendation.
                            Return strict JSON only, no markdown.
                            JSON schema:
                            {
                              "scene":"homepage|campaign|detail",
                              "num_items":5,
                              "include_terms":["term1","term2"],
                              "exclude_terms":["term3"],
                              "price_min":null,
                              "price_max":null,
                              "confidence":0.0
                            }
                            Detect negation intent carefully (e.g. "不要苹果手机" means include smartphone intent but exclude apple/iphone).
                            If a field is unknown, use null or empty array.
                            """)
                    .user("query=" + query + "\nscene_hint=" + safeValue(sceneHint) + "\nnum_items_hint=" + (numHint == null ? "" : numHint))
                    .call()
                    .content();

            if (response == null || response.isBlank()) {
                return null;
            }
            LlmIntentOutput output = objectMapper.readValue(stripCodeFence(response), LlmIntentOutput.class);
            List<String> include = normalizeTerms(output.includeTerms, true);
            List<String> exclude = normalizeTerms(output.excludeTerms, true);

            Set<String> includeSet = new LinkedHashSet<>(include);
            Set<String> excludeSet = new LinkedHashSet<>(exclude);
            includeSet.removeAll(excludeSet);

            return new RuleIntent(
                    sanitizeScene(output.scene, sceneHint, query),
                    sanitizeNumItems(output.numItems, numHint, query),
                    new ArrayList<>(includeSet),
                    new ArrayList<>(excludeSet),
                    output.priceMin,
                    output.priceMax,
                    "llm",
                    sanitizeConfidence(output.confidence)
            );
        } catch (Exception ex) {
            log.warn("LLM intent parse failed, fallback to rules: {}", ex.getMessage());
            return null;
        }
    }

    private RuleIntent parseByRules(String query, String sceneHint, Integer numHint) {
        String normalized = query.toLowerCase(Locale.ROOT);
        String scene = sanitizeScene(sceneHint, sceneHint, normalized);
        int numItems = sanitizeNumItems(numHint, numHint, query);
        Double priceMax = extractPriceSafe(SAFE_MAX_PRICE_PATTERN, query);
        Double priceMin = extractPriceSafe(SAFE_MIN_PRICE_PATTERN, query);

        Set<String> include = new LinkedHashSet<>(extractSplitTokens(normalized));
        Set<String> exclude = new LinkedHashSet<>(extractNegatedTerms(normalized));
        applyDictionary(normalized, include, exclude);
        include.removeAll(exclude);

        return new RuleIntent(
                scene,
                numItems,
                new ArrayList<>(include),
                new ArrayList<>(exclude),
                priceMin,
                priceMax,
                "fallback_rule",
                0.55
        );
    }

    private void applyDictionary(String query, Set<String> include, Set<String> exclude) {
        if (!properties.isFallbackEnabled()) {
            return;
        }
        for (Map.Entry<String, List<String>> entry : properties.getSynonymDictionary().entrySet()) {
            String keyword = safeValue(entry.getKey()).toLowerCase(Locale.ROOT);
            if (keyword.isBlank() || !query.contains(keyword)) {
                continue;
            }
            Set<String> expanded = new LinkedHashSet<>();
            expanded.add(keyword);
            expanded.addAll(normalizeTerms(entry.getValue(), false));
            if (isNegatedKeyword(query, keyword)) {
                exclude.addAll(expanded);
            } else {
                include.addAll(expanded);
            }
        }
    }

    private List<String> extractSplitTokens(String query) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : query.split("[\\s,.;!?，。！？、]+")) {
            String normalized = token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
            if (normalized.length() <= 1 || properties.getStopWords().contains(normalized)) {
                continue;
            }
            tokens.add(normalized);
        }
        return new ArrayList<>(tokens);
    }

    private List<String> extractNegatedTerms(String query) {
        Set<String> terms = new LinkedHashSet<>();
        Matcher matcher = NEGATED_TERM_PATTERN.matcher(query);
        while (matcher.find()) {
            String fragment = safeValue(matcher.group(1));
            if (fragment.isBlank()) {
                continue;
            }
            for (String part : fragment.split("[\\s\\u7684]+")) {
                String token = part.trim().toLowerCase(Locale.ROOT);
                if (token.length() > 1 && !properties.getStopWords().contains(token)) {
                    terms.add(token);
                }
            }
        }
        return new ArrayList<>(terms);
    }

    private boolean isNegatedKeyword(String query, String keyword) {
        Pattern pattern = Pattern.compile("(?:\\u4e0d\\u8981|\\u4e0d\\u60f3\\u8981|\\u522b|\\u6392\\u9664|\\u4e0d\\u8003\\u8651)\\s*" + Pattern.quote(keyword));
        return pattern.matcher(query).find();
    }

    private String sanitizeScene(String sceneCandidate, String sceneHint, String query) {
        if (!safeValue(sceneCandidate).isBlank()) {
            return sceneCandidate.trim();
        }
        if (!safeValue(sceneHint).isBlank()) {
            return sceneHint.trim();
        }
        if (containsAnySafe(query, properties.getSceneCampaignKeywords())) {
            return "campaign";
        }
        if (containsAnySafe(query, properties.getSceneDetailKeywords())) {
            return "detail";
        }
        return properties.getDefaultScene();
    }

    private int sanitizeNumItems(Integer numCandidate, Integer numHint, String query) {
        Integer finalValue = numCandidate;
        if (finalValue == null || finalValue <= 0) {
            finalValue = numHint;
        }
        if (finalValue == null || finalValue <= 0) {
            Matcher matcher = SAFE_COUNT_PATTERN.matcher(safeValue(query));
            if (matcher.find()) {
                finalValue = Integer.parseInt(matcher.group(1));
            }
        }
        if (finalValue == null || finalValue <= 0) {
            finalValue = properties.getDefaultNumItems();
        }
        return Math.min(Math.max(finalValue, 1), properties.getMaxNumItems());
    }

    private String normalizeUserIdSafe(String userId, String query) {
        if (userId != null && !userId.isBlank()) {
            return userId.trim();
        }
        int hash = Math.abs(safeValue(query).hashCode());
        return "guest-" + (hash % 100000);
    }

    private Double extractPriceSafe(Pattern pattern, String query) {
        Matcher matcher = pattern.matcher(safeValue(query));
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        return null;
    }

    private List<String> normalizeTerms(List<String> raw, boolean skipStopWords) {
        if (raw == null) {
            return List.of();
        }
        return raw.stream()
                .map(this::safeValue)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .map(String::trim)
                .filter(value -> value.length() > 1)
                .filter(value -> !skipStopWords || !properties.getStopWords().contains(value))
                .distinct()
                .collect(Collectors.toList());
    }

    private boolean containsAnySafe(String text, List<String> keywords) {
        if (text == null || text.isBlank() || keywords == null || keywords.isEmpty()) {
            return false;
        }
        for (String keyword : keywords) {
            String candidate = safeValue(keyword).toLowerCase(Locale.ROOT);
            if (!candidate.isBlank() && text.toLowerCase(Locale.ROOT).contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private double sanitizeConfidence(Double confidence) {
        if (confidence == null) {
            return 0.7;
        }
        return Math.max(0.0, Math.min(1.0, confidence));
    }

    private String stripCodeFence(String raw) {
        String cleaned = safeValue(raw).trim();
        if (!cleaned.startsWith("```")) {
            return cleaned;
        }
        int firstLineBreak = cleaned.indexOf('\n');
        if (firstLineBreak > 0) {
            cleaned = cleaned.substring(firstLineBreak + 1);
        }
        int fenceEnd = cleaned.lastIndexOf("```");
        if (fenceEnd >= 0) {
            cleaned = cleaned.substring(0, fenceEnd);
        }
        return cleaned.trim();
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }

    private record RuleIntent(String scene,
                              int numItems,
                              List<String> includeTerms,
                              List<String> excludeTerms,
                              Double priceMin,
                              Double priceMax,
                              String source,
                              double confidence) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class LlmIntentOutput {
        public String scene;
        @JsonProperty("num_items")
        public Integer numItems;
        @JsonProperty("include_terms")
        public List<String> includeTerms = List.of();
        @JsonProperty("exclude_terms")
        public List<String> excludeTerms = List.of();
        @JsonProperty("price_min")
        public Double priceMin;
        @JsonProperty("price_max")
        public Double priceMax;
        public Double confidence;
    }
}
