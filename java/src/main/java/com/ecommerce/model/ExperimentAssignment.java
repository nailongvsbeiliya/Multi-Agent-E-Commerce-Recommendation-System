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
public class ExperimentAssignment implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String experimentId;
    private String group;
    private int bucket;
    private String strategy;
    private Map<String, Double> posteriorMeans;
}
