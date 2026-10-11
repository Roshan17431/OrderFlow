package com.orderflow.scheduler;
import java.time.Duration;import org.springframework.boot.context.properties.ConfigurationProperties;import org.springframework.boot.context.properties.bind.DefaultValue;
@ConfigurationProperties("app.outbox") public record OutboxMetricsProperties(@DefaultValue("2m") Duration lockTimeout){}
