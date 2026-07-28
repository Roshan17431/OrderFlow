package com.orderflow.inventory.service;

import com.orderflow.inventory.domain.InventoryAdjustment;
import com.orderflow.inventory.domain.InventoryItem;
import com.orderflow.inventory.domain.StockBelowReservedException;
import com.orderflow.inventory.dto.InventoryStockResponse;
import com.orderflow.inventory.dto.StockAdjustmentRequest;
import com.orderflow.inventory.error.InventoryDomainException;
import com.orderflow.inventory.repository.InventoryAdjustmentRepository;
import com.orderflow.inventory.repository.InventoryItemRepository;
import com.orderflow.platform.error.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryTransactionService {

    private final InventoryItemRepository items;
    private final InventoryAdjustmentRepository adjustments;
    private final InventoryMapper mapper;
    private final MeterRegistry metrics;
    private final Clock clock;

    public InventoryTransactionService(
            InventoryItemRepository items,
            InventoryAdjustmentRepository adjustments,
            InventoryMapper mapper,
            MeterRegistry metrics,
            Clock clock) {
        this.items = items;
        this.adjustments = adjustments;
        this.mapper = mapper;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public InventoryStockResponse adjust(
            UUID productId,
            UUID actorId,
            StockAdjustmentRequest request,
            String correlationId) {
        long delta = request.quantityDelta();
        if (delta == 0) {
            throw new InventoryDomainException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.INVALID_STOCK_ADJUSTMENT,
                    "Quantity delta must not be zero.");
        }
        Instant now = Instant.now(clock);
        InventoryItem item = items.findByProductIdForUpdate(productId).orElse(null);
        if (item == null) {
            if (delta < 0) {
                throw stockBelowReserved();
            }
            item = InventoryItem.create(productId, delta, now);
        } else {
            try {
                item.adjust(delta, now);
            } catch (StockBelowReservedException exception) {
                throw stockBelowReserved();
            } catch (IllegalArgumentException exception) {
                throw new InventoryDomainException(
                        HttpStatus.BAD_REQUEST,
                        ErrorCode.INVALID_STOCK_ADJUSTMENT,
                        "The quantity adjustment is invalid.");
            }
        }
        item = items.saveAndFlush(item);
        adjustments.save(new InventoryAdjustment(
                item,
                delta,
                request.reason().trim(),
                trim(request.reference()),
                actorId,
                now,
                correlationId));
        metrics.counter(
                        "orderflow_inventory_adjustments_total",
                        "result",
                        delta > 0 ? "increase" : "decrease")
                .increment();
        return mapper.stock(productId, item);
    }

    private InventoryDomainException stockBelowReserved() {
        return new InventoryDomainException(
                HttpStatus.CONFLICT,
                ErrorCode.STOCK_BELOW_RESERVED,
                "Stock cannot be reduced below the reserved quantity.");
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
