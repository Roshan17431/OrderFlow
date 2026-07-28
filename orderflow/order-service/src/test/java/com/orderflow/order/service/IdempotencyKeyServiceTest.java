package com.orderflow.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderflow.order.error.OrderDomainException;
import org.junit.jupiter.api.Test;

class IdempotencyKeyServiceTest {

    private final IdempotencyKeyService service = new IdempotencyKeyService();

    @Test
    void validatesSafeKeysAndHashesNormalizedRequestsDeterministically() {
        assertThat(service.validate("create-order_123")).isEqualTo("create-order_123");
        assertThat(service.hash("expectedCartVersion=4"))
                .isEqualTo(service.hash("expectedCartVersion=4"))
                .isNotEqualTo(service.hash("expectedCartVersion=5"));
        assertThatThrownBy(() -> service.validate(null))
                .isInstanceOf(OrderDomainException.class);
        assertThatThrownBy(() -> service.validate("contains spaces"))
                .isInstanceOf(OrderDomainException.class);
        assertThatThrownBy(() -> service.validate("x".repeat(129)))
                .isInstanceOf(OrderDomainException.class);
    }
}
