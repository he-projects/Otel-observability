package com.observability.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    private static final List<Map<String, Object>> PRODUCTS = List.of(
            Map.of("id", 1, "name", "Keyboard", "price", 49.99),
            Map.of("id", 2, "name", "Mouse", "price", 19.99),
            Map.of("id", 3, "name", "Monitor", "price", 299.99)
    );

    @GetMapping
    public List<Map<String, Object>> list() {
        log.info("Listing all products");
        return PRODUCTS;
    }

    @GetMapping("/{id}")
    public Map<String, Object> getById(@PathVariable int id) {
        log.info("Fetching product id={}", id);
        return PRODUCTS.stream()
                .filter(p -> (int) p.get("id") == id)
                .findFirst()
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Product not found: " + id));
    }
}
