package com.observability.commons.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Standard error body returned by every service.
 * Always includes traceId so clients can correlate failures in Grafana/Tempo.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiError {
    private Integer errorCode;
    private String message;
    private String traceId;
}
