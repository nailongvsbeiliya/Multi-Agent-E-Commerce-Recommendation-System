package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String userId;
    private Integer age;
    private String gender;
    private String city;
    private List<String> segments;
    private List<String> preferredCategories;
    private double[] priceRange;
    private List<String> recentViews;
    private List<String> recentPurchases;
    private Map<String, Double> rfmScore;
    private Map<String, Object> realTimeTags;
}
