package com.observability.catalog.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Business logic for the product catalog.
 * Products are stored in memory (no database) so the demo stays simple.
 * Each lookup writes a log line so traces and logs can be correlated in Grafana.
 */
@Service
@Slf4j
public class ProductService {

    /** Static demo catalog: id, name, and unit price for each product. */
    private static final List<Map<String, Object>> PRODUCTS = List.of(
            Map.of("id", 1, "name", "Keyboard", "price", 49.99),
            Map.of("id", 2, "name", "Mouse", "price", 19.99),
            Map.of("id", 3, "name", "Monitor", "price", 299.99)
    );

    /**
     * Returns the full product list without filtering.
     * Logs the request so it appears in Loki alongside the active trace.
     */
    public List<Map<String, Object>> readAll() {
        log.info("Listing all products");
        return PRODUCTS;
    }

    /**
     * Finds a single product by its numeric id.
     * Streams the in-memory list, keeps the first match, and throws 404 if none is found.
     */
    public Map<String, Object> readById(int id) {
        log.info("Fetching product id={}", id);
        return PRODUCTS.stream()
                .filter(p -> (int) p.get("id") == id)
                .findFirst()
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Product not found: " + id));
    }
}
