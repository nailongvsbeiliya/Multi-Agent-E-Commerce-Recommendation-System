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
public class RecommendationRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String userId;
    @Builder.Default
    private String scene = "homepage";
    @Builder.Default
    private int numItems = 10;
    @Builder.Default
    private String userQuery = "";
    private Map<String, Object> context;
}
