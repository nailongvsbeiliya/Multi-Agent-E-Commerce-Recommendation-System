package com.ecommerce.service;

import com.ecommerce.model.UserProfile;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UserFeatureStoreService {

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    public UserFeatureStoreService(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplateProvider = redisTemplateProvider;
    }

    public Map<String, Object> getRealtimeFeatures(String userId, Map<String, Object> context) {
        Map<String, Object> fallback = buildFallback(userId, context);
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return fallback;
        }

        try {
            String prefix = "user:" + userId + ":";
            Map<String, Object> data = new HashMap<>(fallback);
            data.put("clicks_1h", readInt(redisTemplate, prefix + "clicks_1h").orElse((Integer) fallback.get("clicks_1h")));
            data.put("views_7d", readInt(redisTemplate, prefix + "views_7d").orElse((Integer) fallback.get("views_7d")));
            data.put("purchases_30d", readInt(redisTemplate, prefix + "purchases_30d").orElse((Integer) fallback.get("purchases_30d")));
            data.put("avg_order_amount", readDouble(redisTemplate, prefix + "avg_order_amount").orElse((Double) fallback.get("avg_order_amount")));
            data.put("categories", Optional.ofNullable(redisTemplate.opsForList().range(prefix + "categories", 0, 4))
                    .filter(list -> !list.isEmpty())
                    .orElse((List<String>) fallback.get("categories")));
            data.put("recent_views", Optional.ofNullable(redisTemplate.opsForList().range(prefix + "recent_views", 0, 4))
                    .filter(list -> !list.isEmpty())
                    .orElse((List<String>) fallback.get("recent_views")));
            data.put("recent_purchases", Optional.ofNullable(redisTemplate.opsForList().range(prefix + "recent_purchases", 0, 4))
                    .filter(list -> !list.isEmpty())
                    .orElse((List<String>) fallback.get("recent_purchases")));
            return data;
        } catch (Exception ex) {
            return fallback;
        }
    }

    public Map<String, Double> computeRfm(Map<String, Object> features) {
        double recency = Math.min(1.0, ((Integer) features.getOrDefault("clicks_1h", 0)) / 20.0);
        double frequency = Math.min(1.0, ((Integer) features.getOrDefault("purchases_30d", 0)) / 8.0);
        double monetary = Math.min(1.0, ((Double) features.getOrDefault("avg_order_amount", 0.0)) / 5000.0);
        return Map.of(
                "recency", round(recency),
                "frequency", round(frequency),
                "monetary", round(monetary)
        );
    }

    public List<String> segment(Map<String, Object> features, Map<String, Double> rfm) {
        if (rfm.get("monetary") >= 0.7 && rfm.get("frequency") >= 0.5) {
            return List.of("high_value", "active");
        }
        if (rfm.get("recency") >= 0.7 && rfm.get("frequency") < 0.3) {
            return List.of("new_user");
        }
        if (((Double) features.getOrDefault("avg_order_amount", 0.0)) < 600.0) {
            return List.of("price_sensitive", "active");
        }
        return List.of("active");
    }

    public UserProfile buildProfile(String userId, Map<String, Object> features, Map<String, Double> rfm, List<String> segments) {
        List<String> categories = (List<String>) features.getOrDefault("categories", List.of());
        return UserProfile.builder()
                .userId(userId)
                .segments(segments)
                .preferredCategories(categories)
                .recentViews((List<String>) features.getOrDefault("recent_views", List.of()))
                .recentPurchases((List<String>) features.getOrDefault("recent_purchases", List.of()))
                .priceRange(inferPriceRange(segments))
                .rfmScore(rfm)
                .realTimeTags(Map.of(
                        "request_scene", features.getOrDefault("scene", "homepage"),
                        "active_hour", Instant.now().atZone(java.time.ZoneId.systemDefault()).getHour(),
                        "last_active_at", Instant.now().minus(20, ChronoUnit.MINUTES).toString()
                ))
                .build();
    }

    private Map<String, Object> buildFallback(String userId, Map<String, Object> context) {
        return new HashMap<>(Map.of(
                "user_id", userId,
                "scene", context != null ? context.getOrDefault("scene", "homepage") : "homepage",
                "clicks_1h", 12,
                "views_7d", 25,
                "purchases_30d", 3,
                "avg_order_amount", 299.0,
                "categories", List.of("手机", "耳机", "平板"),
                "recent_views", List.of("手机", "耳机", "平板"),
                "recent_purchases", List.of("充电器")
        ));
    }

    private Optional<Integer> readInt(StringRedisTemplate redisTemplate, String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key)).map(Integer::parseInt);
    }

    private Optional<Double> readDouble(StringRedisTemplate redisTemplate, String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key)).map(Double::parseDouble);
    }

    private double[] inferPriceRange(List<String> segments) {
        if (segments.contains("high_value")) {
            return new double[]{2000, 12000};
        }
        if (segments.contains("price_sensitive")) {
            return new double[]{0, 3000};
        }
        return new double[]{500, 8000};
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
