package com.orderflow.payment.service;

import com.orderflow.payment.domain.IdempotencyRecord;
import com.orderflow.payment.domain.IdempotencyScope;
import com.orderflow.payment.domain.Payment;
import com.orderflow.payment.domain.PaymentAttempt;
import com.orderflow.payment.domain.PaymentAttemptStatus;
import com.orderflow.payment.domain.PaymentStateMachine;
import com.orderflow.payment.domain.PaymentStatus;
import com.orderflow.payment.domain.ProviderFailureCategory;
import com.orderflow.payment.dto.PaymentResponse;
import com.orderflow.payment.error.PaymentDomainException;
import com.orderflow.payment.provider.ProviderCancellationResult;
import com.orderflow.payment.provider.ProviderPaymentRequest;
import com.orderflow.payment.provider.ProviderPaymentResult;
import com.orderflow.payment.provider.ProviderTimeoutException;
import com.orderflow.payment.provider.ProviderUnavailableException;
import com.orderflow.payment.provider.ResilientPaymentProvider;
import com.orderflow.payment.provider.SimulatorPaymentProvider;
import com.orderflow.payment.repository.IdempotencyRecordRepository;
import com.orderflow.payment.repository.PaymentAttemptRepository;
import com.orderflow.payment.repository.PaymentRepository;
import com.orderflow.payment.security.RequestIdentity;
import com.orderflow.platform.error.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentCommandService {
    private final PaymentRepository payments;
    private final PaymentAttemptRepository attempts;
    private final IdempotencyRecordRepository idempotency;
    private final IdempotencyKeyService keys;
    private final PaymentStateMachine stateMachine;
    private final ResilientPaymentProvider provider;
    private final PaymentEventOutbox events;
    private final PaymentMapper mapper;
    private final MeterRegistry metrics;
    private final Clock clock;

    public PaymentCommandService(
            PaymentRepository payments,
            PaymentAttemptRepository attempts,
            IdempotencyRecordRepository idempotency,
            IdempotencyKeyService keys,
            PaymentStateMachine stateMachine,
            ResilientPaymentProvider provider,
            PaymentEventOutbox events,
            PaymentMapper mapper,
            MeterRegistry metrics,
            Clock clock) {
        this.payments = payments;
        this.attempts = attempts;
        this.idempotency = idempotency;
        this.keys = keys;
        this.stateMachine = stateMachine;
        this.provider = provider;
        this.events = events;
        this.mapper = mapper;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public CommandResult<PaymentResponse> confirm(
            RequestIdentity actor, UUID orderId, String idempotencyKey,
            String token, String correlationId) {
        String key = keys.require(idempotencyKey);
        if (!SimulatorPaymentProvider.supportedToken(token)) {
            throw new PaymentDomainException(
                    HttpStatus.BAD_REQUEST, ErrorCode.PAYMENT_METHOD_TOKEN_INVALID,
                    "Use a supported opaque payment-method token; raw card data is not accepted.");
        }
        String requestHash = keys.hash(
                "confirm:" + orderId + ":" + keys.hash("opaque-token:" + token));
        IdempotencyRecord existing = idempotency
                .findByScopeAndActorIdAndKey(IdempotencyScope.CONFIRM_PAYMENT, actor.userId(), key)
                .orElse(null);
        if (existing != null) {
            requireSameRequest(existing, requestHash, ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT);
            if (existing.completed()) {
                Payment payment = requirePayment(existing.getResourceId());
                requireAccess(actor, payment);
                return replay(payment, existing.getResponseStatus());
            }
            throw new PaymentDomainException(
                    HttpStatus.CONFLICT, ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT,
                    "The idempotent payment request is still being processed.");
        }
        Payment payment = payments.findByOrderIdForUpdate(orderId).orElseThrow(this::notFound);
        requireAccess(actor, payment);
        existing = idempotency
                .findByScopeAndActorIdAndKey(
                        IdempotencyScope.CONFIRM_PAYMENT, actor.userId(), key)
                .orElse(null);
        if (existing != null) {
            requireSameRequest(existing, requestHash, ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT);
            if (existing.completed()) {
                return replay(payment, existing.getResponseStatus());
            }
            throw new PaymentDomainException(
                    HttpStatus.CONFLICT, ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT,
                    "The idempotent payment request is still being processed.");
        }
        Instant now = Instant.now(clock);
        IdempotencyRecord record = new IdempotencyRecord(
                IdempotencyScope.CONFIRM_PAYMENT, actor.userId(), key, requestHash, now);
        idempotency.saveAndFlush(record);

        if (payment.getStatus() == PaymentStatus.SUCCEEDED
                || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED) {
            complete(record, payment.getId(), 200, now);
            return CommandResult.success(mapper.payment(payment), 200);
        }
        if (payment.getStatus() == PaymentStatus.CANCELLED
                || payment.getStatus() == PaymentStatus.CANCEL_REQUESTED
                || payment.getStatus() == PaymentStatus.REFUNDED
                || payment.getStatus() == PaymentStatus.REFUND_PENDING) {
            throw new PaymentDomainException(
                    HttpStatus.CONFLICT, ErrorCode.PAYMENT_STATE_CONFLICT,
                    "This payment can no longer be confirmed.");
        }
        if (!payment.getPaymentDeadline().isAfter(now)) {
            throw new PaymentDomainException(
                    HttpStatus.CONFLICT, ErrorCode.PAYMENT_DEADLINE_EXPIRED,
                    "The payment deadline has expired.");
        }

        int attemptNumber = Math.toIntExact(attempts.countByPaymentId(payment.getId()) + 1);
        PaymentAttempt attempt = attempts.save(new PaymentAttempt(
                payment.getId(), attemptNumber, payment.getProvider(), correlationId, now));
        stateMachine.transition(payment, PaymentStatus.PROCESSING, now);
        metrics.counter(
                "orderflow_payment_confirmation_attempts_total",
                "provider", payment.getProvider()).increment();
        ProviderPaymentResult result;
        try {
            result = provider.confirm(
                    request(payment, "confirm-" + recordHashReference(key, requestHash)), token);
        } catch (ProviderTimeoutException exception) {
            return providerFailure(
                    payment, attempt, record, "PROVIDER_TIMEOUT",
                    ProviderFailureCategory.TIMEOUT, true, ErrorCode.PAYMENT_PROVIDER_TIMEOUT,
                    correlationId, now);
        } catch (ProviderUnavailableException exception) {
            return providerFailure(
                    payment, attempt, record, "PROVIDER_TEMPORARILY_UNAVAILABLE",
                    ProviderFailureCategory.TEMPORARY_PROVIDER_FAILURE, true,
                    ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE, correlationId, now);
        }
        payment.bindProviderPayment(result.providerPaymentId(), now);
        if (result.result() == ProviderPaymentResult.Result.DECLINED) {
            attempt.fail(
                    result.failureCode(), result.failureCategory(), result.retryable(), now);
            payment.fail(result.failureCode(), result.failureCategory(), now);
            stateMachine.transition(payment, PaymentStatus.FAILED, now);
            events.failed(payment, result.retryable(), now, correlationId, null);
            complete(record, payment.getId(), 402, now);
            metrics.counter(
                    "orderflow_payment_declines_total",
                    "provider", payment.getProvider()).increment();
            return CommandResult.error(
                    mapper.payment(payment), 402, ErrorCode.PAYMENT_DECLINED,
                    "The payment was declined.");
        }
        PaymentStatus next = result.result() == ProviderPaymentResult.Result.REQUIRES_ACTION
                ? PaymentStatus.REQUIRES_ACTION : PaymentStatus.PROCESSING;
        if (next != PaymentStatus.PROCESSING) {
            stateMachine.transition(payment, next, now);
        }
        attempt.submitted(
                result.providerAttemptId(),
                next == PaymentStatus.PROCESSING
                        ? PaymentAttemptStatus.PROCESSING : PaymentAttemptStatus.SUBMITTED,
                now);
        complete(record, payment.getId(), 202, now);
        if (next == PaymentStatus.REQUIRES_ACTION) {
            metrics.counter(
                    "orderflow_payment_requires_action_total",
                    "provider", payment.getProvider()).increment();
        }
        return CommandResult.success(mapper.payment(payment), 202);
    }

    @Transactional
    public CommandResult<PaymentResponse> cancel(
            RequestIdentity actor, UUID paymentId, String idempotencyKey,
            String reason, String correlationId) {
        String key = keys.require(idempotencyKey);
        String requestHash = keys.hash("cancel:" + paymentId + ":" + reason);
        IdempotencyRecord existing = idempotency
                .findByScopeAndActorIdAndKey(IdempotencyScope.CANCEL_PAYMENT, actor.userId(), key)
                .orElse(null);
        if (existing != null) {
            requireSameRequest(existing, requestHash, ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT);
            if (existing.completed()) {
                Payment payment = requirePayment(existing.getResourceId());
                requireAccess(actor, payment);
                return CommandResult.success(
                        mapper.payment(payment), existing.getResponseStatus());
            }
            throw new PaymentDomainException(
                    HttpStatus.CONFLICT, ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT,
                    "The idempotent cancellation request is still being processed.");
        }
        Payment payment = payments.findByIdForUpdate(paymentId).orElseThrow(this::notFound);
        requireAccess(actor, payment);
        existing = idempotency
                .findByScopeAndActorIdAndKey(
                        IdempotencyScope.CANCEL_PAYMENT, actor.userId(), key)
                .orElse(null);
        if (existing != null) {
            requireSameRequest(existing, requestHash, ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT);
            if (existing.completed()) {
                return CommandResult.success(
                        mapper.payment(payment), existing.getResponseStatus());
            }
            throw new PaymentDomainException(
                    HttpStatus.CONFLICT, ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT,
                    "The idempotent cancellation request is still being processed.");
        }
        Instant now = Instant.now(clock);
        IdempotencyRecord record = new IdempotencyRecord(
                IdempotencyScope.CANCEL_PAYMENT, actor.userId(), key, requestHash, now);
        idempotency.saveAndFlush(record);
        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            complete(record, paymentId, 200, now);
            return CommandResult.success(mapper.payment(payment), 200);
        }
        if (!java.util.Set.of(
                        PaymentStatus.PENDING,
                        PaymentStatus.REQUIRES_ACTION,
                        PaymentStatus.PROCESSING)
                .contains(payment.getStatus())) {
            throw new PaymentDomainException(
                    HttpStatus.CONFLICT, ErrorCode.PAYMENT_STATE_CONFLICT,
                    payment.getStatus() == PaymentStatus.SUCCEEDED
                            ? "A successful payment must use the refund flow."
                            : "This payment cannot be cancelled.");
        }
        stateMachine.transition(payment, PaymentStatus.CANCEL_REQUESTED, now);
        ProviderCancellationResult result =
                provider.cancel(payment.getProviderPaymentId(), "cancel-" + recordHashReference(
                        key, requestHash));
        int status = 202;
        if (result.confirmed()) {
            stateMachine.transition(payment, PaymentStatus.CANCELLED, now);
            attempts.findFirstByPaymentIdOrderByAttemptNumberDesc(paymentId)
                    .ifPresent(attempt -> attempt.cancel(now));
            events.cancelled(payment, reason, now, correlationId, null);
            status = 200;
            metrics.counter(
                    "orderflow_payment_cancellations_total",
                    "provider", payment.getProvider()).increment();
        }
        complete(record, paymentId, status, now);
        return CommandResult.success(mapper.payment(payment), status);
    }

    private CommandResult<PaymentResponse> providerFailure(
            Payment payment, PaymentAttempt attempt, IdempotencyRecord record,
            String code, ProviderFailureCategory category, boolean retryable,
            ErrorCode errorCode, String correlationId, Instant now) {
        attempt.fail(code, category, retryable, now);
        payment.fail(code, category, now);
        stateMachine.transition(payment, PaymentStatus.FAILED, now);
        events.failed(payment, retryable, now, correlationId, null);
        complete(record, payment.getId(), 503, now);
        metrics.counter(
                category == ProviderFailureCategory.TIMEOUT
                        ? "orderflow_payment_timeouts_total"
                        : "orderflow_payment_temporary_failures_total",
                "provider", payment.getProvider()).increment();
        return CommandResult.error(
                mapper.payment(payment), 503, errorCode,
                "The payment provider is temporarily unavailable.");
    }

    private CommandResult<PaymentResponse> replay(Payment payment, int status) {
        if (status == 402) {
            return CommandResult.error(
                    mapper.payment(payment), status, ErrorCode.PAYMENT_DECLINED,
                    "The payment was declined.");
        }
        if (status == 503) {
            return CommandResult.error(
                    mapper.payment(payment), status, ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE,
                    "The payment provider is temporarily unavailable.");
        }
        return CommandResult.success(mapper.payment(payment), status);
    }

    private ProviderPaymentRequest request(Payment payment, String reference) {
        return new ProviderPaymentRequest(
                payment.getId(), payment.getOrderId(), payment.getAmount(),
                payment.getCurrency(), reference);
    }

    private String recordHashReference(String key, String requestHash) {
        return keys.hash(key + ":" + requestHash).substring(0, 32);
    }

    private void complete(
            IdempotencyRecord record, UUID resourceId, int status, Instant now) {
        record.complete(resourceId, status, now);
        idempotency.saveAndFlush(record);
    }

    private void requireSameRequest(
            IdempotencyRecord record, String hash, ErrorCode code) {
        if (!record.getRequestHash().equals(hash)) {
            throw new PaymentDomainException(
                    HttpStatus.CONFLICT, code,
                    "The idempotency key was already used for a different request.");
        }
    }

    private Payment requirePayment(UUID id) {
        return payments.findById(id).orElseThrow(this::notFound);
    }

    private void requireAccess(RequestIdentity actor, Payment payment) {
        if (!actor.admin() && !actor.userId().equals(payment.getCustomerId())) {
            throw notFound();
        }
    }

    private PaymentDomainException notFound() {
        return new PaymentDomainException(
                HttpStatus.NOT_FOUND, ErrorCode.PAYMENT_NOT_FOUND,
                "Payment was not found.");
    }
}
