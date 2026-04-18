package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextRecommendationRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String userId;
    private String sessionId;
    @Builder.Default
    private Boolean resetSession = false;
    private String query;
    private String scene;
    private Integer numItems;
    private Map<String, Object> context;
}
