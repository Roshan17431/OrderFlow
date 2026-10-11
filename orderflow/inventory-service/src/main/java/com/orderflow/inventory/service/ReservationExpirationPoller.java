package com.orderflow.inventory.service;

import com.orderflow.inventory.config.InventoryProperties;
import com.orderflow.scheduler.JobResult;
import com.orderflow.scheduler.PostgresJobScheduler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import com.orderflow.inventory.repository.InventoryReservationRepository;
import com.orderflow.inventory.domain.ReservationStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReservationExpirationPoller {

    private final InventoryWorkflowService workflows;
    private final InventoryProperties properties;
    private final PostgresJobScheduler scheduler;
    private final MeterRegistry metrics;
    private final InventoryReservationRepository reservations;
    private final Clock clock;
    private final AtomicLong overdue = new AtomicLong();

    public ReservationExpirationPoller(InventoryWorkflowService workflows, InventoryProperties properties,
            PostgresJobScheduler scheduler, MeterRegistry metrics,
            InventoryReservationRepository reservations, Clock clock) {
        this.workflows = workflows;
        this.properties = properties;
        this.scheduler = scheduler;
        this.metrics = metrics;
        this.reservations = reservations;
        this.clock = clock;
        metrics.gauge("inventory_reservations_overdue", overdue);
    }

    @Scheduled(
            fixedDelayString = "${app.inventory.expiration-poll-delay:30s}",
            initialDelayString = "${app.inventory.expiration-poll-delay:30s}")
    public void releaseExpired() {
        if (!properties.expirationEnabled()) return;
        scheduler.execute("inventoryReservationExpirationJob", properties.expirationLockDuration(), correlation -> {
            Timer.Sample timer = Timer.start(metrics);
            metrics.counter("inventory_reservation_expiration_runs_total").increment();
            try {
                overdue.set(reservations.countByStatusAndExpiresAtLessThanEqual(
                        ReservationStatus.RESERVED, Instant.now(clock)));
                int expired = workflows.expireDueReservations(correlation);
                metrics.counter("inventory_reservation_expired_total").increment(expired);
                return JobResult.success(expired);
            } catch (RuntimeException exception) {
                metrics.counter("inventory_reservation_expiration_failures_total").increment();
                throw exception;
            } finally {
                overdue.set(reservations.countByStatusAndExpiresAtLessThanEqual(
                        ReservationStatus.RESERVED, Instant.now(clock)));
                timer.stop(metrics.timer("inventory_reservation_expiration_duration"));
            }
        });
    }
}
