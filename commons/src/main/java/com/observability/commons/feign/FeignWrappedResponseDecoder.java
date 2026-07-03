package com.observability.commons.feign;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.Decoder;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;

/**
 * Unwraps {@code { "traceId": "...", "data": ... }} Feign responses
 * and deserializes only the {@code data} field into the target type.
 */
public class FeignWrappedResponseDecoder implements Decoder {

    private final ObjectMapper objectMapper;

    /** Stores the Jackson mapper used to unwrap {@code {traceId, data}} Feign responses. */
    public FeignWrappedResponseDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Reads the JSON body, extracts the {@code data} field when present,
     * and converts it to the Feign method's declared return type.
     */
    @Override
    public Object decode(Response response, Type type) throws IOException {
        if (response.body() == null) {
            return null;
        }

        JavaType mapType = objectMapper.getTypeFactory()
                .constructMapType(Map.class, String.class, Object.class);
        Map<String, Object> map = objectMapper.readValue(response.body().asInputStream(), mapType);

        Object data = map.getOrDefault("data", map);
        JavaType targetType = objectMapper.getTypeFactory().constructType(type);
        return objectMapper.convertValue(data, targetType);
    }
}
