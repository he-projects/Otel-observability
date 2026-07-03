package com.observability.catalog.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ProductService {

    private static final List<Map<String, Object>> PRODUCTS = List.of(
            Map.of("id", 1, "name", "Keyboard", "price", 49.99),
            Map.of("id", 2, "name", "Mouse", "price", 19.99),
            Map.of("id", 3, "name", "Monitor", "price", 299.99)
    );

    public List<Map<String, Object>> readAll() {
        log.info("Listing all products");
        return PRODUCTS;
    }

    public Map<String, Object> readById(int id) {
        log.info("Fetching product id={}", id);
        return PRODUCTS.stream()
                .filter(p -> (int) p.get("id") == id)
                .findFirst()
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Product not found: " + id));
    }
}
