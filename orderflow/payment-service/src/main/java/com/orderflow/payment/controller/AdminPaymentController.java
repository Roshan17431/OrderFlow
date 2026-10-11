package com.orderflow.payment.controller;

import com.orderflow.payment.domain.PaymentStatus;
import com.orderflow.payment.dto.AdminPaymentDetailResponse;
import com.orderflow.payment.dto.CreateRefundRequest;
import com.orderflow.payment.dto.PageResponse;
import com.orderflow.payment.dto.PaymentResponse;
import com.orderflow.payment.dto.RefundResponse;
import com.orderflow.payment.service.CommandResult;
import com.orderflow.payment.service.PaymentQueryService;
import com.orderflow.payment.service.RefundCommandService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/payments")
public class AdminPaymentController {
    private final PaymentQueryService queries;
    private final RefundCommandService refunds;

    public AdminPaymentController(PaymentQueryService queries, RefundCommandService refunds) {
        this.queries = queries;
        this.refunds = refunds;
    }

    @GetMapping
    PageResponse<PaymentResponse> search(
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queries.search(
                orderId, customerId, status, provider, currency,
                createdFrom, createdTo, minAmount, maxAmount,
                sort, direction, page, size);
    }

    @GetMapping("/{paymentId}")
    AdminPaymentDetailResponse detail(@PathVariable UUID paymentId) {
        return queries.adminDetail(paymentId);
    }

    @PostMapping("/{paymentId}/refunds")
    ResponseEntity<RefundResponse> refund(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID paymentId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody CreateRefundRequest body) {
        CommandResult<RefundResponse> result = refunds.create(
                UUID.fromString(jwt.getSubject()), paymentId, key,
                body.amount(), body.reason());
        return ResponseEntity.status(result.status()).body(result.body());
    }

    @GetMapping("/{paymentId}/refunds")
    PageResponse<RefundResponse> refunds(
            @PathVariable UUID paymentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queries.refunds(paymentId, page, size);
    }
}
