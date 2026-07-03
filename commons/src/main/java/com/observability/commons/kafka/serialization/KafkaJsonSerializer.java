package com.observability.commons.kafka.serialization;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;
import java.util.Objects;

import static com.observability.commons.constant.KafkaConstants.CLASS_PATH_KEY;

/** JSON serializer that embeds the event class name for polymorphic deserialization. */
public class KafkaJsonSerializer implements Serializer<Object> {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Converts the event object to JSON bytes and embeds the class name
     * so the consumer can deserialize to the correct concrete type.
     */
    @Override
    public byte[] serialize(String topic, Object data) {
        if (Objects.isNull(data)) {
            return null;
        }
        try {
            Map<String, Object> map = mapper.convertValue(data, new TypeReference<>() {});
            map.put(CLASS_PATH_KEY, data.getClass().getName());
            return mapper.writeValueAsBytes(map);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing Kafka message", e);
        }
    }
}
