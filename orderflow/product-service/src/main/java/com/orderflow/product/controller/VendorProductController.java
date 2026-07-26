package com.orderflow.product.controller;

import com.orderflow.product.dto.CreateProductRequest;
import com.orderflow.product.dto.PageResponse;
import com.orderflow.product.dto.ProductManagementResponse;
import com.orderflow.product.dto.UpdateProductRequest;
import com.orderflow.product.dto.VersionRequest;
import com.orderflow.product.security.RequestIdentity;
import com.orderflow.product.service.ProductManagementService;
import com.orderflow.product.validation.ProductSearchValidator;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vendor/products")
public class VendorProductController {

    private final ProductManagementService products;

    public VendorProductController(ProductManagementService products) {
        this.products = products;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductManagementResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateProductRequest request) {
        return products.create(RequestIdentity.from(jwt), request);
    }

    @PutMapping("/{productId}")
    public ProductManagementResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductRequest request) {
        return products.update(RequestIdentity.from(jwt), productId, request);
    }

    @PatchMapping("/{productId}/activate")
    public ProductManagementResponse activate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID productId,
            @Valid @RequestBody VersionRequest request) {
        return products.activate(RequestIdentity.from(jwt), productId, request);
    }

    @PatchMapping("/{productId}/deactivate")
    public ProductManagementResponse deactivate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID productId,
            @Valid @RequestBody VersionRequest request) {
        return products.deactivate(RequestIdentity.from(jwt), productId, request);
    }

    @PatchMapping("/{productId}/archive")
    public ProductManagementResponse archive(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID productId,
            @Valid @RequestBody VersionRequest request) {
        return products.archive(RequestIdentity.from(jwt), productId, request);
    }

    @GetMapping
    public PageResponse<ProductManagementResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = ProductSearchValidator.DEFAULT_SORT) String sort) {
        return products.list(RequestIdentity.from(jwt), vendorId, page, size, sort);
    }
}
