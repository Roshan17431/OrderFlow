package com.orderflow.operations.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.security")
public record OperationsSecurityProperties(@Valid Jwt jwt) {
    public record Jwt(@NotBlank String issuer, @NotBlank String jwkSetUri) {}
}
