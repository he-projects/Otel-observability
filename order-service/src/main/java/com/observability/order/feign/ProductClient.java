package com.observability.order.feign;

import com.observability.commons.feign.FeignCommonConfig;
import com.observability.commons.feign.FeignOtelInterceptor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;

/**
 * Declarative HTTP client for catalog-service.
 * OTel auto-instruments Feign; {@link FeignOtelInterceptor} propagates trace context.
 * {@link FeignCommonConfig} unwraps {@code {traceId, data}} responses.
 */
@FeignClient(
        name = "catalog-service",
        url = "${feign.catalog-service-url}",
        configuration = {FeignOtelInterceptor.class, FeignCommonConfig.class}
)
public interface ProductClient {

    /** GET /api/products — used by health-check to verify Feign connectivity. */
    @GetMapping("/api/products")
    List<Map<String, Object>> listProducts();

    /** GET /api/products/{id} — fetches product name and price when creating an order. */
    @GetMapping("/api/products/{id}")
    Map<String, Object> getProduct(@PathVariable("id") int id);
}
