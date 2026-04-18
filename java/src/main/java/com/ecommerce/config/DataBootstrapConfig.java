package com.ecommerce.config;

import com.ecommerce.service.InventoryService;
import com.ecommerce.service.DemoDataSeedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;

@Configuration
public class DataBootstrapConfig {
    private static final Logger log = LoggerFactory.getLogger(DataBootstrapConfig.class);

    @Bean
    CommandLineRunner seedData(DataSource dataSource,
                               InventoryService inventoryService,
                               DemoDataSeedService demoDataSeedService) {
        return args -> {
            try (Connection connection = dataSource.getConnection()) {
                log.info("Datasource connected: {} / {}", connection.getMetaData().getURL(),
                        connection.getMetaData().getUserName());
            } catch (Exception ex) {
                log.warn("Datasource metadata check failed: {}", ex.getMessage());
            }
            log.info("Bootstrap demo data started");
            inventoryService.seedIfEmpty();
            demoDataSeedService.seedRecommendationEventsIfEmpty();
            log.info("Bootstrap demo data finished");
        };
    }
}
