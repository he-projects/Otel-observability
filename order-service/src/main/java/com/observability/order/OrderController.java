package com.observability.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);
    private final AtomicLong orderSequence = new AtomicLong(1000);

    private final RestTemplate restTemplate;
    private final String catalogBaseUrl;

    public OrderController(
            RestTemplate restTemplate,
            @Value("${catalog.base-url}") String catalogBaseUrl
    ) {
        this.restTemplate = restTemplate;
        this.catalogBaseUrl = catalogBaseUrl;
    }

    @PostMapping
    public Map<String, Object> createOrder(@RequestBody Map<String, Object> request) {
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

    @GetMapping("/health-check")
    public Map<String, String> healthCheck() {
        restTemplate.getForObject(catalogBaseUrl + "/api/products", List.class);
        return Map.of("status", "ok", "catalog", "reachable");
    }
}
