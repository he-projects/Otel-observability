package com.observability.commons.kafka.producer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Sends Kafka events with W3C traceparent header so consumer spans link to the HTTP trace.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.application.name}")
    private String applicationName;

    /**
     * Publishes a message to the given topic with traceparent header
     * so the Kafka consumer span links to the current HTTP trace.
     */
    public CompletableFuture<SendResult<String, Object>> send(String topic, String key, Object payload) {
        MessageBuilder<Object> builder = MessageBuilder
                .withPayload(payload)
                .setHeader(KafkaHeaders.TOPIC, topic)
                .setHeader(KafkaHeaders.KEY, key)
                .setHeader("spring_application_name", applicationName);

        // Attach W3C trace context from the active span (e.g. the POST /api/orders span).
        SpanContext spanContext = Span.current().getSpanContext();
        if (spanContext.isValid()) {
            builder.setHeader("traceparent", String.format("00-%s-%s-%s",
                    spanContext.getTraceId(), spanContext.getSpanId(), spanContext.getTraceFlags().asHex()));
        }

        Message<Object> message = builder.build();
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(message);
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                Span.current().setStatus(StatusCode.ERROR, ex.getMessage()).recordException(ex);
                log.error("Failed to produce message in topic {}: {}", topic, ex.getMessage(), ex);
            } else {
                log.info("Published event to topic={} partition={} offset={}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
        return future;
    }
}
