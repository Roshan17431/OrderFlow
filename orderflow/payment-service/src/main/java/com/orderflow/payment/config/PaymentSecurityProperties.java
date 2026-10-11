package com.orderflow.payment.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.security.jwt")
public record PaymentSecurityProperties(
        @NotBlank String issuer,
        @NotBlank String jwkSetUri) {}
