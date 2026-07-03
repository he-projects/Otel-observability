package com.observability.catalog.services;

import com.observability.commons.kafka.event.OrderCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulates inventory updates triggered by Kafka events.
 * Keeps an in-memory reserved count per product for demo queries.
 */
@Service
@Slf4j
public class InventoryService {

    private final Map<Integer, AtomicInteger> reservedByProduct = new ConcurrentHashMap<>();

    /**
     * Reserves stock when an order is created asynchronously.
     * Logs inside the Kafka consumer span so Loki shows the async hop.
     */
    public void reserveStock(OrderCreatedEvent event) {
        int reserved = reservedByProduct
                .computeIfAbsent(event.getProductId(), id -> new AtomicInteger(0))
                .addAndGet(event.getQuantity());

        log.info("Reserved {} units of productId={} (total reserved={}) for orderId={}",
                event.getQuantity(), event.getProductId(), reserved, event.getOrderId());
    }

    /** Returns how many units are currently reserved per product (demo metric). */
    public Map<Integer, Integer> reservedSnapshot() {
        Map<Integer, Integer> snapshot = new ConcurrentHashMap<>();
        reservedByProduct.forEach((productId, count) -> snapshot.put(productId, count.get()));
        return snapshot;
    }
}
