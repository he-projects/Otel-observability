package com.observability.commons.config;

import com.observability.commons.exception.ApiError;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Map;

/**
 * Wraps every successful controller response as {@code { "traceId": "...", "data": ... }}.
 * Errors are handled by {@link com.observability.commons.exception.GlobalExceptionHandler}
 * and are not double-wrapped.
 */
@RestControllerAdvice
public class TraceIdResponseAdvice implements ResponseBodyAdvice<Object> {

    /** Always applies — every controller response gets a traceId wrapper. */
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    /**
     * Wraps the controller return value as {@code {traceId, data}} using the active OTel span.
     * Skips null, errors, raw strings, and byte arrays to avoid double-wrapping.
     */
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null || body instanceof ApiError || body instanceof byte[] || body instanceof String) {
            return body;
        }

        SpanContext spanContext = Span.current().getSpanContext();
        if (!spanContext.isValid()) {
            return body;
        }

        return Map.of("traceId", spanContext.getTraceId(), "data", body);
    }
}
