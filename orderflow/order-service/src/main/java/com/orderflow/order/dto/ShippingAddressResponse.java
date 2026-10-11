package com.orderflow.order.dto;
public record ShippingAddressResponse(String recipientName, String addressLine1, String addressLine2,
        String city, String stateOrRegion, String postalCode, String countryCode, String deliveryInstructions) {}
