package com.observability.order.services;

import com.observability.commons.kafka.event.OrderCreatedEvent;
import com.observability.order.feign.ProductClient;
import com.observability.order.producer.OrderEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Business logic for creating orders.
 * Uses Feign for synchronous catalog lookup and Kafka for async inventory notification.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final AtomicLong orderSequence = new AtomicLong(1000);
    private final ProductClient productClient;
    private final ObjectProvider<OrderEventProducer> orderEventProducer;

    private final List<Map<String, Object>> recentOrders = Collections.synchronizedList(new ArrayList<>());

    /**
     * Creates an order for the given product.
     *
     * Flow:
     * 1. Parse productId (required) and quantity (optional, default 1) from the JSON body.
     * 2. Call catalog-service via Feign to load product details and current price.
     * 3. Assign a new orderId and compute total = price × quantity.
     * 4. Publish OrderCreatedEvent to Kafka (async span linked to this HTTP trace).
     * 5. Return orderId, product snapshot, quantity, and total to the client.
     */
    public Map<String, Object> createOrder(Map<String, Object> request) {
        if (!request.containsKey("productId")) {
            throw new IllegalArgumentException("productId is required");
        }

        // JSON numbers arrive as Integer (or Double); intValue() handles both.
        // productId identifies which catalog item to order, e.g. 1 = Keyboard.
        int productId = ((Number) request.get("productId")).intValue();

        // quantity is optional; containsKey avoids NPE when the key is absent.
        // Defaults to 1 when the client sends only {"productId": 1}.
        int quantity = request.containsKey("quantity")
                ? ((Number) request.get("quantity")).intValue()
                : 1;

        log.info("Creating order for productId={} quantity={}", productId, quantity);

        // Synchronous Feign call — creates a child HTTP span in the same trace.
        Map<String, Object> product = productClient.getProduct(productId);

        // price may be Integer or Double in the JSON map; Number handles both.
        double price = ((Number) product.get("price")).doubleValue();

        // Monotonic counter: each order gets a unique id (no database in this demo).
        long orderId = orderSequence.incrementAndGet();
        double total = price * quantity;

        // Build and publish async event — Kafka consumer span will link to this trace.
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(orderId)
                .productId(productId)
                .productName((String) product.get("name"))
                .quantity(quantity)
                .total(total)
                .build();
        orderEventProducer.ifAvailable(producer -> producer.publishOrderCreated(event));
        if (orderEventProducer.getIfAvailable() == null) {
            log.info("Kafka disabled — order event not published for orderId={}", orderId);
        }

        Map<String, Object> order = Map.of(
                "orderId", orderId,
                "product", product,
                "quantity", quantity,
                "total", total
        );
        recentOrders.add(order);

        log.info("Order {} created for product '{}' total={}", orderId, product.get("name"), total);
        return order;
    }

    /**
     * Returns orders created in this JVM session (in-memory demo store).
     */
    public List<Map<String, Object>> listRecentOrders() {
        return List.copyOf(recentOrders);
    }

    /**
     * Verifies connectivity to catalog-service by calling GET /api/products via Feign.
     */
    public Map<String, String> healthCheck() {
        productClient.listProducts();
        return Map.of("status", "ok", "catalog", "reachable", "transport", "feign");
    }
}
