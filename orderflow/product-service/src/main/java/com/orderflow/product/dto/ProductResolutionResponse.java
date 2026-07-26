package com.orderflow.product.dto;

import java.util.List;

public record ProductResolutionResponse(List<ProductResolutionItem> items) {

    public ProductResolutionResponse {
        items = List.copyOf(items);
    }
}
