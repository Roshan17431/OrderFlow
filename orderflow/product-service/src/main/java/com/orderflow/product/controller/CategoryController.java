package com.orderflow.product.controller;

import com.orderflow.product.dto.CategoryListResponse;
import com.orderflow.product.dto.PageResponse;
import com.orderflow.product.dto.ProductSummary;
import com.orderflow.product.service.PublicCatalogService;
import com.orderflow.product.validation.ProductSearchValidator;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final PublicCatalogService catalog;
    private final ProductSearchValidator search;

    public CategoryController(PublicCatalogService catalog, ProductSearchValidator search) {
        this.catalog = catalog;
        this.search = search;
    }

    @GetMapping
    public CategoryListResponse categories() {
        return catalog.categories();
    }

    @GetMapping("/{slug}/products")
    public PageResponse<ProductSummary> products(
            @PathVariable String slug,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String currency,
            @RequestParam(defaultValue = ProductSearchValidator.DEFAULT_SORT) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return catalog.categoryProducts(
                slug,
                search.validate(
                        query, slug, minPrice, maxPrice, currency, sort, page, size));
    }
}
