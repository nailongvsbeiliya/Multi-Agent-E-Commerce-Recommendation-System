package com.ecommerce.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "product_catalog")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRecord {

    @Id
    @Column(name = "product_id", nullable = false, length = 64)
    private String productId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "category", nullable = false, length = 100)
    private String category;

    @Column(name = "price", nullable = false)
    private double price;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "brand", length = 120)
    private String brand;

    @Column(name = "seller_id", length = 64)
    private String sellerId;

    @Column(name = "stock", nullable = false)
    private int stock;

    @Column(name = "brand_preference_weight", nullable = false)
    private double brandPreferenceWeight;

    @Column(name = "sales_heat", nullable = false)
    private double salesHeat;

    @Convert(converter = StringListToTextConverter.class)
    @Column(name = "tags", length = 1000)
    private List<String> tags;
}
