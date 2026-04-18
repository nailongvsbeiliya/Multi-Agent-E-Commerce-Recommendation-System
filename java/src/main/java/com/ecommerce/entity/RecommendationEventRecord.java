package com.ecommerce.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "recommendation_event")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationEventRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, length = 64)
    private String requestId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "scene", nullable = false, length = 32)
    private String scene;

    @Column(name = "num_items", nullable = false)
    private Integer numItems;

    @Column(name = "experiment_group", nullable = false, length = 32)
    private String experimentGroup;

    @Column(name = "strategy", nullable = false, length = 64)
    private String strategy;

    @Column(name = "total_latency_ms", nullable = false)
    private Double totalLatencyMs;

    @Column(name = "converted", nullable = false)
    private Boolean converted;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
