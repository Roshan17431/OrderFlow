package com.orderflow.payment.service;

import com.orderflow.payment.domain.IdempotencyRecord;
import com.orderflow.payment.domain.IdempotencyScope;
import com.orderflow.payment.domain.Payment;
import com.orderflow.payment.domain.PaymentStatus;
import com.orderflow.payment.domain.Refund;
import com.orderflow.payment.dto.RefundResponse;
import com.orderflow.payment.error.PaymentDomainException;
import com.orderflow.payment.provider.ProviderTimeoutException;
import com.orderflow.payment.provider.ProviderUnavailableException;
import com.orderflow.payment.repository.IdempotencyRecordRepository;
import com.orderflow.payment.repository.PaymentRepository;
import com.orderflow.payment.repository.RefundRepository;
import com.orderflow.platform.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefundCommandService {
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final IdempotencyRecordRepository idempotency;
    private final IdempotencyKeyService keys;
    private final RefundCreationSupport support;
    private final PaymentMapper mapper;
    private final Clock clock;

    public RefundCommandService(
            PaymentRepository payments, RefundRepository refunds,
            IdempotencyRecordRepository idempotency, IdempotencyKeyService keys,
            RefundCreationSupport support, PaymentMapper mapper, Clock clock) {
        this.payments = payments;
        this.refunds = refunds;
        this.idempotency = idempotency;
        this.keys = keys;
        this.support = support;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public CommandResult<RefundResponse> create(
            UUID adminId, UUID paymentId, String idempotencyKey,
            BigDecimal amount, String reason) {
        String key = keys.require(idempotencyKey);
        String requestHash = keys.hash(
                "refund:" + paymentId + ":" + amount.stripTrailingZeros().toPlainString()
                        + ":" + reason);
        IdempotencyRecord existing = idempotency
                .findByScopeAndActorIdAndKey(IdempotencyScope.CREATE_REFUND, adminId, key)
                .orElse(null);
        if (existing != null) {
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new PaymentDomainException(
                        HttpStatus.CONFLICT, ErrorCode.REFUND_IDEMPOTENCY_CONFLICT,
                        "The idempotency key was already used for a different refund.");
            }
            if (existing.completed()) {
                Refund refund = refunds.findById(existing.getResourceId())
                        .orElseThrow(this::refundNotFound);
                return CommandResult.success(
                        mapper.refund(refund), existing.getResponseStatus());
            }
            throw new PaymentDomainException(
                    HttpStatus.CONFLICT, ErrorCode.REFUND_IDEMPOTENCY_CONFLICT,
                    "The idempotent refund request is still being processed.");
        }
        Payment payment = payments.findByIdForUpdate(paymentId).orElseThrow(this::paymentNotFound);
        if (!Set.of(PaymentStatus.SUCCEEDED, PaymentStatus.PARTIALLY_REFUNDED)
                .contains(payment.getStatus())) {
            throw new PaymentDomainException(
                    HttpStatus.CONFLICT, ErrorCode.REFUND_STATE_CONFLICT,
                    "Refunds require a successful payment.");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new PaymentDomainException(
                    HttpStatus.BAD_REQUEST, ErrorCode.REFUND_AMOUNT_INVALID,
                    "Refund amount must be positive.");
        }
        if (amount.compareTo(payment.refundableAmount()) > 0) {
            throw new PaymentDomainException(
                    HttpStatus.CONFLICT, ErrorCode.REFUND_AMOUNT_EXCEEDS_AVAILABLE,
                    "Refund amount exceeds the remaining refundable balance.");
        }
        Instant now = Instant.now(clock);
        IdempotencyRecord record = new IdempotencyRecord(
                IdempotencyScope.CREATE_REFUND, adminId, key, requestHash, now);
        idempotency.saveAndFlush(record);
        Refund refund;
        try {
            refund = support.create(
                    payment, amount, reason, adminId,
                    "refund-" + keys.hash(key + ":" + requestHash).substring(0, 32), now);
        } catch (ProviderTimeoutException | ProviderUnavailableException failure) {
            throw new PaymentDomainException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ErrorCode.REFUND_PROVIDER_UNAVAILABLE,
                    "The refund provider is temporarily unavailable.");
        }
        record.complete(refund.getId(), 202, now);
        idempotency.saveAndFlush(record);
        return CommandResult.success(mapper.refund(refund), 202);
    }

    private PaymentDomainException paymentNotFound() {
        return new PaymentDomainException(
                HttpStatus.NOT_FOUND, ErrorCode.PAYMENT_NOT_FOUND,
                "Payment was not found.");
    }
    private PaymentDomainException refundNotFound() {
        return new PaymentDomainException(
                HttpStatus.NOT_FOUND, ErrorCode.REFUND_NOT_FOUND,
                "Refund was not found.");
    }
}
