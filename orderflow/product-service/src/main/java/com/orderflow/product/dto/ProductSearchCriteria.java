package com.orderflow.product.dto;

import java.math.BigDecimal;

public record ProductSearchCriteria(
        String query,
        String category,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String currency,
        String sort,
        int page,
        int size) {}
