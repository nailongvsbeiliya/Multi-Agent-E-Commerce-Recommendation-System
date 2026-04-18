package com.ecommerce.repository;

import com.ecommerce.entity.InventoryRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<InventoryRecord, String> {
}
