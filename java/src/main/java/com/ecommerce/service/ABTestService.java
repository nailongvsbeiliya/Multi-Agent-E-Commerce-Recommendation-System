package com.ecommerce.service;

import com.ecommerce.model.ExperimentAssignment;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A/B测试服务 — 流量分桶 + Thompson Sampling
 */
@Service
public class ABTestService {

    private static final int BUCKET_COUNT = 100;
    private static final String EXPERIMENT_ID = "rec_strategy";

    private final Map<String, ArmState> states = new ConcurrentHashMap<>();

    public ABTestService() {
        states.put("control", new ArmState("control", "rule_based"));
        states.put("treatment_llm", new ArmState("treatment_llm", "llm_rerank"));
        states.put("explore", new ArmState("explore", "explore_diversity"));
    }

    public ExperimentAssignment assign(String userId) {
        return assign(userId, EXPERIMENT_ID);
    }

    public synchronized ExperimentAssignment assign(String userId, String experimentId) {
        int bucket = hashBucket(userId, experimentId);
        String group = bucket < 40 ? "control" : sampleBestArm();
        ArmState armState = states.getOrDefault(group, states.get("control"));

        return ExperimentAssignment.builder()
                .experimentId(experimentId)
                .group(group)
                .bucket(bucket)
                .strategy(armState.strategy())
                .posteriorMeans(snapshotMeans())
                .build();
    }

    public synchronized void recordOutcome(String userId, String group, boolean converted) {
        recordOutcome(userId, EXPERIMENT_ID, group, converted);
    }

    public synchronized void recordOutcome(String userId, String experimentId, String group, boolean converted) {
        ArmState armState = states.getOrDefault(group, states.get("control"));
        if (converted) {
            armState.alpha++;
        } else {
            armState.beta++;
        }
    }

    public synchronized Map<String, Object> getExperimentSnapshot() {
        Map<String, Object> groups = new LinkedHashMap<>();
        states.forEach((group, state) -> groups.put(group, Map.of(
                "strategy", state.strategy(),
                "alpha", state.alpha,
                "beta", state.beta,
                "mean_reward", round(state.mean())
        )));
        return Map.of(
                "experiment_id", EXPERIMENT_ID,
                "name", "推荐策略实验",
                "allocation", "bucket_hash + thompson_sampling",
                "groups", groups
        );
    }

    private int hashBucket(String userId, String experimentId) {
        try {
            String raw = userId + ":" + experimentId;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            int value = ((hash[0] & 0xFF) << 24) | ((hash[1] & 0xFF) << 16)
                    | ((hash[2] & 0xFF) << 8) | (hash[3] & 0xFF);
            return Math.abs(value) % BUCKET_COUNT;
        } catch (Exception e) {
            return Math.abs(userId.hashCode()) % BUCKET_COUNT;
        }
    }

    private String sampleBestArm() {
        String bestGroup = "control";
        double bestScore = -1.0;
        for (ArmState armState : states.values()) {
            double score = sampleBeta(armState.alpha, armState.beta);
            if (score > bestScore) {
                bestScore = score;
                bestGroup = armState.group();
            }
        }
        return bestGroup;
    }

    private double sampleBeta(int alpha, int beta) {
        double x = sampleGamma(alpha);
        double y = sampleGamma(beta);
        return x / (x + y);
    }

    private double sampleGamma(int shape) {
        double sum = 0.0;
        for (int i = 0; i < shape; i++) {
            sum += -Math.log(Math.max(Math.random(), 1.0E-9));
        }
        return sum;
    }

    private Map<String, Double> snapshotMeans() {
        Map<String, Double> means = new LinkedHashMap<>();
        states.forEach((group, state) -> means.put(group, round(state.mean())));
        return means;
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static final class ArmState {
        private final String group;
        private final String strategy;
        private int alpha = 1;
        private int beta = 1;

        private ArmState(String group, String strategy) {
            this.group = group;
            this.strategy = strategy;
        }

        private String group() {
            return group;
        }

        private String strategy() {
            return strategy;
        }

        private double mean() {
            return (double) alpha / (alpha + beta);
        }
    }
}
