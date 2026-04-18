package com.ecommerce.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String productId;
    private String name;
    private String category;
    private double price;
    private String description;
    private String brand;
    private String sellerId;
    private int stock;
    private double brandPreferenceWeight;
    private double salesHeat;
    private List<String> tags;
    private double score;
}
