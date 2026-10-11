package com.orderflow.scheduler;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.scheduler")
public record SchedulerSupportProperties(@NotBlank String instanceId) {}
