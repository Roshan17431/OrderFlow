package com.orderflow.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orderflow.payment.error.PaymentDomainException;
import org.junit.jupiter.api.Test;

class IdempotencyKeyServiceTest {
    private final IdempotencyKeyService service = new IdempotencyKeyService();

    @Test
    void validatesSafeKeysAndHashesNormalizedInputDeterministically() {
        assertThat(service.require("confirm:abc-123")).isEqualTo("confirm:abc-123");
        assertThat(service.hash("same")).isEqualTo(service.hash("same")).hasSize(64);
        assertThat(service.hash("same")).isNotEqualTo(service.hash("different"));
        assertThatThrownBy(() -> service.require("unsafe key"))
                .isInstanceOf(PaymentDomainException.class);
        assertThatThrownBy(() -> service.require(""))
                .isInstanceOf(PaymentDomainException.class);
    }
}
