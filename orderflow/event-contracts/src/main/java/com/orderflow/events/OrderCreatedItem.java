package com.orderflow.events;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record OrderCreatedItem(
        UUID productId,
        String productName,
        long quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        String currency,
        Long productVersion) {

    public OrderCreatedItem {
        Objects.requireNonNull(productId, "productId is required");
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("productName is required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        Objects.requireNonNull(unitPrice, "unitPrice is required");
        Objects.requireNonNull(lineTotal, "lineTotal is required");
        if (unitPrice.signum() < 0 || lineTotal.signum() < 0) {
            throw new IllegalArgumentException("money cannot be negative");
        }
        if (lineTotal.compareTo(unitPrice.multiply(BigDecimal.valueOf(quantity))) != 0) {
            throw new IllegalArgumentException("lineTotal does not match quantity and unitPrice");
        }
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be a three-letter uppercase code");
        }
    }
}
