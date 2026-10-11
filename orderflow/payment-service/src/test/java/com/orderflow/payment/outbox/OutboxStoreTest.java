package com.orderflow.payment.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OutboxStoreTest {
    @Test
    void retryDelayIsBoundedExponential() {
        assertThat(OutboxStore.backoff(Duration.ofSeconds(2), 1))
                .isEqualTo(Duration.ofSeconds(2));
        assertThat(OutboxStore.backoff(Duration.ofSeconds(2), 4))
                .isEqualTo(Duration.ofSeconds(16));
        assertThat(OutboxStore.backoff(Duration.ofSeconds(2), 50))
                .isEqualTo(Duration.ofSeconds(2048));
    }
}
