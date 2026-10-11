package com.orderflow.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.orderflow.notification.config.NotificationProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SseRegistryTest {
    @Test
    void connectionsAreUserScopedAndBounded() {
        NotificationProperties properties = new NotificationProperties(
                "simulator", "simulator",
                new NotificationProperties.Async(1, 2, 10), Duration.ofMinutes(1), 100);
        SseRegistry registry = new SseRegistry(properties, new SimpleMeterRegistry());
        UUID firstUser = UUID.randomUUID();
        UUID secondUser = UUID.randomUUID();

        for (int connection = 0; connection < 6; connection++) {
            registry.connect(firstUser);
        }
        registry.connect(secondUser);

        assertThat(registry.connections(firstUser)).isEqualTo(5);
        assertThat(registry.connections(secondUser)).isEqualTo(1);
    }
    @Test
    void simultaneousConnectionsCannotExceedCapOrLoseGaugeAccounting() throws Exception {
        var properties = new NotificationProperties("simulator", "simulator",
                new NotificationProperties.Async(1, 2, 10), Duration.ofMinutes(1), 100);
        var metrics = new SimpleMeterRegistry();
        var registry = new SseRegistry(properties, metrics);
        UUID user = UUID.randomUUID();
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(16)) {
            var tasks = java.util.stream.IntStream.range(0, 100)
                    .<java.util.concurrent.Callable<Void>>mapToObj(i -> () -> { registry.connect(user); return null; })
                    .toList();
            for (var result : executor.invokeAll(tasks)) result.get();
        }
        assertThat(registry.connections(user)).isLessThanOrEqualTo(5);
        assertThat(metrics.get("notification_sse_connections").gauge().value())
                .isEqualTo(registry.connections(user));
        registry.shutdown();
        assertThat(metrics.get("notification_sse_connections").gauge().value()).isZero();
    }

}
