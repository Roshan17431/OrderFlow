package com.orderflow.product.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductSummary(
        UUID id,
        String slug,
        String name,
        String description,
        BigDecimal priceAmount,
        String currency,
        CategorySummary category,
        Instant updatedAt) {}
