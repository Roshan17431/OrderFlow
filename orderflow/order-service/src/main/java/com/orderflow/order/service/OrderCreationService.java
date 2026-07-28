package com.orderflow.order.service;

import com.orderflow.order.client.CartClient;
import com.orderflow.order.client.CartSnapshot;
import com.orderflow.order.client.CartValidationResult;
import com.orderflow.order.domain.IdempotencyRecord;
import com.orderflow.order.domain.IdempotencyScope;
import com.orderflow.order.dto.CreateOrderRequest;
import com.orderflow.order.dto.OrderResponse;
import com.orderflow.order.error.OrderDomainException;
import com.orderflow.order.repository.CustomerOrderRepository;
import com.orderflow.order.repository.IdempotencyRecordRepository;
import com.orderflow.platform.error.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashSet;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCreationService {

    private final CartClient carts;
    private final IdempotencyRecordRepository idempotency;
    private final CustomerOrderRepository orders;
    private final OrderCreationTransaction transaction;
    private final IdempotencyKeyService keys;
    private final OrderMapper mapper;
    private final MeterRegistry metrics;

    public OrderCreationService(
            CartClient carts,
            IdempotencyRecordRepository idempotency,
            CustomerOrderRepository orders,
            OrderCreationTransaction transaction,
            IdempotencyKeyService keys,
            OrderMapper mapper,
            MeterRegistry metrics) {
        this.carts = carts;
        this.idempotency = idempotency;
        this.orders = orders;
        this.transaction = transaction;
        this.keys = keys;
        this.mapper = mapper;
        this.metrics = metrics;
    }

    public OrderResponse create(
            UUID customerId,
            String idempotencyKey,
            CreateOrderRequest request,
            String correlationId) {
        metrics.counter("orderflow_order_creation_attempts_total").increment();
        String key = keys.validate(idempotencyKey);
        String hash = keys.hash("expectedCartVersion=" + request.expectedCartVersion());
        OrderResponse previous = previous(customerId, key, hash);
        if (previous != null) {
            metrics.counter("orderflow_order_idempotency_total", "result", "hit").increment();
            return previous;
        }

        CartSnapshot before = carts.get();
        validateOwner(customerId, before);
        if (before.items().isEmpty()) {
            throw new OrderDomainException(
                    HttpStatus.CONFLICT,
                    ErrorCode.ORDER_CART_EMPTY,
                    "The shopping cart is empty.");
        }
        if (before.version() != request.expectedCartVersion()) {
            throw new OrderDomainException(
                    HttpStatus.CONFLICT,
                    ErrorCode.ORDER_CART_VERSION_CONFLICT,
                    "The shopping cart version has changed.");
        }
        CartValidationResult validation = carts.validate();
        CartSnapshot validated = validation.cart();
        validateOwner(customerId, validated);
        long expectedVersion = request.expectedCartVersion();
        boolean validationKeptVersion = validated.version() == expectedVersion;
        boolean validationReplacedSnapshot =
                expectedVersion < Long.MAX_VALUE && validated.version() == expectedVersion + 1;
        if (!validationKeptVersion && !validationReplacedSnapshot) {
            throw new OrderDomainException(
                    HttpStatus.CONFLICT,
                    ErrorCode.ORDER_CART_VERSION_CONFLICT,
                    "The shopping cart version changed during validation.");
        }
        validateCart(validation);
        try {
            OrderResponse response =
                    transaction.create(customerId, key, hash, validated, correlationId);
            metrics.counter("orderflow_order_creation_success_total").increment();
            return response;
        } catch (DataIntegrityViolationException exception) {
            OrderResponse concurrent = previous(customerId, key, hash);
            if (concurrent != null) {
                metrics.counter("orderflow_order_idempotency_total", "result", "concurrent_hit")
                        .increment();
                return concurrent;
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    protected OrderResponse previous(UUID customerId, String key, String hash) {
        IdempotencyRecord record = idempotency
                .findByScopeAndActorIdAndIdempotencyKey(
                        IdempotencyScope.CREATE_ORDER, customerId, key)
                .orElse(null);
        if (record == null) {
            return null;
        }
        if (!record.getRequestHash().equals(hash)) {
            metrics.counter("orderflow_order_idempotency_total", "result", "conflict")
                    .increment();
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
                .map(mapper::response)
                .orElseThrow(() -> new IllegalStateException("Idempotent order is missing"));
    }

    private void validateCart(CartValidationResult validation) {
        CartSnapshot cart = validation.cart();
        boolean productIdsUnique = new HashSet<>(
                        cart.items().stream().map(item -> item.productId()).toList())
                .size()
                == cart.items().size();
        boolean itemsValid = cart.items().stream()
                .allMatch(item -> item.purchasable()
                        && !item.priceChanged()
                        && item.quantity() > 0
                        && item.unitPriceSnapshot() != null
                        && item.unitPriceSnapshot().signum() >= 0
                        && cart.currency() != null
                        && cart.currency().equals(item.currency()));
        if (!validation.valid()
                || cart.items().isEmpty()
                || !itemsValid
                || !productIdsUnique
                || cart.currency() == null
                || !cart.currency().matches("[A-Z]{3}")) {
            throw new OrderDomainException(
                    HttpStatus.CONFLICT,
                    ErrorCode.ORDER_CART_INVALID,
                    "The shopping cart is not valid for order creation.");
        }
    }

    private void validateOwner(UUID customerId, CartSnapshot cart) {
        if (cart == null || !customerId.equals(cart.userId())) {
            throw new OrderDomainException(
                    HttpStatus.CONFLICT,
                    ErrorCode.ORDER_CART_INVALID,
                    "The shopping cart does not belong to the authenticated customer.");
        }
    }
}
