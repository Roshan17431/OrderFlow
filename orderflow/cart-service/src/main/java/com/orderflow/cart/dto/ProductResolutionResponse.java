package com.orderflow.cart.dto;

import java.util.List;

public record ProductResolutionResponse(List<ProductResolutionItem> items) {

    public ProductResolutionResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
