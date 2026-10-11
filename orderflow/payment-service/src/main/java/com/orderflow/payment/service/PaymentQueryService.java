package com.orderflow.payment.service;

import com.orderflow.payment.config.PaymentProperties;
import com.orderflow.payment.domain.Payment;
import com.orderflow.payment.domain.PaymentStatus;
import com.orderflow.payment.dto.AdminPaymentDetailResponse;
import com.orderflow.payment.dto.PageResponse;
import com.orderflow.payment.dto.PaymentAttemptResponse;
import com.orderflow.payment.dto.PaymentResponse;
import com.orderflow.payment.dto.RefundResponse;
import com.orderflow.payment.error.PaymentDomainException;
import com.orderflow.payment.repository.PaymentAttemptRepository;
import com.orderflow.payment.repository.PaymentRepository;
import com.orderflow.payment.repository.PaymentSpecifications;
import com.orderflow.payment.repository.RefundRepository;
import com.orderflow.payment.security.RequestIdentity;
import com.orderflow.platform.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PaymentQueryService {
    private static final Set<String> SORT_FIELDS =
            Set.of("createdAt", "updatedAt", "amount", "status");
    private final PaymentRepository payments;
    private final PaymentAttemptRepository attempts;
    private final RefundRepository refunds;
    private final PaymentMapper mapper;
    private final PaymentProperties properties;

    public PaymentQueryService(
            PaymentRepository payments, PaymentAttemptRepository attempts,
            RefundRepository refunds, PaymentMapper mapper,
            PaymentProperties properties) {
        this.payments = payments;
        this.attempts = attempts;
        this.refunds = refunds;
        this.mapper = mapper;
        this.properties = properties;
    }

    public PaymentResponse byOrder(RequestIdentity actor, UUID orderId) {
        Payment payment = payments.findByOrderId(orderId).orElseThrow(this::notFound);
        requireAccess(actor, payment);
        return mapper.payment(payment);
    }

    public PaymentResponse byId(RequestIdentity actor, UUID paymentId) {
        Payment payment = payments.findById(paymentId).orElseThrow(this::notFound);
        requireAccess(actor, payment);
        return mapper.payment(payment);
    }

    public PageResponse<PaymentAttemptResponse> attempts(
            RequestIdentity actor, UUID paymentId, int page, int size) {
        Payment payment = payments.findById(paymentId).orElseThrow(this::notFound);
        requireAccess(actor, payment);
        Page<PaymentAttemptResponse> result = attempts
                .findByPaymentId(
                        paymentId,
                        PageRequest.of(
                                validPage(page), validSize(size),
                                Sort.by("attemptNumber").descending()))
                .map(mapper::attempt);
        return PageResponse.from(result);
    }

    public PageResponse<PaymentResponse> search(
            UUID orderId, UUID customerId, PaymentStatus status, String provider,
            String currency, Instant createdFrom, Instant createdTo,
            BigDecimal minAmount, BigDecimal maxAmount,
            String sortField, Sort.Direction direction, int page, int size) {
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw badRequest("createdFrom cannot be after createdTo.");
        }
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw badRequest("minAmount cannot exceed maxAmount.");
        }
        if (!SORT_FIELDS.contains(sortField)) {
            throw badRequest("Unsupported payment sort field.");
        }
        Page<PaymentResponse> result = payments.findAll(
                        PaymentSpecifications.filters(
                                orderId, customerId, status, provider, currency,
                                createdFrom, createdTo, minAmount, maxAmount),
                        PageRequest.of(
                                validPage(page), validSize(size),
                                Sort.by(direction, sortField)))
                .map(mapper::payment);
        return PageResponse.from(result);
    }

    public AdminPaymentDetailResponse adminDetail(UUID paymentId) {
        Payment payment = payments.findById(paymentId).orElseThrow(this::notFound);
        var attemptResponses = attempts.findByPaymentId(
                        paymentId,
                        PageRequest.of(0, properties.maxPageSize(),
                                Sort.by("attemptNumber").ascending()))
                .map(mapper::attempt).getContent();
        var refundResponses = refunds.findByPaymentId(
                        paymentId,
                        PageRequest.of(0, properties.maxPageSize(),
                                Sort.by("createdAt").ascending()))
                .map(mapper::refund).getContent();
        return new AdminPaymentDetailResponse(
                mapper.payment(payment), attemptResponses, refundResponses);
    }

    public PageResponse<RefundResponse> refunds(UUID paymentId, int page, int size) {
        if (!payments.existsById(paymentId)) throw notFound();
        return PageResponse.from(refunds.findByPaymentId(
                        paymentId,
                        PageRequest.of(validPage(page), validSize(size),
                                Sort.by("createdAt").descending()))
                .map(mapper::refund));
    }

    private int validPage(int page) {
        if (page < 0) throw badRequest("Page cannot be negative.");
        return page;
    }
    private int validSize(int size) {
        if (size < 1 || size > properties.maxPageSize()) {
            throw badRequest("Page size is outside the allowed range.");
        }
        return size;
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
    private PaymentDomainException badRequest(String message) {
        return new PaymentDomainException(
                HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, message);
    }
}
