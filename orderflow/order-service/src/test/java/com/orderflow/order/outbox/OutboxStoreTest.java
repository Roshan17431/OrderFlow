package com.orderflow.order.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OutboxStoreTest {
    @Test
    void retryBackoffIsBoundedAndExponential() {
        assertThat(OutboxStore.backoff(Duration.ofSeconds(2), 1))
                .isEqualTo(Duration.ofSeconds(2));
        assertThat(OutboxStore.backoff(Duration.ofSeconds(2), 3))
                .isEqualTo(Duration.ofSeconds(8));
        assertThat(OutboxStore.backoff(Duration.ofSeconds(2), 99))
                .isEqualTo(Duration.ofSeconds(2048));
    }
}
