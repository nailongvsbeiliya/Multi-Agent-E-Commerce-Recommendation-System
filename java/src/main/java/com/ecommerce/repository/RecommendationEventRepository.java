package com.ecommerce.repository;

import com.ecommerce.entity.RecommendationEventRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationEventRepository extends JpaRepository<RecommendationEventRecord, Long> {
}
