package com.orderflow.gateway.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.http")
public record GatewayHttpProperties(@NotNull DataSize maxRequestSize) {
    public GatewayHttpProperties {
        if (maxRequestSize != null && maxRequestSize.toBytes() < 1024) {
            throw new IllegalArgumentException("max request size must be at least 1 KiB");
        }
    }
}
