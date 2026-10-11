package com.orderflow.payment.service;

import com.orderflow.payment.domain.PaymentStateMachine;
import com.orderflow.payment.domain.PaymentStatus;
import com.orderflow.payment.provider.ProviderPaymentStatus;
import com.orderflow.payment.provider.ProviderUnavailableException;
import com.orderflow.payment.provider.ResilientPaymentProvider;
import com.orderflow.payment.repository.PaymentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentReconciliationService {
    private final PaymentRepository payments;
    private final PaymentStateMachine states;
    private final ResilientPaymentProvider provider;
    private final PaymentEventOutbox events;
    private final PaymentWebhookService webhooks;
    private final Clock clock;
    public PaymentReconciliationService(PaymentRepository payments, PaymentStateMachine states,
            ResilientPaymentProvider provider, PaymentEventOutbox events,
            PaymentWebhookService webhooks, Clock clock) {
        this.payments = payments; this.states = states; this.provider = provider;
        this.events = events; this.webhooks = webhooks; this.clock = clock;
    }

    @Transactional
    public Outcome reconcile(UUID id, String correlation) {
        var payment = payments.findByIdForUpdate(id).orElse(null);
        Instant now = Instant.now(clock);
        if (payment == null || payment.getPaymentDeadline().isAfter(now)
                || !java.util.Set.of(PaymentStatus.PENDING, PaymentStatus.REQUIRES_ACTION,
                PaymentStatus.PROCESSING).contains(payment.getStatus())) return Outcome.UNCHANGED;
        if (payment.getStatus() == PaymentStatus.PROCESSING) {
            if (payment.getProviderPaymentId() == null) return Outcome.UNKNOWN;
            ProviderPaymentStatus status;
            try { status = provider.lookup(payment.getProviderPaymentId()); }
            catch (ProviderUnavailableException exception) { return Outcome.UNKNOWN; }
            if (status.state() == ProviderPaymentStatus.State.SUCCEEDED) {
                webhooks.reconciledSuccess(id, correlation);
                return Outcome.RECONCILED;
            }
            if (status.state() == ProviderPaymentStatus.State.FAILED) {
                webhooks.reconciledFailure(id, status.failureCode() == null ? "PROVIDER_PAYMENT_FAILED" : status.failureCode(), correlation);
                return Outcome.RECONCILED;
            }
            if (status.state() == ProviderPaymentStatus.State.UNKNOWN) return Outcome.UNKNOWN;
        }
        try {
            if (payment.getProviderPaymentId() != null) {
                var result = provider.cancel(payment.getProviderPaymentId(), "deadline-" + payment.getId());
                if (!result.confirmed()) return Outcome.UNKNOWN;
            }
        } catch (ProviderUnavailableException exception) { return Outcome.UNKNOWN; }
        states.transition(payment, PaymentStatus.CANCEL_REQUESTED, now);
        states.transition(payment, PaymentStatus.CANCELLED, now);
        events.cancelled(payment, "PAYMENT_DEADLINE_EXPIRED", now, correlation, null);
        return Outcome.RECONCILED;
    }
    public enum Outcome { RECONCILED, UNKNOWN, UNCHANGED }
}
