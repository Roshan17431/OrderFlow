package com.orderflow.inventory.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationExpirationPoller {

    private final InventoryWorkflowService workflows;

    public ReservationExpirationPoller(InventoryWorkflowService workflows) {
        this.workflows = workflows;
    }

    @Scheduled(
            fixedDelayString = "${app.inventory.expiration-poll-delay:30s}",
            initialDelayString = "${app.inventory.expiration-poll-delay:30s}")
    public void releaseExpired() {
        workflows.expireDueReservations();
    }
}
