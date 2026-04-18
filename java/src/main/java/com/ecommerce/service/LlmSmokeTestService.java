package com.ecommerce.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Service
public class LlmSmokeTestService {

    private final ChatClient chatClient;
    private final String model;
    private final String baseUrl;
    private final String apiKey;
    private final Environment environment;

    public LlmSmokeTestService(ChatClient.Builder chatClientBuilder,
                               @Value("${spring.ai.openai.chat.options.model:unknown}") String model,
                               @Value("${spring.ai.openai.base-url:}") String baseUrl,
                               @Value("${spring.ai.openai.api-key:}") String apiKey,
                               Environment environment) {
        this.chatClient = chatClientBuilder.build();
        this.model = model;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.environment = environment;
    }

    public Map<String, Object> status() {
        Map<String, Object> result = new HashMap<>();
        result.put("provider", "openai-compatible");
        result.put("model", model);
        result.put("baseUrl", baseUrl);
        result.put("apiKeyConfigured", apiKey != null && !apiKey.isBlank() && !"your_api_key_here".equals(apiKey));
        result.put("activeProfiles", Arrays.asList(environment.getActiveProfiles()));
        return result;
    }

    public Map<String, Object> smoke(String prompt) {
        long start = System.nanoTime();
        String finalPrompt = (prompt == null || prompt.isBlank())
                ? "Reply exactly: PONG"
                : prompt;

        Map<String, Object> result = new HashMap<>();
        result.putAll(status());
        result.put("prompt", finalPrompt);

        try {
            String response = chatClient.prompt()
                    .system("You are a connectivity checker. Keep the answer short.")
                    .user(finalPrompt)
                    .call()
                    .content();

            double latencyMs = (System.nanoTime() - start) / 1_000_000.0;
            result.put("success", true);
            result.put("response", response);
            result.put("latencyMs", latencyMs);
        } catch (Exception e) {
            double latencyMs = (System.nanoTime() - start) / 1_000_000.0;
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("latencyMs", latencyMs);
        }

        return result;
    }
}
