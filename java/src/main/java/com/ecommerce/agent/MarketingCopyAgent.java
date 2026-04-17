package com.ecommerce.agent;

import com.ecommerce.model.AgentResult;
import com.ecommerce.model.Product;
import com.ecommerce.model.UserProfile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class MarketingCopyAgent extends BaseAgent {

    private static final Map<String, String> TEMPLATES = Map.of(
            "new_user", "为新用户撰写欢迎推荐文案，风格热情友好，突出新客福利。",
            "high_value", "为VIP用户撰写推荐文案，风格品质尊享，突出品牌价值。",
            "price_sensitive", "为价格敏感用户撰写推荐文案，突出性价比和促销利益点。",
            "active", "为活跃用户撰写推荐文案，突出商品亮点和使用场景。",
            "churn_risk", "为流失风险用户撰写召回文案，突出专属优惠和限时激励。"
    );

    private static final List<String> FORBIDDEN_WORDS = List.of(
            "最好的", "第一", "国家级", "全球首", "绝对", "100%", "永久", "万能"
    );

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MarketingCopyAgent(ChatClient.Builder chatClientBuilder) {
        super("marketing_copy", 10.0, 2);
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        UserProfile profile = (UserProfile) params.get("userProfile");
        List<Product> products = (List<Product>) params.getOrDefault("products", List.of());

        if (products.isEmpty()) {
            return AgentResult.builder()
                    .agentName(name)
                    .success(true)
                    .data(Map.of("copies", List.of(), "template_used", "active", "compliance_checked", true))
                    .confidence(1.0)
                    .build();
        }

        String templateKey = selectTemplate(profile);
        String systemPrompt = TEMPLATES.getOrDefault(templateKey, TEMPLATES.get("active"))
                + "\n每个商品生成一条30到50字的推荐文案，输出JSON数组: [{\"product_id\":\"xxx\",\"copy\":\"文案\"}]";
        String productInfo = products.stream()
                .map(p -> "ID:" + p.getProductId() + " " + p.getName() + " ￥" + p.getPrice() + " " + p.getTags())
                .collect(Collectors.joining("\n"));

        String response;
        boolean llmUsed = true;
        try {
            response = chatClient.prompt()
                    .system(systemPrompt)
                    .user("商品列表:\n" + productInfo)
                    .call()
                    .content();
        } catch (Exception ex) {
            llmUsed = false;
            response = buildFallbackCopies(products, templateKey);
        }

        List<Map<String, String>> copies = parseCopies(response);
        if (copies.isEmpty()) {
            llmUsed = false;
            copies = parseCopies(buildFallbackCopies(products, templateKey));
        }
        copies = copies.stream().map(this::complianceCheck).collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("copies", copies);
        data.put("template_used", templateKey);
        data.put("llm_used", llmUsed);
        data.put("compliance_checked", true);

        return AgentResult.builder()
                .agentName(name)
                .success(true)
                .data(data)
                .confidence(0.9)
                .build();
    }

    private String selectTemplate(UserProfile profile) {
        if (profile == null || profile.getSegments() == null) {
            return "active";
        }
        List<String> priority = List.of("new_user", "high_value", "churn_risk", "price_sensitive", "active");
        for (String segment : priority) {
            if (profile.getSegments().contains(segment)) {
                return segment;
            }
        }
        return "active";
    }

    private List<Map<String, String>> parseCopies(String raw) {
        try {
            String cleaned = stripCodeFence(raw);
            return objectMapper.readValue(cleaned, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse copies: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildFallbackCopies(List<Product> products, String templateKey) {
        List<Map<String, String>> items = products.stream().map(product -> Map.of(
                "product_id", product.getProductId(),
                "copy", switch (templateKey) {
                    case "new_user" -> "新客上手很合适，" + product.getName() + " 兼顾体验与价格，值得先加入购物车看看。";
                    case "high_value" -> product.getName() + " 兼具品质与性能，适合追求体验稳定与品牌价值的你。";
                    case "price_sensitive" -> product.getName() + " 性价比很突出，当前价格和配置都比较友好，入手压力更小。";
                    case "churn_risk" -> "为你保留了 " + product.getName() + " 的专属优惠机会，现在回来看会更划算。";
                    default -> product.getName() + " 很贴合你当前的浏览偏好，功能与口碑都比较均衡。";
                }
        )).collect(Collectors.toList());
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            return "[]";
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

    private Map<String, String> complianceCheck(Map<String, String> copyItem) {
        String text = copyItem.getOrDefault("copy", "");
        for (String word : FORBIDDEN_WORDS) {
            text = text.replace(word, "***");
        }
        Map<String, String> result = new HashMap<>(copyItem);
        result.put("copy", text);
        return result;
    }
}
