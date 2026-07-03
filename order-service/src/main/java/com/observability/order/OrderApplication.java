package com.observability.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Entry point for order-service (port 8081).
 * Enables Feign clients and scans {@code com.observability} for shared commons beans.
 */
@SpringBootApplication(scanBasePackages = {"com.observability"})
@EnableFeignClients(basePackages = "com.observability.order.feign")
public class OrderApplication {

    /** Starts the Spring Boot application. */
    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
