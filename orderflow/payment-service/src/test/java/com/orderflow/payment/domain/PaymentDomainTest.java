package com.orderflow.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentDomainTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void paymentRequiresPositiveExactMoneyAndCurrency() {
        assertThatThrownBy(() -> payment(BigDecimal.ZERO, "USD"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> payment(BigDecimal.ONE, "usd"))
                .isInstanceOf(IllegalArgumentException.class);
        Payment payment = payment(new BigDecimal("149.97"), "USD");
        assertThat(payment.getAmount()).isEqualByComparingTo("149.97");
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void stateMachineEnforcesAttemptCancellationAndRefundTransitions() {
        PaymentStateMachine machine = new PaymentStateMachine();
        Payment payment = payment(new BigDecimal("100.00"), "USD");
        machine.transition(payment, PaymentStatus.PROCESSING, NOW.plusSeconds(1));
        machine.transition(payment, PaymentStatus.SUCCEEDED, NOW.plusSeconds(2));
        machine.transition(payment, PaymentStatus.REFUND_PENDING, NOW.plusSeconds(3));
        payment.applyRefund(new BigDecimal("25.00"), NOW.plusSeconds(4));
        machine.transition(payment, PaymentStatus.PARTIALLY_REFUNDED, NOW.plusSeconds(4));
        assertThat(payment.refundableAmount()).isEqualByComparingTo("75.00");
        assertThatThrownBy(() -> payment.applyRefund(
                        new BigDecimal("75.01"), NOW.plusSeconds(5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> machine.transition(
                        payment, PaymentStatus.SUCCEEDED, NOW.plusSeconds(5)))
                .isInstanceOf(PaymentStateConflictException.class);
    }

    @Test
    void failedPaymentNeedsANewAttemptBeforeSuccess() {
        PaymentStateMachine machine = new PaymentStateMachine();
        Payment payment = payment(BigDecimal.TEN, "EUR");
        machine.transition(payment, PaymentStatus.PROCESSING, NOW.plusSeconds(1));
        payment.fail(
                "DECLINED", ProviderFailureCategory.DECLINED, NOW.plusSeconds(2));
        machine.transition(payment, PaymentStatus.FAILED, NOW.plusSeconds(2));
        assertThatThrownBy(() -> machine.transition(
                        payment, PaymentStatus.SUCCEEDED, NOW.plusSeconds(3)))
                .isInstanceOf(PaymentStateConflictException.class);
        machine.transition(payment, PaymentStatus.PROCESSING, NOW.plusSeconds(3));
        machine.transition(payment, PaymentStatus.SUCCEEDED, NOW.plusSeconds(4));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void attemptsUsePositiveMonotonicNumbersAndNeverContainTokenFields() {
        PaymentAttempt attempt =
                new PaymentAttempt(UUID.randomUUID(), 1, "SIMULATOR", "test", NOW);
        attempt.submitted("provider-attempt", PaymentAttemptStatus.PROCESSING, NOW);
        assertThat(attempt.getAttemptNumber()).isEqualTo(1);
        assertThat(PaymentAttempt.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .noneMatch(name -> name.toLowerCase().contains("token"));
        assertThatThrownBy(() -> new PaymentAttempt(
                        UUID.randomUUID(), 0, "SIMULATOR", "test", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Payment payment(BigDecimal amount, String currency) {
        return Payment.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                amount, currency, "SIMULATOR", "reference",
                NOW.plusSeconds(600), NOW);
    }
}
