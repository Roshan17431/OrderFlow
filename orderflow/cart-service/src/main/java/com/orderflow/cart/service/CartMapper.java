package com.orderflow.cart.service;

import com.orderflow.cart.domain.Cart;
import com.orderflow.cart.domain.CartItem;
import com.orderflow.cart.dto.CartItemResponse;
import com.orderflow.cart.dto.CartResponse;
import org.springframework.stereotype.Component;

@Component
public class CartMapper {

    public CartResponse response(Cart cart) {
        return new CartResponse(
                cart.userId(),
                cart.version(),
                cart.currency(),
                cart.items().stream().map(this::item).toList(),
                cart.distinctItemCount(),
                cart.totalQuantity(),
                cart.subtotal(),
                cart.createdAt(),
                cart.updatedAt(),
                cart.expiresAt());
    }

    private CartItemResponse item(CartItem item) {
        return new CartItemResponse(
                item.productId(),
                item.productNameSnapshot(),
                item.quantity(),
                item.unitPriceSnapshot(),
                item.currency(),
                item.productVersionSnapshot(),
                item.purchasable(),
                item.priceChanged(),
                item.addedAt(),
                item.updatedAt());
    }
}
