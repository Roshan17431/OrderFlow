package com.orderflow.order.domain;

import com.orderflow.events.ShippingAddressSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ShippingAddress {
    @Column(name = "recipient_name", nullable = false, length = 120) private String recipientName;
    @Column(name = "address_line_1", nullable = false, length = 200) private String addressLine1;
    @Column(name = "address_line_2", length = 200) private String addressLine2;
    @Column(nullable = false, length = 120) private String city;
    @Column(name = "state_or_region", nullable = false, length = 120) private String stateOrRegion;
    @Column(name = "postal_code", nullable = false, length = 32) private String postalCode;
    @Column(name = "country_code", nullable = false, length = 2) private String countryCode;
    @Column(name = "delivery_instructions", length = 300) private String deliveryInstructions;

    protected ShippingAddress() {}

    public ShippingAddress(ShippingAddressSnapshot value) {
        this.recipientName = value.recipientName(); this.addressLine1 = value.addressLine1();
        this.addressLine2 = value.addressLine2(); this.city = value.city();
        this.stateOrRegion = value.stateOrRegion(); this.postalCode = value.postalCode();
        this.countryCode = value.countryCode(); this.deliveryInstructions = value.deliveryInstructions();
    }

    public ShippingAddressSnapshot snapshot() {
        return new ShippingAddressSnapshot(recipientName, addressLine1, addressLine2, city,
                stateOrRegion, postalCode, countryCode, deliveryInstructions);
    }
}
