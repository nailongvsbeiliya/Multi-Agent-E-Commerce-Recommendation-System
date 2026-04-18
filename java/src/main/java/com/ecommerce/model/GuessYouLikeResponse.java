package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuessYouLikeResponse {

    private String userId;
    private String sessionId;
    private String strategy;
    private List<GuessItem> items;

    @Builder.Default
    private Instant timestamp = Instant.now();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GuessItem {
        private String productId;
        private String name;
        private String category;
        private String brand;
        private double price;
        private int stock;
        private double score;
        private String reason;
    }
}
