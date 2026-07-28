package com.orderflow.order.service;

import com.orderflow.order.domain.IdempotencyRecord;
import com.orderflow.order.domain.IdempotencyScope;
import com.orderflow.order.dto.CancelOrderRequest;
import com.orderflow.order.dto.OrderResponse;
import com.orderflow.order.error.OrderDomainException;
import com.orderflow.order.repository.CustomerOrderRepository;
import com.orderflow.order.repository.IdempotencyRecordRepository;
import com.orderflow.order.security.RequestIdentity;
import com.orderflow.platform.error.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class OrderCancellationService {

    private final IdempotencyRecordRepository idempotency;
    private final CustomerOrderRepository orders;
    private final OrderCancellationTransaction transaction;
    private final IdempotencyKeyService keys;
    private final OrderMapper mapper;
    private final MeterRegistry metrics;

    public OrderCancellationService(
            IdempotencyRecordRepository idempotency,
            CustomerOrderRepository orders,
            OrderCancellationTransaction transaction,
            IdempotencyKeyService keys,
            OrderMapper mapper,
            MeterRegistry metrics) {
        this.idempotency = idempotency;
        this.orders = orders;
        this.transaction = transaction;
        this.keys = keys;
        this.mapper = mapper;
        this.metrics = metrics;
    }

    public OrderResponse cancel(
            RequestIdentity actor,
            UUID orderId,
            String idempotencyKey,
            CancelOrderRequest request,
            String correlationId) {
        String key = keys.validate(idempotencyKey);
        String reason = request.reason().trim();
        String hash = keys.hash("orderId=" + orderId + "&reason=" + reason);
        OrderResponse previous = previous(actor, key, hash);
        if (previous != null) {
            return previous;
        }
        try {
            OrderResponse response = transaction.cancel(
                    actor, orderId, key, hash, reason, correlationId);
            metrics.counter("orderflow_order_cancellations_total", "result", "accepted")
                    .increment();
            return response;
        } catch (DataIntegrityViolationException exception) {
            OrderResponse concurrent = previous(actor, key, hash);
            if (concurrent != null) {
                return concurrent;
            }
            throw exception;
        }
    }

    private OrderResponse previous(RequestIdentity actor, String key, String hash) {
        IdempotencyRecord record = idempotency
                .findByScopeAndActorIdAndIdempotencyKey(
                        IdempotencyScope.CANCEL_ORDER, actor.userId(), key)
                .orElse(null);
        if (record == null) {
            return null;
        }
        if (!record.getRequestHash().equals(hash)) {
            throw new OrderDomainException(
                    HttpStatus.CONFLICT,
                    ErrorCode.ORDER_IDEMPOTENCY_CONFLICT,
                    "The idempotency key was already used for a different request.");
        }
        if (!record.completed()) {
            throw new OrderDomainException(
                    HttpStatus.CONFLICT,
                    ErrorCode.ORDER_IDEMPOTENCY_CONFLICT,
                    "The idempotent request is still being processed.");
        }
        return orders.findWithItemsById(record.getResourceId())
                .filter(order -> actor.admin() || actor.userId().equals(order.getCustomerId()))
                .map(mapper::response)
                .orElseThrow(() -> new OrderDomainException(
                        HttpStatus.NOT_FOUND,
                        ErrorCode.ORDER_NOT_FOUND,
                        "Order was not found."));
    }
}
