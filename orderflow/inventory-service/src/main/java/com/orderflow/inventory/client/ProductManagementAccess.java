package com.orderflow.inventory.client;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ProductManagementAccess(UUID productId, String status, UUID vendorId) {
    private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "INACTIVE", "ARCHIVED");

    public ProductManagementAccess {
        Objects.requireNonNull(productId, "productId is required");
        if (!STATUSES.contains(status)) throw new IllegalArgumentException("status is invalid");
        Objects.requireNonNull(vendorId, "vendorId is required");
    }
}
