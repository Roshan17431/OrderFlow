package com.orderflow.product.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record ProductResolutionRequest(
        @NotEmpty @Size(max = 100) List<UUID> productIds) {

    public ProductResolutionRequest {
        productIds = productIds == null ? List.of() : List.copyOf(productIds);
    }
}
