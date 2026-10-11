package com.orderflow.order.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.kafka.topics")
public record KafkaTopicProperties(
        @NotBlank String orderEvents,
        @NotBlank String inventoryEvents,
        @NotBlank String paymentEvents,
        @NotBlank String deliveryEvents,
        @NotBlank String orderEventsDlt,
        @NotBlank String inventoryEventsDlt,
        @NotBlank String paymentEventsDlt,
        @NotBlank String deliveryEventsDlt) {}
