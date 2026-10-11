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
}
