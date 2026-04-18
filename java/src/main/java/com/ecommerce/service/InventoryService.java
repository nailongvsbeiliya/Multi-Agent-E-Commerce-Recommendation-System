package com.ecommerce.service;

import com.ecommerce.entity.InventoryRecord;
import com.ecommerce.model.Product;
import com.ecommerce.repository.InventoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class InventoryService {
    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository inventoryRepository;
    private final ProductCatalogService productCatalogService;

    public InventoryService(InventoryRepository inventoryRepository, ProductCatalogService productCatalogService) {
        this.inventoryRepository = inventoryRepository;
        this.productCatalogService = productCatalogService;
    }

    public Map<String, Integer> getRealtimeStock(List<String> productIds) {
        Map<String, Integer> result = new HashMap<>();
        inventoryRepository.findAllById(productIds).forEach(record ->
                result.put(record.getProductId(), Math.max(0, record.getAvailableStock() - record.getReservedStock())));

        for (String productId : productIds) {
            result.computeIfAbsent(productId, id -> {
                Product product = productCatalogService.getById(id);
                return product != null ? product.getStock() : 0;
            });
        }
        return result;
    }

    public void seedIfEmpty() {
        Map<String, InventoryRecord> existingMap = inventoryRepository.findAll().stream()
                .collect(Collectors.toMap(InventoryRecord::getProductId, record -> record, (left, right) -> left));

        List<InventoryRecord> missingRecords = productCatalogService.getAllProducts().stream()
                .filter(product -> !existingMap.containsKey(product.getProductId()))
                .map(product -> InventoryRecord.builder()
                        .productId(product.getProductId())
                        .availableStock(product.getStock())
                        .reservedStock(Math.max(0, product.getStock() / 20))
                        .lastUpdated(Instant.now())
                        .build())
                .collect(Collectors.toList());

        if (missingRecords.isEmpty()) {
            log.info("Inventory seed skipped, existing rows={} (already in sync with catalog)", existingMap.size());
            return;
        }

        inventoryRepository.saveAll(missingRecords);
        log.info("Inventory backfill completed, existing rows={}, inserted missing rows={}",
                existingMap.size(), missingRecords.size());
    }
}
