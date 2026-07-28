package com.orderflow.inventory.dto;

import java.util.UUID;

public record ReservationItemResponse(UUID productId, long quantity) {}
