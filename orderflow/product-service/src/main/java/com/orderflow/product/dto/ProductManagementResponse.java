package com.orderflow.product.dto;

import com.orderflow.product.domain.ProductStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductManagementResponse(
        UUID id,
        UUID vendorId,
        String sku,
        String slug,
        String name,
        String description,
        BigDecimal priceAmount,
        String currency,
        ProductStatus status,
        CategorySummary category,
        Instant createdAt,
        Instant updatedAt,
        long version) {}
