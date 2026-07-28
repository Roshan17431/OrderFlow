package com.orderflow.order.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.order")
public record OrderProperties(@Min(1) int maxPageSize) {}
