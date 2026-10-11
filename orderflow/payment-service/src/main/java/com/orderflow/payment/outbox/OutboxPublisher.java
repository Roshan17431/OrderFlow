package com.orderflow.payment.outbox;

import com.orderflow.payment.config.OutboxProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxStore store;
    private final KafkaTemplate<String, String> kafka;
    private final OutboxProperties properties;
    private final MeterRegistry metrics;
    private final String owner = UUID.randomUUID().toString();

    public OutboxPublisher(
            OutboxStore store, KafkaTemplate<String, String> kafka,
            OutboxProperties properties, MeterRegistry metrics) {
        this.store = store;
        this.kafka = kafka;
        this.properties = properties;
        this.metrics = metrics;
        Gauge.builder("orderflow_outbox_backlog", store, OutboxStore::backlog)
                .tag("service", "payment-service").register(metrics);
        Gauge.builder("orderflow_outbox_failed", store, OutboxStore::failedCount)
                .tag("service", "payment-service").register(metrics);
    }

    @Scheduled(
            fixedDelayString = "${app.outbox.poll-delay:500ms}",
            initialDelayString = "${app.outbox.poll-delay:500ms}")
    public void publishDue() {
        store.claim(owner).forEach(this::publish);
    }

    private void publish(OutboxMessage message) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(message.topic(), message.messageKey(), message.payload());
        record.headers().add(new RecordHeader(
                "eventType", message.eventType().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader(
                "eventVersion",
                Integer.toString(message.eventVersion()).getBytes(StandardCharsets.UTF_8)));
        try {
            kafka.send(record).get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            store.published(message.id(), owner);
            metrics.counter(
                    "orderflow_outbox_published_total", "service", "payment-service",
                    "eventType", message.eventType()).increment();
        } catch (Exception exception) {
            store.failed(message, owner, exception.getClass().getSimpleName());
            metrics.counter(
                    "orderflow_outbox_publication_failures_total",
                    "service", "payment-service", "eventType", message.eventType()).increment();
            LOGGER.warn(
                    "Payment outbox publication failed eventId={} eventType={}",
                    message.id(), message.eventType());
        }
    }
}
