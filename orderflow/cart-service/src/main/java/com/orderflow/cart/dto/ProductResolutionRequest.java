package com.orderflow.cart.dto;

import java.util.List;
import java.util.UUID;

public record ProductResolutionRequest(List<UUID> productIds) {

    public ProductResolutionRequest {
        productIds = List.copyOf(productIds);
    }
}
