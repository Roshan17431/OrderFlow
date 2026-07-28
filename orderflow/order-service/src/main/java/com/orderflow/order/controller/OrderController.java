package com.orderflow.order.controller;

import com.orderflow.order.correlation.OrderRequestAttributes;
import com.orderflow.order.domain.OrderStatus;
import com.orderflow.order.dto.CancelOrderRequest;
import com.orderflow.order.dto.CreateOrderRequest;
import com.orderflow.order.dto.OrderHistoryResponse;
import com.orderflow.order.dto.OrderResponse;
import com.orderflow.order.dto.PageResponse;
import com.orderflow.order.security.RequestIdentity;
import com.orderflow.order.service.OrderCancellationService;
import com.orderflow.order.service.OrderCreationService;
import com.orderflow.order.service.OrderQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderCreationService creation;
    private final OrderCancellationService cancellation;
    private final OrderQueryService queries;

    public OrderController(
            OrderCreationService creation,
            OrderCancellationService cancellation,
            OrderQueryService queries) {
        this.creation = creation;
        this.cancellation = cancellation;
        this.queries = queries;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OrderResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request,
            HttpServletRequest servletRequest) {
        return creation.create(
                UUID.fromString(jwt.getSubject()),
                idempotencyKey,
                request,
                correlationId(servletRequest));
    }

    @GetMapping
    public PageResponse<OrderResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant createdFrom,
            @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant createdTo,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queries.customerOrders(
                UUID.fromString(jwt.getSubject()),
                status,
                createdFrom,
                createdTo,
                sort,
                page,
                size);
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return queries.get(RequestIdentity.from(jwt), orderId);
    }

    @GetMapping("/{orderId}/history")
    public List<OrderHistoryResponse> history(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return queries.history(RequestIdentity.from(jwt), orderId);
    }

    @PostMapping("/{orderId}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OrderResponse cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID orderId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CancelOrderRequest request,
            HttpServletRequest servletRequest) {
        return cancellation.cancel(
                RequestIdentity.from(jwt),
                orderId,
                idempotencyKey,
                request,
                correlationId(servletRequest));
    }

    private String correlationId(HttpServletRequest request) {
        return (String) request.getAttribute(OrderRequestAttributes.CORRELATION_ID);
    }
}
