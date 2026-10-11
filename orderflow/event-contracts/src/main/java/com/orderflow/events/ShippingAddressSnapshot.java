package com.orderflow.events;

import java.util.regex.Pattern;

public record ShippingAddressSnapshot(
        String recipientName,
        String addressLine1,
        String addressLine2,
        String city,
        String stateOrRegion,
        String postalCode,
        String countryCode,
        String deliveryInstructions) {

    private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}\\p{Cf}]");

    public ShippingAddressSnapshot {
        recipientName = required(recipientName, 120, "recipientName");
        addressLine1 = required(addressLine1, 200, "addressLine1");
        addressLine2 = optional(addressLine2, 200, "addressLine2");
        city = required(city, 120, "city");
        stateOrRegion = required(stateOrRegion, 120, "stateOrRegion");
        postalCode = required(postalCode, 32, "postalCode");
        if (countryCode == null || !countryCode.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("countryCode must be ISO alpha-2");
        }
        deliveryInstructions = optional(deliveryInstructions, 300, "deliveryInstructions");
    }

    private static String required(String value, int max, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return bounded(value.trim(), max, name);
    }

    private static String optional(String value, int max, String name) {
        return value == null || value.isBlank() ? null : bounded(value.trim(), max, name);
    }

    private static String bounded(String value, int max, String name) {
        if (value.length() > max || CONTROL.matcher(value).find()) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }
}
