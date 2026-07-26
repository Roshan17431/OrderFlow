package com.orderflow.cart.persistence;

import com.orderflow.cart.domain.CartItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StoredCartItem(
        UUID productId,
        String productNameSnapshot,
        int quantity,
        String unitPriceSnapshot,
        String currency,
        Long productVersionSnapshot,
        boolean purchasable,
        boolean priceChanged,
        String addedAt,
        String updatedAt) {

    public static StoredCartItem from(CartItem item) {
        return new StoredCartItem(
                item.productId(),
                item.productNameSnapshot(),
                item.quantity(),
                item.unitPriceSnapshot().toPlainString(),
                item.currency(),
                item.productVersionSnapshot(),
                item.purchasable(),
                item.priceChanged(),
                item.addedAt().toString(),
                item.updatedAt().toString());
    }

    public CartItem toDomain() {
        return new CartItem(
                productId,
                productNameSnapshot,
                quantity,
                new BigDecimal(unitPriceSnapshot),
                currency,
                productVersionSnapshot,
                purchasable,
                priceChanged,
                Instant.parse(addedAt),
                Instant.parse(updatedAt));
    }
}
