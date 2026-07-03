package com.observability.commons.exception;

import feign.FeignException;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Maps exceptions to {@link ApiError} with traceId on every error response.
 */
@RestControllerAdvice
@Slf4j
@Order(10)
public class GlobalExceptionHandler {

    /** Handles 404/4xx thrown as {@link ResponseStatusException} (e.g. product not found). */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatusException(ResponseStatusException ex) {
        markSpanAsError(ex);
        return ResponseEntity.status(ex.getStatusCode()).body(buildError(ex.getStatusCode().value(), ex.getReason()));
    }

    /** Handles validation errors such as missing productId in the order request body. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgumentException(IllegalArgumentException ex) {
        markSpanAsError(ex);
        return ResponseEntity.badRequest().body(buildError(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    /** Handles downstream errors from Feign calls; parses message from peer's ApiError JSON. */
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiError> handleFeignException(FeignException ex) {
        int status = ex.status() > 0 ? ex.status() : HttpStatus.INTERNAL_SERVER_ERROR.value();
        String message = ex.responseBody()
                .map(body -> {
                    try {
                        String json = java.nio.charset.StandardCharsets.UTF_8.decode(body).toString();
                        com.fasterxml.jackson.databind.JsonNode node =
                                new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
                        if (node.has("message")) {
                            return node.path("message").asText(ex.getMessage());
                        }
                        if (node.has("data") && node.get("data").has("message")) {
                            return node.path("data").path("message").asText(ex.getMessage());
                        }
                        return ex.getMessage();
                    } catch (Exception e) {
                        return ex.getMessage();
                    }
                })
                .orElse(ex.getMessage());
        markSpanAsError(ex);
        return new ResponseEntity<>(buildError(status, message), HttpStatusCode.valueOf(status));
    }

    /** Handles missing query/path parameters on controller methods. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex) {
        markSpanAsError(ex);
        return ResponseEntity.badRequest()
                .body(buildError(HttpStatus.BAD_REQUEST.value(), "Missing required parameter: " + ex.getParameterName()));
    }

    /** Handles malformed JSON request bodies that cannot be parsed. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex) {
        markSpanAsError(ex);
        return ResponseEntity.badRequest()
                .body(buildError(HttpStatus.BAD_REQUEST.value(), "Invalid JSON request body"));
    }

    /** Catch-all for unexpected exceptions; always returns traceId for support correlation. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        markSpanAsError(ex);
        return ResponseEntity.internalServerError().body(buildError(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred"
        ));
    }

    /** Marks the current OTel span as ERROR and attaches the exception. */
    private void markSpanAsError(Exception ex) {
        Span.current().setStatus(StatusCode.ERROR, ex.getMessage()).recordException(ex);
    }

    /** Builds a standard error body with the current span's traceId. */
    private ApiError buildError(int errorCode, String message) {
        return ApiError.builder()
                .errorCode(errorCode)
                .message(message)
                .traceId(Span.current().getSpanContext().getTraceId())
                .build();
    }
}
