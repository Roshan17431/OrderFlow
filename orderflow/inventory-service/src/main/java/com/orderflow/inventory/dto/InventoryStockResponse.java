package com.orderflow.inventory.dto;

import java.time.Instant;
import java.util.UUID;

public record InventoryStockResponse(
        UUID productId,
        long onHandQuantity,
        long reservedQuantity,
        long availableQuantity,
        long version,
        Instant updatedAt) {}
