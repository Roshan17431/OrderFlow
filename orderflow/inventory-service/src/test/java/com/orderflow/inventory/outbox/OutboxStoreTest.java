package com.orderflow.inventory.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OutboxStoreTest {

    @Test
    void retryBackoffIsBoundedAndExponential() {
        assertThat(OutboxStore.backoff(Duration.ofSeconds(2), 1))
                .isEqualTo(Duration.ofSeconds(2));
        assertThat(OutboxStore.backoff(Duration.ofSeconds(2), 2))
                .isEqualTo(Duration.ofSeconds(4));
        assertThat(OutboxStore.backoff(Duration.ofSeconds(2), 11))
                .isEqualTo(Duration.ofSeconds(2048));
        assertThat(OutboxStore.backoff(Duration.ofSeconds(2), 50))
                .isEqualTo(Duration.ofSeconds(2048));
    }
}
