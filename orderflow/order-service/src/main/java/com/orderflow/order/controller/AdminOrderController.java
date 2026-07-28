package com.orderflow.order.controller;

import com.orderflow.order.domain.OrderStatus;
import com.orderflow.order.dto.OrderResponse;
import com.orderflow.order.dto.PageResponse;
import com.orderflow.order.service.OrderQueryService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderQueryService queries;

    public AdminOrderController(OrderQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    public PageResponse<OrderResponse> list(
            @RequestParam(required = false) UUID customerId,
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
        return queries.adminOrders(
                customerId, status, createdFrom, createdTo, sort, page, size);
    }
}
