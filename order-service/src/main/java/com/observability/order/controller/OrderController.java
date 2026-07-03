package com.observability.order.controller;

import com.observability.order.services.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * HTTP layer for order operations.
 * Accepts JSON request bodies and delegates all business logic to {@link OrderService}.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * POST /api/orders
     * Creates a new order from a JSON body, e.g. {@code {"productId": 1, "quantity": 2}}.
     */
    @PostMapping
    public Map<String, Object> createOrder(@RequestBody Map<String, Object> request) {
        return orderService.createOrder(request);
    }

    /**
     * GET /api/orders
     * Lists orders created during this demo session (in-memory).
     */
    @GetMapping
    public List<Map<String, Object>> listOrders() {
        return orderService.listRecentOrders();
    }

    /**
     * GET /api/orders/health-check
     * Checks that order-service can reach catalog-service over Feign.
     */
    @GetMapping("/health-check")
    public Map<String, String> healthCheck() {
        return orderService.healthCheck();
    }
}
