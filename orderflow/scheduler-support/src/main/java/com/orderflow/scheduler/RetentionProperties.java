package com.orderflow.scheduler;
import java.time.Duration;import org.springframework.boot.context.properties.ConfigurationProperties;import org.springframework.boot.context.properties.bind.DefaultValue;
@ConfigurationProperties("app.retention") public record RetentionProperties(@DefaultValue("24h") Duration delay,@DefaultValue("10m") Duration lockDuration,@DefaultValue("90d") Duration processedEvent,@DefaultValue("90d") Duration idempotency,@DefaultValue("30d") Duration publishedOutbox,@DefaultValue("30d") Duration jobHistory,@DefaultValue("1000") int batchSize){}
