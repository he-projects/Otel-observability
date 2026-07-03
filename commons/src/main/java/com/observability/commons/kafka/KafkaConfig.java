package com.observability.commons.kafka;

import com.observability.commons.kafka.serialization.KafkaJsonDeserializer;
import com.observability.commons.kafka.serialization.KafkaJsonSerializer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared Kafka producer/consumer configuration.
 * Registers JSON serde, reconnect backoff, and the listener factory with trace propagation.
 */
@Configuration
@EnableKafka
@Slf4j
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConfig {

    @Value("${kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Listener factory with {@link KafkaRecordInterceptor} for trace propagation
     * and a common error handler that records failures on the active span.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            KafkaRecordInterceptor kafkaRecordInterceptor
    ) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setRecordInterceptor(kafkaRecordInterceptor);
        factory.setConcurrency(1);
        factory.setCommonErrorHandler(new CommonErrorHandler() {
            /** Records unhandled consumer errors on the active OTel span. */
            @Override
            public void handleOtherException(Exception thrownException, Consumer<?, ?> consumer,
                                             MessageListenerContainer container, boolean batchListener) {
                Span.current().setStatus(StatusCode.ERROR, thrownException.getMessage())
                        .recordException(thrownException);
                log.error("Kafka unhandled error: {}", thrownException.getMessage(), thrownException);
            }
        });
        return factory;
    }

    /** Consumer factory with JSON deserializer and shared consumer group. */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "otel-observability");
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, KafkaJsonDeserializer.class);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        applyReconnectBackoff(config);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    /** Producer factory with JSON serializer for event payloads. */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaJsonSerializer.class);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        applyReconnectBackoff(config);
        return new DefaultKafkaProducerFactory<>(config);
    }

    /**
     * Slower reconnect/backoff so a down broker does not flood the console.
     * When Kafka is healthy, these settings have no visible effect.
     */
    private void applyReconnectBackoff(Map<String, Object> config) {
        config.put(ConsumerConfig.RECONNECT_BACKOFF_MS_CONFIG, 5_000);
        config.put(ConsumerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, 60_000);
        config.put(ConsumerConfig.RETRY_BACKOFF_MS_CONFIG, 5_000);
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
    }

    /** Spring Kafka template used by {@link com.observability.commons.kafka.producer.KafkaEventProducer}. */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
