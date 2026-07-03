package com.observability.commons.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.springframework.context.annotation.Configuration;

/**
 * Injects W3C trace context (traceparent) into outbound Feign requests
 * so child spans stay linked to the caller span in Tempo.
 */
@Configuration
public class FeignOtelInterceptor implements RequestInterceptor {

    private static final TextMapSetter<RequestTemplate> SETTER =
            (carrier, key, value) -> {
                if (carrier != null && value != null) {
                    carrier.header(key, value);
                }
            };

    /**
     * Called before each Feign request — injects traceparent/tracestate headers
     * so catalog-service continues the same distributed trace.
     */
    @Override
    public void apply(RequestTemplate template) {
        Context context = Context.current();
        GlobalOpenTelemetry.getPropagators()
                .getTextMapPropagator()
                .inject(context, template, SETTER);
    }
}
