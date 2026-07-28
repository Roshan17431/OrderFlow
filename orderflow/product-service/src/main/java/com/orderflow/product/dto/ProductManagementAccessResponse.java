package com.orderflow.product.dto;

import com.orderflow.product.domain.ProductStatus;
import java.util.UUID;

public record ProductManagementAccessResponse(
        UUID productId, ProductStatus status, UUID vendorId) {}
