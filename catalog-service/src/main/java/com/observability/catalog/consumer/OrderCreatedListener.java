package com.observability.catalog.consumer;

import com.observability.catalog.services.InventoryService;
import com.observability.commons.kafka.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes order-created events from Kafka.
 * Creates a CONSUMER span (linked to the HTTP trace via traceparent header).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCreatedListener {

    private final InventoryService inventoryService;

    /**
     * Kafka listener for order.created.topic.
     * Runs inside a CONSUMER span linked to the original POST /api/orders trace.
     */
    @KafkaListener(topics = "${message-broker.topic.order-created}", id = "order-created.consumer")
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Received order-created event: orderId={} productId={} quantity={}",
                event.getOrderId(), event.getProductId(), event.getQuantity());
        inventoryService.reserveStock(event);
    }
}
