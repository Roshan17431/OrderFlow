package com.orderflow.order.dto;

import com.orderflow.events.ShippingAddressSnapshot;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ShippingAddressRequest(
        @NotBlank @Size(max = 120) @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]+$") String recipientName,
        @NotBlank @Size(max = 200) @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]+$") String addressLine1,
        @Size(max = 200) @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$") String addressLine2,
        @NotBlank @Size(max = 120) @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]+$") String city,
        @NotBlank @Size(max = 120) @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]+$") String stateOrRegion,
        @NotBlank @Size(max = 32) @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]+$") String postalCode,
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String countryCode,
        @Size(max = 300) @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$") String deliveryInstructions) {
    public ShippingAddressSnapshot snapshot() {
        return new ShippingAddressSnapshot(recipientName, addressLine1, addressLine2, city,
                stateOrRegion, postalCode, countryCode, deliveryInstructions);
    }
}
