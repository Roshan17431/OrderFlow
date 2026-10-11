package com.orderflow.cart.dto;

import java.util.List;
import java.util.Objects;

public record ProductResolutionResponse(List<ProductResolutionItem> items) {

    public ProductResolutionResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items are required"));
    }
}
