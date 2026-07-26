package com.orderflow.product.dto;

import java.util.List;

public record CategoryListResponse(List<CategoryResponse> items) {

    public CategoryListResponse {
        items = List.copyOf(items);
    }
}
