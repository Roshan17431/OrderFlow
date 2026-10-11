package com.orderflow.delivery.config;
import jakarta.validation.constraints.Max;import jakarta.validation.constraints.Min;import jakarta.validation.constraints.NotBlank;import org.springframework.boot.context.properties.ConfigurationProperties;import org.springframework.validation.annotation.Validated;
@Validated @ConfigurationProperties("app.delivery") public record DeliveryProperties(@NotBlank String assignmentStrategy,@Min(1) @Max(500) int maxPageSize){}
