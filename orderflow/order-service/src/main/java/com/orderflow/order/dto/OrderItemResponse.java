package com.orderflow.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID productId,
        String productName,
        long quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        String currency,
        Long productVersion) {}
