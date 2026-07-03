package com.observability.commons.kafka.serialization;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Deserializer;

import java.util.Map;
import java.util.Objects;

import static com.observability.commons.constant.KafkaConstants.CLASS_PATH_KEY;

/** JSON deserializer that restores the concrete event type from the embedded class name. */
public class KafkaJsonDeserializer implements Deserializer<Object> {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Reads JSON bytes, loads the class from the embedded classPath field,
     * and converts the map back to the original event type.
     */
    @Override
    public Object deserialize(String topic, byte[] data) {
        if (Objects.isNull(data) || data.length == 0) {
            return null;
        }
        try {
            Map<String, Object> map = mapper.readValue(data, new TypeReference<>() {});
            String classPath = map.get(CLASS_PATH_KEY).toString();
            Class<?> clazz = Class.forName(classPath);
            map.remove(CLASS_PATH_KEY);
            return mapper.convertValue(map, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Error deserializing Kafka message", e);
        }
    }
}
