package com.ecommerce.service;

import com.ecommerce.entity.ProductRecord;
import com.ecommerce.model.Product;
import com.ecommerce.model.UserProfile;
import com.ecommerce.repository.ProductCatalogRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProductCatalogService {

    private final ProductCatalogRepository productCatalogRepository;

    public ProductCatalogService(ProductCatalogRepository productCatalogRepository) {
        this.productCatalogRepository = productCatalogRepository;
    }

    public List<Product> getAllProducts() {
        return productCatalogRepository.findAll(Sort.by(Sort.Direction.ASC, "productId")).stream()
                .map(this::toModel)
                .collect(Collectors.toList());
    }

    public Product getById(String productId) {
        return productCatalogRepository.findById(productId)
                .map(this::toModel)
                .orElse(null);
    }

    public List<Product> recallProducts(UserProfile profile, int limit, String strategy) {
        List<Product> ranked = new ArrayList<>(getAllProducts());
        Set<String> preferredCategories = profile != null && profile.getPreferredCategories() != null
                ? new HashSet<>(profile.getPreferredCategories())
                : Set.of();
        double minPrice = profile != null && profile.getPriceRange() != null && profile.getPriceRange().length > 0
                ? profile.getPriceRange()[0] : 0.0;
        double maxPrice = profile != null && profile.getPriceRange() != null && profile.getPriceRange().length > 1
                ? profile.getPriceRange()[1] : Double.MAX_VALUE;
        boolean explore = "explore_diversity".equals(strategy);

        for (Product product : ranked) {
            product.setScore(scoreProduct(product, preferredCategories, minPrice, maxPrice, explore));
        }
        ranked.sort(Comparator
                .comparingDouble(Product::getScore)
                .reversed()
                .thenComparing(Product::getProductId));

        return ranked.stream().limit(Math.max(1, limit)).collect(Collectors.toList());
    }

    private double scoreProduct(Product product, Set<String> preferredCategories, double minPrice, double maxPrice, boolean explore) {
        double score = baseScoreFromAttributes(product);
        score += normalizeBrandWeight(product.getBrandPreferenceWeight());
        score += normalizeSalesHeat(product.getSalesHeat(), explore);
        if (preferredCategories.contains(product.getCategory())) {
            score += 1.2;
        }
        if (product.getPrice() >= minPrice && product.getPrice() <= maxPrice) {
            score += 0.6;
        }
        if (product.getTags() != null && product.getTags().contains("新品")) {
            score += explore ? 0.7 : 0.2;
        }
        if (product.getTags() != null && product.getTags().contains("性价比")) {
            score += 0.3;
        }
        return round3(score);
    }

    private double baseScoreFromAttributes(Product product) {
        double score = 0.25;
        if (product.getTags() != null) {
            if (product.getTags().contains("旗舰")) {
                score += 0.22;
            }
            if (product.getTags().contains("新品")) {
                score += 0.18;
            }
            if (product.getTags().contains("高性能")) {
                score += 0.12;
            }
            if (product.getTags().contains("性价比")) {
                score += 0.16;
            }
            if (product.getTags().contains("降噪") || product.getTags().contains("4K")) {
                score += 0.08;
            }
        }
        if (product.getStock() >= 500) {
            score += 0.12;
        } else if (product.getStock() >= 200) {
            score += 0.08;
        } else if (product.getStock() > 0) {
            score += 0.03;
        }
        if (product.getPrice() >= 1000 && product.getPrice() <= 8000) {
            score += 0.06;
        }
        return score;
    }

    private double normalizeBrandWeight(double brandPreferenceWeight) {
        double bounded = Math.max(0.7, Math.min(1.3, brandPreferenceWeight));
        return (bounded - 1.0) * 0.45;
    }

    private double normalizeSalesHeat(double salesHeat, boolean explore) {
        double bounded = Math.max(0.0, Math.min(1.0, salesHeat));
        // Explore strategy lowers head-item bias to improve diversity.
        double factor = explore ? 0.22 : 0.38;
        return bounded * factor;
    }

    private double round3(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private Product toModel(ProductRecord record) {
        return Product.builder()
                .productId(record.getProductId())
                .name(record.getName())
                .category(record.getCategory())
                .price(record.getPrice())
                .description(record.getDescription())
                .brand(record.getBrand())
                .sellerId(record.getSellerId())
                .stock(record.getStock())
                .brandPreferenceWeight(record.getBrandPreferenceWeight())
                .salesHeat(record.getSalesHeat())
                .tags(record.getTags() == null ? List.of() : record.getTags())
                .score(0.0)
                .build();
    }
}
