package com.observability.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for catalog-service (port 8080).
 * Scans the shared {@code com.observability} package so commons beans (traceId, Kafka) are loaded.
 */
@SpringBootApplication(scanBasePackages = {"com.observability"})
public class CatalogApplication {

    /** Starts the Spring Boot application. */
    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }
}
