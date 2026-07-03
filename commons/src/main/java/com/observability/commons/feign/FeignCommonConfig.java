package com.observability.commons.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.codec.Decoder;
import org.springframework.context.annotation.Bean;

/**
 * Registers {@link FeignWrappedResponseDecoder} so Feign clients unwrap
 * {@code {traceId, data}} responses from other services in this project.
 */
public class FeignCommonConfig {

    /** Creates a decoder that strips the {@code traceId} wrapper from peer service responses. */
    @Bean
    public Decoder feignDecoder(ObjectMapper objectMapper) {
        return new FeignWrappedResponseDecoder(objectMapper);
    }
}
