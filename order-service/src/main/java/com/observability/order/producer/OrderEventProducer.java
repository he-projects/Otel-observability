package com.observability.order.producer;

import com.observability.commons.kafka.event.OrderCreatedEvent;
import com.observability.commons.kafka.producer.KafkaEventProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Publishes order-created events to Kafka with trace context in headers.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OrderEventProducer {

    private final KafkaEventProducer kafkaEventProducer;

    @Value("${message-broker.topic.order-created}")
    private String orderCreatedTopic;

    /**
     * Sends an OrderCreatedEvent to Kafka after a successful order.
     * The message key is the orderId for ordered processing per order.
     */
    public void publishOrderCreated(OrderCreatedEvent event) {
        kafkaEventProducer.send(orderCreatedTopic, String.valueOf(event.getOrderId()), event);
    }
}
