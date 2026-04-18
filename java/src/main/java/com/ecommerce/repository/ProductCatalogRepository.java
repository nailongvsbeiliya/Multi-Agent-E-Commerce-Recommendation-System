package com.ecommerce.repository;

import com.ecommerce.entity.ProductRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCatalogRepository extends JpaRepository<ProductRecord, String> {
}
