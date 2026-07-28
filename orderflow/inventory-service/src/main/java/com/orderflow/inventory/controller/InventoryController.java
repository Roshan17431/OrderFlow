package com.orderflow.inventory.controller;

import com.orderflow.inventory.correlation.InventoryRequestAttributes;
import com.orderflow.inventory.dto.InventoryAdjustmentResponse;
import com.orderflow.inventory.dto.InventoryStockResponse;
import com.orderflow.inventory.dto.PageResponse;
import com.orderflow.inventory.dto.ReservationResponse;
import com.orderflow.inventory.dto.StockAdjustmentRequest;
import com.orderflow.inventory.security.RequestIdentity;
import com.orderflow.inventory.service.InventoryManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryManagementService inventory;

    public InventoryController(InventoryManagementService inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/products/{productId}")
    public InventoryStockResponse stock(@PathVariable UUID productId) {
        return inventory.get(productId);
    }

    @PostMapping("/products/{productId}/adjustments")
    public InventoryStockResponse adjust(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID productId,
            @Valid @RequestBody StockAdjustmentRequest request,
            HttpServletRequest servletRequest) {
        return inventory.adjust(
                productId,
                RequestIdentity.from(jwt).userId(),
                request,
                (String) servletRequest.getAttribute(
                        InventoryRequestAttributes.CORRELATION_ID));
    }

    @GetMapping("/products/{productId}/adjustments")
    public PageResponse<InventoryAdjustmentResponse> history(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return inventory.history(productId, page, size);
    }

    @GetMapping("/reservations/order/{orderId}")
    public ReservationResponse reservation(@PathVariable UUID orderId) {
        return inventory.reservation(orderId);
    }
}
