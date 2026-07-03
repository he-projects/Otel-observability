package com.observability.commons.kafka;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.stream.StreamSupport;

/**
 * Starts a CONSUMER span per Kafka record and links it to the producer trace
 * via W3C trace context extracted from message headers.
 */
@Component
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaRecordInterceptor implements RecordInterceptor<String, Object> {

    private final ThreadLocal<Span> spanHolder = new ThreadLocal<>();
    private final ThreadLocal<Scope> scopeHolder = new ThreadLocal<>();

    private final TextMapGetter<ConsumerRecord<String, Object>> getter = new TextMapGetter<>() {
        /** Lists Kafka header keys so OTel can extract traceparent. */
        @Override
        public Iterable<String> keys(ConsumerRecord<String, Object> carrier) {
            return StreamSupport.stream(carrier.headers().spliterator(), false)
                    .map(Header::key)
                    .toList();
        }

        /** Reads a single header value (e.g. traceparent) from the Kafka record. */
        @Override
        public String get(ConsumerRecord<String, Object> carrier, String key) {
            Header header = carrier.headers().lastHeader(key);
            if (header == null) {
                return null;
            }
            return new String(header.value(), StandardCharsets.UTF_8);
        }
    };

    /**
     * Runs before the @KafkaListener method — extracts traceparent from headers
     * and starts a CONSUMER span linked to the producer's HTTP trace.
     */
    @Override
    public ConsumerRecord<String, Object> intercept(ConsumerRecord<String, Object> record,
                                                    Consumer<String, Object> consumer) {
        Context context = GlobalOpenTelemetry.getPropagators()
                .getTextMapPropagator()
                .extract(Context.current(), record, getter);

        Span span = GlobalOpenTelemetry.getTracer("kafka-consumer")
                .spanBuilder(record.topic())
                .setParent(context)
                .setSpanKind(SpanKind.CONSUMER)
                .startSpan();

        Scope scope = span.makeCurrent();
        spanHolder.set(span);
        scopeHolder.set(scope);
        return record;
    }

    /** Marks the consumer span as ERROR when the listener throws an exception. */
    @Override
    public void failure(ConsumerRecord<String, Object> record, Exception exception,
                        Consumer<String, Object> consumer) {
        Span span = spanHolder.get();
        if (span != null) {
            span.recordException(exception);
            span.setStatus(StatusCode.ERROR);
        }
    }

    /** Ends the consumer span and closes the OTel scope after each record is processed. */
    @Override
    public void afterRecord(ConsumerRecord<String, Object> record, Consumer<String, Object> consumer) {
        Scope scope = scopeHolder.get();
        if (scope != null) {
            scope.close();
            scopeHolder.remove();
        }

        Span span = spanHolder.get();
        if (span != null) {
            span.end();
            spanHolder.remove();
        }
    }
}
