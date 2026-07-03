package com.observability.commons.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published by order-service after a successful order.
 * Consumed by catalog-service to simulate inventory reservation (async hop in the trace).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCreatedEvent {
    private Long orderId;
    private int productId;
    private String productName;
    private int quantity;
    private double total;
}
