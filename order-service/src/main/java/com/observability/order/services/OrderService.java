package com.observability.order.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    private final AtomicLong orderSequence = new AtomicLong(1000);
    private final RestTemplate restTemplate;

    @Value("${catalog.base-url}")
    String catalogBaseUrl;


    public Map<String, Object> createOrder(Map<String, Object> request) {
        int productId = (int) request.get("productId");
        int quantity = request.containsKey("quantity") ? (int) request.get("quantity") : 1;

        log.info("Creating order for productId={} quantity={}", productId, quantity);

        @SuppressWarnings("unchecked")
        Map<String, Object> product = restTemplate.getForObject(
                catalogBaseUrl + "/api/products/" + productId,
                Map.class
        );

        double price = ((Number) product.get("price")).doubleValue();
        long orderId = orderSequence.incrementAndGet();

        log.info("Order {} created for product '{}' total={}", orderId, product.get("name"), price * quantity);

        return Map.of(
                "orderId", orderId,
                "product", product,
                "quantity", quantity,
                "total", price * quantity
        );
    }

    public Map<String, String> healthCheck() {
        restTemplate.getForObject(catalogBaseUrl + "/api/products", List.class);
        return Map.of("status", "ok", "catalog", "reachable");
    }
}
