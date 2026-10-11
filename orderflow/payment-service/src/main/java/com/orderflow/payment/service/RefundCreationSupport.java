package com.orderflow.payment.service;

import com.orderflow.payment.domain.Payment;
import com.orderflow.payment.domain.PaymentStateMachine;
import com.orderflow.payment.domain.PaymentStatus;
import com.orderflow.payment.domain.Refund;
import com.orderflow.payment.provider.ProviderRefundRequest;
import com.orderflow.payment.provider.ProviderRefundResult;
import com.orderflow.payment.provider.ResilientPaymentProvider;
import com.orderflow.payment.repository.RefundRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RefundCreationSupport {
    private final RefundRepository refunds;
    private final PaymentStateMachine stateMachine;
    private final ResilientPaymentProvider provider;
    private final MeterRegistry metrics;

    public RefundCreationSupport(
            RefundRepository refunds,
            PaymentStateMachine stateMachine,
            ResilientPaymentProvider provider,
            MeterRegistry metrics) {
        this.refunds = refunds;
        this.stateMachine = stateMachine;
        this.provider = provider;
        this.metrics = metrics;
    }

    public Refund create(
            Payment payment, BigDecimal amount, String reason, UUID actor,
            String idempotencyReference, Instant now) {
        Refund refund = refunds.save(new Refund(payment, amount, reason, actor, now));
        if (payment.getStatus() != PaymentStatus.REFUND_PENDING) {
            stateMachine.transition(payment, PaymentStatus.REFUND_PENDING, now);
        }
        ProviderRefundResult result = provider.refund(new ProviderRefundRequest(
                refund.getId(), payment.getProviderPaymentId(), amount,
                payment.getCurrency(), idempotencyReference));
        if (!result.processing()) {
            throw new IllegalStateException("Provider did not accept the refund");
        }
        refund.processing(result.providerRefundId(), now);
        metrics.counter(
                "orderflow_payment_refund_requests_total",
                "provider", payment.getProvider()).increment();
        return refund;
    }
}
