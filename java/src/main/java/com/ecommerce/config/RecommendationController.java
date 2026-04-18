package com.ecommerce.config;

import com.ecommerce.model.GuessYouLikeResponse;
import com.ecommerce.model.RecommendationRequest;
import com.ecommerce.model.RecommendationResponse;
import com.ecommerce.model.TextRecommendationRequest;
import com.ecommerce.orchestrator.SupervisorOrchestrator;
import com.ecommerce.service.ABTestService;
import com.ecommerce.service.ChatRecommendationService;
import com.ecommerce.service.GuessYouLikeService;
import com.ecommerce.service.LlmSmokeTestService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class RecommendationController {

    private final SupervisorOrchestrator orchestrator;
    private final ABTestService abTestService;
    private final LlmSmokeTestService llmSmokeTestService;
    private final ChatRecommendationService chatRecommendationService;
    private final GuessYouLikeService guessYouLikeService;

    public RecommendationController(SupervisorOrchestrator orchestrator,
                                    ABTestService abTestService,
                                    LlmSmokeTestService llmSmokeTestService,
                                    ChatRecommendationService chatRecommendationService,
                                    GuessYouLikeService guessYouLikeService) {
        this.orchestrator = orchestrator;
        this.abTestService = abTestService;
        this.llmSmokeTestService = llmSmokeTestService;
        this.chatRecommendationService = chatRecommendationService;
        this.guessYouLikeService = guessYouLikeService;
    }

    @PostMapping("/recommend")
    public RecommendationResponse recommend(@RequestBody RecommendationRequest request) {
        return orchestrator.recommend(request);
    }

    @PostMapping("/recommend/chat")
    public RecommendationResponse recommendByText(@RequestBody TextRecommendationRequest request) {
        return chatRecommendationService.recommendByText(request);
    }

    @GetMapping("/recommend/guess-you-like")
    public GuessYouLikeResponse guessYouLike(@RequestParam(value = "userId", required = false) String userId,
                                             @RequestParam(value = "sessionId", required = false) String sessionId,
                                             @RequestParam(value = "numItems", required = false) Integer numItems) {
        return guessYouLikeService.guessYouLike(userId, sessionId, numItems);
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "healthy", "language", "java");
    }

    @GetMapping("/llm/status")
    public Map<String, Object> llmStatus() {
        return llmSmokeTestService.status();
    }

    @GetMapping("/llm/smoke")
    public Map<String, Object> llmSmoke(@RequestParam(value = "prompt", required = false) String prompt) {
        return llmSmokeTestService.smoke(prompt);
    }

    @GetMapping("/experiments")
    public Map<String, Object> getExperiments() {
        return Map.of("rec_strategy", abTestService.getExperimentSnapshot());
    }

    @PostMapping("/experiments/track")
    public Map<String, Object> trackExperiment(@RequestBody Map<String, Object> request) {
        String userId = String.valueOf(request.getOrDefault("user_id", "anonymous"));
        String group = String.valueOf(request.getOrDefault("group", "control"));
        boolean converted = Boolean.parseBoolean(String.valueOf(request.getOrDefault("converted", false)));
        abTestService.recordOutcome(userId, group, converted);
        return Map.of(
                "status", "ok",
                "group", group,
                "converted", converted,
                "snapshot", abTestService.getExperimentSnapshot()
        );
    }
}
