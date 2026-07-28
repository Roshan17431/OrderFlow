package com.orderflow.inventory.client;

import java.util.UUID;

public record ProductManagementAccess(UUID productId, String status, UUID vendorId) {}
