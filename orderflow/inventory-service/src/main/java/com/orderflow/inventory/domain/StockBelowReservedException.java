package com.orderflow.inventory.domain;

public final class StockBelowReservedException extends IllegalStateException {
    public StockBelowReservedException() {
        super("Stock cannot be reduced below the reserved quantity");
    }
}
