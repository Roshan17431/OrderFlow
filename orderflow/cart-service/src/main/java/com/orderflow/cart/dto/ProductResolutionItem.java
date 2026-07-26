package com.orderflow.cart.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductResolutionItem(
        UUID productId,
        String name,
        BigDecimal priceAmount,
        String currency,
        boolean purchasable,
        Long productVersion) {}
