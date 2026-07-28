package com.orderflow.order.service;

import com.orderflow.order.config.OrderProperties;
import com.orderflow.order.domain.CustomerOrder;
import com.orderflow.order.domain.OrderStatus;
import com.orderflow.order.dto.OrderHistoryResponse;
import com.orderflow.order.dto.OrderResponse;
import com.orderflow.order.dto.PageResponse;
import com.orderflow.order.error.OrderDomainException;
import com.orderflow.order.repository.CustomerOrderRepository;
import com.orderflow.order.repository.OrderSpecifications;
import com.orderflow.order.security.RequestIdentity;
import com.orderflow.platform.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderQueryService {

    private static final Set<String> SORT_FIELDS =
            Set.of("createdAt", "updatedAt", "status", "totalAmount");
    private final CustomerOrderRepository orders;
    private final OrderMapper mapper;
    private final OrderProperties properties;

    public OrderQueryService(
            CustomerOrderRepository orders, OrderMapper mapper, OrderProperties properties) {
        this.orders = orders;
        this.mapper = mapper;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> customerOrders(
            UUID customerId,
            OrderStatus status,
            Instant from,
            Instant to,
            String sort,
            int page,
            int size) {
        return search(customerId, status, from, to, sort, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> adminOrders(
            UUID customerId,
            OrderStatus status,
            Instant from,
            Instant to,
            String sort,
            int page,
            int size) {
        return search(customerId, status, from, to, sort, page, size);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(RequestIdentity actor, UUID orderId) {
        CustomerOrder order = orders.findWithItemsById(orderId).orElseThrow(this::notFound);
        if (!actor.admin() && !actor.userId().equals(order.getCustomerId())) {
            throw notFound();
        }
        return mapper.response(order);
    }

    @Transactional(readOnly = true)
    public List<OrderHistoryResponse> history(RequestIdentity actor, UUID orderId) {
        CustomerOrder order = orders.findById(orderId).orElseThrow(this::notFound);
        if (!actor.admin() && !actor.userId().equals(order.getCustomerId())) {
            throw notFound();
        }
        return order.getHistory().stream().map(mapper::history).toList();
    }

    private PageResponse<OrderResponse> search(
            UUID customerId,
            OrderStatus status,
            Instant from,
            Instant to,
            String sort,
            int page,
            int size) {
        validate(page, size, from, to);
        Sort ordering = sort(sort);
        return PageResponse.from(orders.findAll(
                        OrderSpecifications.filters(customerId, status, from, to),
                        PageRequest.of(page, size, ordering))
                .map(mapper::response));
    }

    private Sort sort(String value) {
        String[] parts = value == null ? new String[0] : value.split(",", -1);
        String field = parts.length == 0 || parts[0].isBlank() ? "createdAt" : parts[0];
        String direction = parts.length < 2 ? "desc" : parts[1];
        if (!SORT_FIELDS.contains(field)
                || (!direction.equalsIgnoreCase("asc")
                        && !direction.equalsIgnoreCase("desc"))
                || parts.length > 2) {
            throw new OrderDomainException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.INVALID_SORT,
                    "The requested order sort is invalid.");
        }
        return Sort.by(
                direction.equalsIgnoreCase("asc")
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC,
                field);
    }

    private void validate(int page, int size, Instant from, Instant to) {
        if (page < 0
                || size < 1
                || size > properties.maxPageSize()
                || from != null && to != null && from.isAfter(to)) {
            throw new OrderDomainException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR,
                    "Order search parameters are invalid.");
        }
    }

    private OrderDomainException notFound() {
        return new OrderDomainException(
                HttpStatus.NOT_FOUND, ErrorCode.ORDER_NOT_FOUND, "Order was not found.");
    }
}
