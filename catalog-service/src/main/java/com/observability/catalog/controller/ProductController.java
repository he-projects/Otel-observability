package com.observability.catalog.controller;

import com.observability.catalog.services.InventoryService;
import com.observability.catalog.services.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * HTTP layer for the product catalog.
 * Exposes read-only endpoints used by clients and by order-service (via Feign).
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final InventoryService inventoryService;

    /**
     * GET /api/products
     * Returns every product in the catalog (id, name, price).
     */
    @GetMapping
    public List<Map<String, Object>> readAll() {
        return productService.readAll();
    }

    /**
     * GET /api/products/{id}
     * Returns one product matching the path variable.
     * Responds with 404 when the id does not exist in the catalog.
     */
    @GetMapping("/{id}")
    public Map<String, Object> readById(@PathVariable int id) {
        return productService.readById(id);
    }

    /**
     * GET /api/products/inventory/reserved
     * Shows units reserved by Kafka consumers (updated after each order).
     */
    @GetMapping("/inventory/reserved")
    public Map<Integer, Integer> reservedInventory() {
        return inventoryService.reservedSnapshot();
    }
}
