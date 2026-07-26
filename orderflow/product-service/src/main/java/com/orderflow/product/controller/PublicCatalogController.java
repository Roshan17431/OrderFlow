package com.orderflow.product.controller;

import com.orderflow.product.dto.PageResponse;
import com.orderflow.product.dto.ProductDetailResponse;
import com.orderflow.product.dto.ProductResolutionRequest;
import com.orderflow.product.dto.ProductResolutionResponse;
import com.orderflow.product.dto.ProductSearchCriteria;
import com.orderflow.product.dto.ProductSummary;
import com.orderflow.product.service.PublicCatalogService;
import com.orderflow.product.validation.ProductSearchValidator;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class PublicCatalogController {

    private final PublicCatalogService catalog;
    private final ProductSearchValidator search;

    public PublicCatalogController(
            PublicCatalogService catalog, ProductSearchValidator search) {
        this.catalog = catalog;
        this.search = search;
    }

    @GetMapping
    public PageResponse<ProductSummary> products(
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String currency,
            @RequestParam(defaultValue = ProductSearchValidator.DEFAULT_SORT) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return catalog.search(search.validate(
                query, category, minPrice, maxPrice, currency, sort, page, size));
    }

    @GetMapping("/{productId}")
    public ProductDetailResponse product(@PathVariable UUID productId) {
        return catalog.detail(productId);
    }

    @GetMapping("/slug/{slug}")
    public ProductDetailResponse productBySlug(@PathVariable String slug) {
        return catalog.detailBySlug(slug);
    }

    @PostMapping("/resolve")
    @ResponseStatus(HttpStatus.OK)
    public ProductResolutionResponse resolve(
            @Valid @RequestBody ProductResolutionRequest request) {
        return catalog.resolve(request);
    }
}
