package com.ecommerce.service;

import com.ecommerce.entity.RecommendationEventRecord;
import com.ecommerce.repository.RecommendationEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
public class DemoDataSeedService {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeedService.class);

    private static final String[] SCENES = {"homepage", "detail", "campaign", "search", "cart"};
    private static final String[] GROUPS = {"control", "treatment_llm", "explore"};

    private final RecommendationEventRepository recommendationEventRepository;

    public DemoDataSeedService(RecommendationEventRepository recommendationEventRepository) {
        this.recommendationEventRepository = recommendationEventRepository;
    }

    public void seedRecommendationEventsIfEmpty() {
        long existing = recommendationEventRepository.count();
        if (existing > 0) {
            log.info("Recommendation event seed skipped, existing rows={}", existing);
            return;
        }

        Random random = new Random(42L);
        Instant now = Instant.now();
        List<RecommendationEventRecord> records = new ArrayList<>();

        for (int i = 0; i < 300; i++) {
            String group = GROUPS[random.nextInt(GROUPS.length)];
            String strategy = switch (group) {
                case "treatment_llm" -> "llm_rerank";
                case "explore" -> "explore_diversity";
                default -> "rule_based";
            };

            double baseLatency = switch (group) {
                case "treatment_llm" -> 580.0;
                case "explore" -> 470.0;
                default -> 360.0;
            };
            double latency = Math.max(120.0, baseLatency + random.nextGaussian() * 90.0);
            int numItems = 3 + random.nextInt(4);
            String scene = SCENES[random.nextInt(SCENES.length)];
            String userId = String.format("u%03d", 1 + random.nextInt(120));
            boolean converted = random.nextDouble() < conversionProbability(group, scene);
            Instant createdAt = now.minus(random.nextInt(45 * 24 * 60), ChronoUnit.MINUTES);

            records.add(RecommendationEventRecord.builder()
                    .requestId(UUID.randomUUID().toString())
                    .userId(userId)
                    .scene(scene)
                    .numItems(numItems)
                    .experimentGroup(group)
                    .strategy(strategy)
                    .totalLatencyMs(Math.round(latency * 10.0) / 10.0)
                    .converted(converted)
                    .createdAt(createdAt)
                    .build());
        }

        recommendationEventRepository.saveAll(records);
        log.info("Recommendation event seed completed, inserted rows={}", records.size());
    }

    private double conversionProbability(String group, String scene) {
        double groupBoost = switch (group) {
            case "treatment_llm" -> 0.08;
            case "explore" -> 0.05;
            default -> 0.03;
        };
        double sceneBias = switch (scene) {
            case "detail" -> 0.06;
            case "campaign" -> 0.05;
            case "cart" -> 0.07;
            case "search" -> 0.04;
            default -> 0.03;
        };
        return Math.min(0.45, groupBoost + sceneBias);
    }
}
