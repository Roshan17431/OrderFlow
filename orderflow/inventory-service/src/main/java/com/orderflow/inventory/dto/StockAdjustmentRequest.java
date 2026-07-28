package com.orderflow.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;

public record StockAdjustmentRequest(
        @NotNull Long quantityDelta,
        @NotBlank @Size(max = 120) String reason,
        @Size(max = 200) String reference) {

    @AssertTrue(message = "quantityDelta must not be zero")
    public boolean isQuantityDeltaNonZero() {
        return quantityDelta == null || quantityDelta != 0;
    }
}
