package com.orderflow.inventory.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.security.jwt")
public record InventorySecurityProperties(
        @NotBlank String issuer, @NotBlank String jwkSetUri) {}
