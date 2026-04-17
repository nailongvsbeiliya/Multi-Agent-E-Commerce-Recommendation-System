package com.ecommerce.agent;

import com.ecommerce.model.AgentResult;
import com.ecommerce.model.UserProfile;
import com.ecommerce.service.UserFeatureStoreService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class UserProfileAgent extends BaseAgent {

    private static final String SYSTEM_PROMPT = """
            你是电商用户画像分析专家。
            请根据实时行为特征总结用户分群、偏好类目、价格带和关键标签。
            输出一段简洁中文摘要，不需要JSON。
            """;

    private final ChatClient chatClient;
    private final UserFeatureStoreService featureStoreService;

    public UserProfileAgent(ChatClient.Builder chatClientBuilder, UserFeatureStoreService featureStoreService) {
        super("user_profile", 5.0, 2);
        this.chatClient = chatClientBuilder.build();
        this.featureStoreService = featureStoreService;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected AgentResult execute(Map<String, Object> params) throws Exception {
        String userId = (String) params.get("userId");
        Map<String, Object> behavior = featureStoreService.getRealtimeFeatures(userId, (Map<String, Object>) params.get("context"));
        Map<String, Double> rfm = featureStoreService.computeRfm(behavior);
        List<String> segments = featureStoreService.segment(behavior, rfm);
        UserProfile profile = featureStoreService.buildProfile(userId, behavior, rfm, segments);
        SummaryResult summaryResult = summarizeProfile(userId, behavior, profile);

        Map<String, Object> data = new HashMap<>();
        data.put("behavior_features", behavior);
        data.put("raw_analysis", summaryResult.content());
        data.put("llm_used", summaryResult.llmUsed());
        data.put("profile", profile);

        return AgentResult.builder()
                .agentName(name)
                .success(true)
                .data(data)
                .confidence(0.88)
                .build();
    }

    private SummaryResult summarizeProfile(String userId, Map<String, Object> behavior, UserProfile profile) {
        try {
            String content = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user("用户ID: " + userId
                            + "\n行为特征: " + behavior
                            + "\n结构化画像: segments=" + profile.getSegments()
                            + ", preferredCategories=" + profile.getPreferredCategories()
                            + ", rfm=" + profile.getRfmScore())
                    .call()
                    .content();
            return new SummaryResult(content, true);
        } catch (Exception e) {
            log.warn("Failed to summarize profile for {}: {}", userId, e.getMessage());
            String fallback = "segments=" + profile.getSegments() + ", preferredCategories=" + profile.getPreferredCategories();
            return new SummaryResult(fallback, false);
        }
    }

    private record SummaryResult(String content, boolean llmUsed) {
    }
}
