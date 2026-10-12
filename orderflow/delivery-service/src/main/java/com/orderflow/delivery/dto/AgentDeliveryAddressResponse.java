package com.orderflow.delivery.dto;
public record AgentDeliveryAddressResponse(String recipientName, String addressLine1,
        String addressLine2, String city, String stateOrRegion, String postalCode,
        String countryCode, String deliveryInstructions) {}
