package com.orderflow.payment.controller;

import com.orderflow.payment.correlation.PaymentRequestAttributes;
import com.orderflow.payment.dto.CancelPaymentRequest;
import com.orderflow.payment.dto.ConfirmPaymentRequest;
import com.orderflow.payment.dto.PageResponse;
import com.orderflow.payment.dto.PaymentAttemptResponse;
import com.orderflow.payment.dto.PaymentResponse;
import com.orderflow.payment.error.PaymentDomainException;
import com.orderflow.payment.security.RequestIdentity;
import com.orderflow.payment.service.CommandResult;
import com.orderflow.payment.service.PaymentCommandService;
import com.orderflow.payment.service.PaymentQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
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
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentQueryService queries;
    private final PaymentCommandService commands;

    public PaymentController(PaymentQueryService queries, PaymentCommandService commands) {
        this.queries = queries;
        this.commands = commands;
    }

    @GetMapping("/orders/{orderId}")
    PaymentResponse byOrder(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return queries.byOrder(RequestIdentity.from(jwt), orderId);
    }

    @GetMapping("/{paymentId}")
    PaymentResponse byId(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID paymentId) {
        return queries.byId(RequestIdentity.from(jwt), paymentId);
    }

    @GetMapping("/{paymentId}/attempts")
    PageResponse<PaymentAttemptResponse> attempts(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID paymentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queries.attempts(RequestIdentity.from(jwt), paymentId, page, size);
    }

    @PostMapping("/orders/{orderId}/confirm")
    ResponseEntity<PaymentResponse> confirm(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID orderId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody ConfirmPaymentRequest body,
            HttpServletRequest request) {
        CommandResult<PaymentResponse> result = commands.confirm(
                RequestIdentity.from(jwt), orderId, key, body.paymentMethodToken(),
                correlationId(request));
        return response(result);
    }

    @PostMapping("/{paymentId}/cancel")
    ResponseEntity<PaymentResponse> cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID paymentId,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody CancelPaymentRequest body,
            HttpServletRequest request) {
        CommandResult<PaymentResponse> result = commands.cancel(
                RequestIdentity.from(jwt), paymentId, key, body.reason(),
                correlationId(request));
        return response(result);
    }

    private ResponseEntity<PaymentResponse> response(CommandResult<PaymentResponse> result) {
        if (result.failed()) {
            throw new PaymentDomainException(
                    org.springframework.http.HttpStatus.valueOf(result.status()),
                    result.errorCode(), result.errorMessage());
        }
        return ResponseEntity.status(result.status()).body(result.body());
    }

    private String correlationId(HttpServletRequest request) {
        return (String) request.getAttribute(PaymentRequestAttributes.CORRELATION_ID);
    }
}
