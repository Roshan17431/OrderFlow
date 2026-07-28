package com.orderflow.inventory.correlation;

public final class InventoryRequestAttributes {
    public static final String CORRELATION_ID =
            InventoryRequestAttributes.class.getName() + ".correlationId";

    private InventoryRequestAttributes() {}
}
