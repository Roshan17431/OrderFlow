package com.orderflow.inventory.service;

import com.orderflow.events.EventMetadata;
import com.orderflow.events.EventType;
import com.orderflow.events.InventoryReleasedEvent;
import com.orderflow.events.InventoryReservationFailedEvent;
import com.orderflow.events.InventoryReservationFailure;
import com.orderflow.events.InventoryReservedEvent;
import com.orderflow.events.InventoryReservedItem;
import com.orderflow.events.OrderCancelRequestedEvent;
import com.orderflow.events.OrderCreatedEvent;
import com.orderflow.inventory.config.InventoryProperties;
import com.orderflow.inventory.config.KafkaTopicProperties;
import com.orderflow.inventory.domain.InventoryItem;
import com.orderflow.inventory.domain.InventoryReservation;
import com.orderflow.inventory.domain.ProcessedEvent;
import com.orderflow.inventory.domain.ReservationStatus;
import com.orderflow.inventory.outbox.OutboxStore;
import com.orderflow.inventory.repository.InventoryItemRepository;
import com.orderflow.inventory.repository.InventoryReservationRepository;
import com.orderflow.inventory.repository.ProcessedEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryWorkflowService {

    static final String ORDER_CREATED_CONSUMER = "inventory-order-created-v1";
    static final String ORDER_CANCEL_CONSUMER = "inventory-order-cancel-v1";
    private static final Logger LOGGER =
            LoggerFactory.getLogger(InventoryWorkflowService.class);

    private final InventoryItemRepository inventory;
    private final InventoryReservationRepository reservations;
    private final ProcessedEventRepository processed;
    private final OutboxStore outbox;
    private final KafkaTopicProperties topics;
    private final InventoryProperties properties;
    private final MeterRegistry metrics;
    private final Clock clock;

    public InventoryWorkflowService(
            InventoryItemRepository inventory,
            InventoryReservationRepository reservations,
            ProcessedEventRepository processed,
            OutboxStore outbox,
            KafkaTopicProperties topics,
            InventoryProperties properties,
            MeterRegistry metrics,
            Clock clock) {
        this.inventory = inventory;
        this.reservations = reservations;
        this.processed = processed;
        this.outbox = outbox;
        this.topics = topics;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public void reserve(OrderCreatedEvent event) {
        event.metadata().require(EventType.ORDER_CREATED, OrderCreatedEvent.VERSION);
        Timer.Sample sample = Timer.start(metrics);
        metrics.counter("orderflow_inventory_reservation_attempts_total").increment();
        try {
            if (duplicate(event.metadata(), ORDER_CREATED_CONSUMER)) {
                return;
            }
            if (reservations.findByOrderIdForUpdate(event.orderId()).isPresent()) {
                remember(event.metadata(), ORDER_CREATED_CONSUMER);
                return;
            }

            Map<UUID, Long> quantities = quantities(event);
            List<UUID> productIds = quantities.keySet().stream().sorted().toList();
            Map<UUID, InventoryItem> locked = inventory
                    .findAllByProductIdForUpdate(productIds)
                    .stream()
                    .collect(Collectors.toMap(
                            InventoryItem::getProductId, Function.identity()));
            List<InventoryReservationFailure> failures = new ArrayList<>();
            for (UUID productId : productIds) {
                InventoryItem item = locked.get(productId);
                long available = item == null ? 0 : item.availableQuantity();
                long requested = quantities.get(productId);
                if (available < requested) {
                    failures.add(new InventoryReservationFailure(
                            productId, requested, available, "INSUFFICIENT_STOCK"));
                }
            }

            Instant now = Instant.now(clock);
            if (!failures.isEmpty()) {
                InventoryReservation reservation = InventoryReservation.rejected(
                        event.orderId(), failureSummary(failures), now);
                reservations.save(reservation);
                InventoryReservationFailedEvent failed = new InventoryReservationFailedEvent(
                        metadata(
                                EventType.INVENTORY_RESERVATION_FAILED,
                                event.orderId(),
                                event.metadata().correlationId(),
                                event.metadata().eventId()),
                        event.orderId(),
                        failures);
                outbox.enqueue(
                        topics.inventoryEvents(),
                        event.orderId(),
                        failed,
                        failed.metadata());
                remember(event.metadata(), ORDER_CREATED_CONSUMER);
                metrics.counter("orderflow_inventory_reservations_total", "result", "failed")
                        .increment();
                metrics.counter(
                                "orderflow_inventory_reservation_failures_total",
                                "reason",
                                "insufficient_stock")
                        .increment();
                return;
            }

            Instant expiresAt = now.plus(properties.reservationTtl());
            InventoryReservation reservation =
                    InventoryReservation.reserved(event.orderId(), expiresAt, now);
            for (UUID productId : productIds) {
                long quantity = quantities.get(productId);
                locked.get(productId).reserve(quantity, now);
                reservation.addItem(productId, quantity, now);
            }
            inventory.saveAll(locked.values());
            reservations.save(reservation);
            InventoryReservedEvent reserved = new InventoryReservedEvent(
                    metadata(
                            EventType.INVENTORY_RESERVED,
                            event.orderId(),
                            event.metadata().correlationId(),
                            event.metadata().eventId()),
                    event.orderId(),
                    reservation.getId(),
                    expiresAt,
                    productIds.stream()
                            .map(productId -> new InventoryReservedItem(
                                    productId, quantities.get(productId)))
                            .toList());
            outbox.enqueue(
                    topics.inventoryEvents(), event.orderId(), reserved, reserved.metadata());
            remember(event.metadata(), ORDER_CREATED_CONSUMER);
            metrics.counter("orderflow_inventory_reservations_total", "result", "success")
                    .increment();
        } finally {
            sample.stop(metrics.timer("orderflow_inventory_reservation_processing"));
        }
    }

    @Transactional
    public void release(OrderCancelRequestedEvent event) {
        event.metadata().require(
                EventType.ORDER_CANCEL_REQUESTED, OrderCancelRequestedEvent.VERSION);
        if (duplicate(event.metadata(), ORDER_CANCEL_CONSUMER)) {
            return;
        }
        InventoryReservation reservation =
                reservations.findByOrderIdForUpdate(event.orderId()).orElse(null);
        Instant now = Instant.now(clock);
        if (reservation == null) {
            reservation = InventoryReservation.rejected(
                    event.orderId(), "CANCEL_BEFORE_RESERVATION", now);
            reservation.release(now, false);
            reservations.save(reservation);
            emitReleased(reservation, event.metadata(), "NO_RESERVATION", false, now);
        } else if (reservation.getStatus() == ReservationStatus.RESERVED) {
            releaseStock(reservation, now);
            reservation.release(now, false);
            emitReleased(reservation, event.metadata(), "ORDER_CANCELLED", true, now);
        } else if (reservation.getStatus() == ReservationStatus.REJECTED) {
            reservation.release(now, false);
            emitReleased(reservation, event.metadata(), "RESERVATION_REJECTED", false, now);
        }
        remember(event.metadata(), ORDER_CANCEL_CONSUMER);
    }

    @Transactional
    public int expireDueReservations() {
        if (!properties.expirationEnabled()) {
            return 0;
        }
        Instant now = Instant.now(clock);
        List<InventoryReservation> expired = reservations.findExpiredForUpdate(
                now, properties.expirationBatchSize());
        for (InventoryReservation reservation : expired) {
            releaseStock(reservation, now);
            reservation.release(now, true);
            EventMetadata origin = new EventMetadata(
                    UUID.randomUUID(),
                    EventType.INVENTORY_RELEASED,
                    InventoryReleasedEvent.VERSION,
                    "ORDER",
                    reservation.getOrderId(),
                    now,
                    "reservation-expiration-" + reservation.getId(),
                    null,
                    "inventory-service");
            InventoryReleasedEvent released = new InventoryReleasedEvent(
                    origin,
                    reservation.getOrderId(),
                    reservation.getId(),
                    now,
                    "RESERVATION_EXPIRED",
                    true);
            outbox.enqueue(
                    topics.inventoryEvents(),
                    reservation.getOrderId(),
                    released,
                    released.metadata());
            metrics.counter("orderflow_inventory_expired_reservations_total").increment();
        }
        return expired.size();
    }

    private void releaseStock(InventoryReservation reservation, Instant now) {
        List<UUID> productIds = reservation.getItems().stream()
                .map(item -> item.getProductId())
                .sorted()
                .toList();
        Map<UUID, InventoryItem> locked = inventory
                .findAllByProductIdForUpdate(productIds)
                .stream()
                .collect(Collectors.toMap(InventoryItem::getProductId, Function.identity()));
        for (var item : reservation.getItems()) {
            InventoryItem stock = locked.get(item.getProductId());
            if (stock == null) {
                throw new IllegalStateException("Reserved inventory row is missing");
            }
            stock.release(item.getQuantity(), now);
        }
        inventory.saveAll(locked.values());
    }

    private void emitReleased(
            InventoryReservation reservation,
            EventMetadata cause,
            String reason,
            boolean stockReleased,
            Instant now) {
        InventoryReleasedEvent released = new InventoryReleasedEvent(
                metadata(
                        EventType.INVENTORY_RELEASED,
                        reservation.getOrderId(),
                        cause.correlationId(),
                        cause.eventId()),
                reservation.getOrderId(),
                reservation.getId(),
                now,
                reason,
                stockReleased);
        outbox.enqueue(
                topics.inventoryEvents(),
                reservation.getOrderId(),
                released,
                released.metadata());
        metrics.counter(
                        "orderflow_inventory_releases_total",
                        "stockReleased",
                        Boolean.toString(stockReleased))
                .increment();
    }

    private Map<UUID, Long> quantities(OrderCreatedEvent event) {
        Map<UUID, Long> result = new LinkedHashMap<>();
        event.items().stream()
                .sorted(Comparator.comparing(item -> item.productId()))
                .forEach(item -> result.merge(item.productId(), item.quantity(), Math::addExact));
        return result;
    }

    private boolean duplicate(EventMetadata metadata, String consumer) {
        boolean duplicate =
                processed.existsByEventIdAndConsumerName(metadata.eventId(), consumer);
        if (duplicate) {
            metrics.counter(
                            "orderflow_consumer_duplicates_total",
                            "service",
                            "inventory-service",
                            "eventType",
                            metadata.eventType().name())
                    .increment();
            LOGGER.debug(
                    "Duplicate event ignored eventId={} eventType={}",
                    metadata.eventId(),
                    metadata.eventType());
        }
        return duplicate;
    }

    private void remember(EventMetadata metadata, String consumer) {
        processed.save(new ProcessedEvent(
                metadata.eventId(),
                metadata.eventType().name(),
                metadata.aggregateId(),
                consumer,
                Instant.now(clock),
                metadata.correlationId()));
        metrics.counter(
                        "orderflow_consumer_processed_total",
                        "service",
                        "inventory-service",
                        "eventType",
                        metadata.eventType().name())
                .increment();
    }

    private EventMetadata metadata(
            EventType type, UUID orderId, String correlationId, UUID causationId) {
        return new EventMetadata(
                UUID.randomUUID(),
                type,
                1,
                "ORDER",
                orderId,
                Instant.now(clock),
                correlationId,
                causationId,
                "inventory-service");
    }

    private String failureSummary(List<InventoryReservationFailure> failures) {
        String value = failures.stream()
                .map(failure -> failure.productId() + ":" + failure.reasonCode())
                .collect(Collectors.joining(","));
        return value.substring(0, Math.min(500, value.length()));
    }
}
