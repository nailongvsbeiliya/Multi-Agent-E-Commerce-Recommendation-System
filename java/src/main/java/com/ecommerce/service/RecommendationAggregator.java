package com.ecommerce.service;

import com.ecommerce.model.Product;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RecommendationAggregator {

    public List<Product> aggregate(List<Product> rerankedProducts,
                                   List<Product> recalledProducts,
                                   List<String> availableIds,
                                   int numItems) {
        Set<String> availableSet = new HashSet<>(availableIds);
        Map<String, Product> merged = new HashMap<>();
        Map<String, Integer> rerankOrder = new HashMap<>();
        for (int i = 0; i < rerankedProducts.size(); i++) {
            rerankOrder.putIfAbsent(rerankedProducts.get(i).getProductId(), i);
        }

        for (Product product : rerankedProducts) {
            if (availableSet.contains(product.getProductId())) {
                merged.putIfAbsent(product.getProductId(), product);
            }
        }
        for (Product product : recalledProducts) {
            if (availableSet.contains(product.getProductId())) {
                merged.putIfAbsent(product.getProductId(), product);
            }
        }

        List<Product> result = new ArrayList<>(merged.values());
        result.sort(Comparator
                .comparingInt((Product p) -> rerankOrder.getOrDefault(p.getProductId(), Integer.MAX_VALUE))
                .thenComparing(Comparator.comparingDouble(Product::getScore).reversed())
                .thenComparing(Product::getProductId));

        if (result.isEmpty()) {
            result = new ArrayList<>(rerankedProducts.isEmpty() ? recalledProducts : rerankedProducts);
        }

        return result.stream().limit(Math.max(1, numItems)).toList();
    }
}
