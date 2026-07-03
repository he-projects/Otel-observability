package com.observability.order.controller;

import com.observability.order.services.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public Map<String, Object> createOrder(@RequestBody Map<String, Object> request) {
        return orderService.createOrder(request);
    }

    @GetMapping("/health-check")
    public Map<String, String> healthCheck() {
        return orderService.healthCheck();
    }
}
