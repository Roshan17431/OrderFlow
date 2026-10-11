package com.orderflow.cart.dto;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record ProductResolutionItem(
        UUID productId,
        String name,
        BigDecimal priceAmount,
        String currency,
        boolean purchasable,
        Long productVersion) {

    public ProductResolutionItem {
        Objects.requireNonNull(productId, "productId is required");
        if (purchasable) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name is required for a purchasable product");
            }
            Objects.requireNonNull(priceAmount, "priceAmount is required for a purchasable product");
            if (priceAmount.signum() < 0) {
                throw new IllegalArgumentException("priceAmount cannot be negative");
            }
            if (currency == null || !currency.matches("[A-Z]{3}")) {
                throw new IllegalArgumentException("currency must be a three-letter uppercase code");
            }
            if (productVersion == null || productVersion < 0) {
                throw new IllegalArgumentException("productVersion is required and cannot be negative");
            }
        }
    }
}
