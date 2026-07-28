package com.orderflow.inventory.service;

import com.orderflow.inventory.client.ProductClient;
import com.orderflow.inventory.config.InventoryProperties;
import com.orderflow.inventory.dto.InventoryAdjustmentResponse;
import com.orderflow.inventory.dto.InventoryStockResponse;
import com.orderflow.inventory.dto.PageResponse;
import com.orderflow.inventory.dto.ReservationResponse;
import com.orderflow.inventory.dto.StockAdjustmentRequest;
import com.orderflow.inventory.error.InventoryDomainException;
import com.orderflow.inventory.repository.InventoryAdjustmentRepository;
import com.orderflow.inventory.repository.InventoryItemRepository;
import com.orderflow.inventory.repository.InventoryReservationRepository;
import com.orderflow.platform.error.ErrorCode;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryManagementService {

    private final ProductClient products;
    private final InventoryItemRepository items;
    private final InventoryAdjustmentRepository adjustments;
    private final InventoryReservationRepository reservations;
    private final InventoryTransactionService transactions;
    private final InventoryMapper mapper;
    private final InventoryProperties properties;

    public InventoryManagementService(
            ProductClient products,
            InventoryItemRepository items,
            InventoryAdjustmentRepository adjustments,
            InventoryReservationRepository reservations,
            InventoryTransactionService transactions,
            InventoryMapper mapper,
            InventoryProperties properties) {
        this.products = products;
        this.items = items;
        this.adjustments = adjustments;
        this.reservations = reservations;
        this.transactions = transactions;
        this.mapper = mapper;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public InventoryStockResponse get(UUID productId) {
        products.requireManagementAccess(productId);
        return mapper.stock(productId, items.findByProductId(productId).orElse(null));
    }

    public InventoryStockResponse adjust(
            UUID productId,
            UUID actorId,
            StockAdjustmentRequest request,
            String correlationId) {
        products.requireManagementAccess(productId);
        return transactions.adjust(productId, actorId, request, correlationId);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryAdjustmentResponse> history(
            UUID productId, int page, int size) {
        products.requireManagementAccess(productId);
        validatePage(page, size);
        return PageResponse.from(adjustments.findByProductId(
                productId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(mapper::adjustment));
    }

    @Transactional(readOnly = true)
    public ReservationResponse reservation(UUID orderId) {
        return reservations.findByOrderId(orderId)
                .map(mapper::reservation)
                .orElseThrow(() -> new InventoryDomainException(
                        HttpStatus.NOT_FOUND,
                        ErrorCode.INVENTORY_RESERVATION_NOT_FOUND,
                        "Inventory reservation was not found."));
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > properties.maxHistoryPageSize()) {
            throw new InventoryDomainException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR,
                    "Pagination parameters are invalid.");
        }
    }
}
