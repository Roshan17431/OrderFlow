package com.orderflow.product.service;

import com.orderflow.product.domain.Category;
import com.orderflow.product.domain.Product;
import com.orderflow.product.dto.CategoryResponse;
import com.orderflow.product.dto.CategorySummary;
import com.orderflow.product.dto.ProductDetailResponse;
import com.orderflow.product.dto.ProductManagementResponse;
import com.orderflow.product.dto.ProductSummary;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductSummary summary(Product product) {
        String description = product.getDescription();
        String shortDescription = description != null && description.length() > 240
                ? description.substring(0, 237) + "..."
                : description;
        return new ProductSummary(
                product.getId(),
                product.getSlug(),
                product.getName(),
                shortDescription,
                product.getPriceAmount(),
                product.getCurrency(),
                categorySummary(product.getCategory()),
                product.getUpdatedAt());
    }

    public ProductDetailResponse detail(Product product) {
        return new ProductDetailResponse(
                product.getId(),
                product.getSlug(),
                product.getName(),
                product.getDescription(),
                product.getPriceAmount(),
                product.getCurrency(),
                categorySummary(product.getCategory()),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                product.getVersion());
    }

    public ProductManagementResponse management(Product product) {
        return new ProductManagementResponse(
                product.getId(),
                product.getVendorId(),
                product.getSku(),
                product.getSlug(),
                product.getName(),
                product.getDescription(),
                product.getPriceAmount(),
                product.getCurrency(),
                product.getStatus(),
                categorySummary(product.getCategory()),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                product.getVersion());
    }

    public CategoryResponse category(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.getStatus(),
                category.getCreatedAt(),
                category.getUpdatedAt(),
                category.getVersion());
    }

    private CategorySummary categorySummary(Category category) {
        return new CategorySummary(category.getId(), category.getName(), category.getSlug());
    }
}
