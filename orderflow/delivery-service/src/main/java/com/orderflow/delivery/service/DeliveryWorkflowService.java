package com.orderflow.delivery.service;

import com.orderflow.delivery.domain.*;
import com.orderflow.delivery.repository.*;
import com.orderflow.events.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.*;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryWorkflowService {
    static final String CONSUMER = "delivery-order-events-v1";
    private final DeliveryRepository deliveries;
    private final DeliveryStatusHistoryRepository history;
    private final DeliveryAgentRepository agents;
    private final ProcessedEventStore processed;
    private final DeliveryAssignmentService assignment;
    private final DeliveryEventOutbox events;
    private final MeterRegistry metrics;
    private final Clock clock;
    private final JdbcTemplate jdbc;

    public DeliveryWorkflowService(DeliveryRepository d, DeliveryStatusHistoryRepository h,
            DeliveryAgentRepository a, ProcessedEventStore p, DeliveryAssignmentService s,
            DeliveryEventOutbox e, MeterRegistry m, Clock c, JdbcTemplate jdbc) {
        deliveries = d; history = h; agents = a; processed = p;
        assignment = s; events = e; metrics = m; clock = c; this.jdbc = jdbc;
    }

    @Transactional
    public void requested(DeliveryRequestedEvent event) {
        event.metadata().require(EventType.DELIVERY_REQUESTED, 1);
        lockOrder(event.orderId());
        if (processed.contains(event.metadata(), CONSUMER)) return;
        Delivery d = deliveries.findByOrderId(event.orderId()).orElse(null);
        if (d == null) {
            Instant now = Instant.now(clock);
            d = Delivery.create(event.orderId(), event.customerId(), event.shippingAddress(), now);
            d = deliveries.saveAndFlush(d);
            history.save(new DeliveryStatusHistory(d.getId(), null, d.getStatus(), null,
                    event.metadata().eventId(), event.metadata().correlationId(), now));
            var cancelled = jdbc.queryForList("SELECT customer_id, reason_code FROM delivery_cancellation WHERE order_id = ?", event.orderId());
            if (!cancelled.isEmpty()) {
                if (!event.customerId().equals(cancelled.getFirst().get("customer_id"))) {
                    throw new IllegalArgumentException("Cancellation customer does not match delivery");
                }
                String reason = (String) cancelled.getFirst().get("reason_code");
                d.cancel(reason, now);
                history.save(new DeliveryStatusHistory(d.getId(), DeliveryStatus.PENDING_ASSIGNMENT,
                        d.getStatus(), null, event.metadata().eventId(), event.metadata().correlationId(), now));
                events.cancelled(d, reason, event.metadata().correlationId(), event.metadata().eventId(), now);
            }
            metrics.counter("delivery_requests_total").increment();
        }
        if (!d.getCustomerId().equals(event.customerId())) {
            throw new IllegalArgumentException("Delivery customer does not match request");
        }
        processed.add(event.metadata(), CONSUMER);
        if (d.getStatus() == DeliveryStatus.PENDING_ASSIGNMENT) {
            assignment.autoAssign(d.getId(), event.metadata().correlationId(), event.metadata().eventId());
        }
    }

    @Transactional
    public void cancel(DeliveryCancelRequestedEvent event) {
        event.metadata().require(EventType.DELIVERY_CANCEL_REQUESTED, 1);
        lockOrder(event.orderId());
        if (processed.contains(event.metadata(), CONSUMER)) return;
        Delivery d = deliveries.lockByOrderId(event.orderId()).orElse(null);
        Instant now = Instant.now(clock);
        if (d == null) {
            jdbc.update("INSERT INTO delivery_cancellation(order_id,customer_id,reason_code,created_at) VALUES (?,?,?,?) ON CONFLICT(order_id) DO NOTHING",
                    event.orderId(), event.customerId(), event.reasonCode(), Timestamp.from(now));
        } else {
            if (!d.getCustomerId().equals(event.customerId())
                    || (event.deliveryId() != null && !event.deliveryId().equals(d.getId()))) {
                throw new IllegalArgumentException("Cancellation does not match delivery");
            }
            // A late cancellation reconciles the real terminal/irreversible status.
            switch (d.getStatus()) {
                case OUT_FOR_DELIVERY -> events.outForDelivery(d, event.metadata().correlationId(), now);
                case DELIVERED -> events.delivered(d, event.metadata().correlationId(), now);
                case FAILED -> events.failed(d, d.getFailureCode(), event.metadata().correlationId(), now);
                case CANCELLED -> { }
                default -> {
                    DeliveryStatus previous = d.getStatus();
                    d.cancel(event.reasonCode(), now);
                    if (d.getDeliveryAgentId() != null) {
                        agents.lockById(d.getDeliveryAgentId()).ifPresent(a -> a.released(now));
                    }
                    history.save(new DeliveryStatusHistory(d.getId(), previous, d.getStatus(), null,
                            event.metadata().eventId(), event.metadata().correlationId(), now));
                    events.cancelled(d, event.reasonCode(), event.metadata().correlationId(), event.metadata().eventId(), now);
                    metrics.counter("deliveries_cancelled_total").increment();
                }
            }
        }
        processed.add(event.metadata(), CONSUMER);
    }

    private void lockOrder(UUID order) {
        // Protect the absent-row race between initial creation and cancellation.
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, order.toString());
                statement.execute();
            }
            return null;
        });
    }
}
