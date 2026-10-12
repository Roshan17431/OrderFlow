package com.orderflow.product.controller;

import com.orderflow.product.dto.CategoryResponse;
import com.orderflow.product.dto.CreateCategoryRequest;
import com.orderflow.product.dto.UpdateCategoryRequest;
import com.orderflow.product.dto.VersionRequest;
import com.orderflow.product.service.CategoryManagementService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/categories")
public class AdminCategoryController {

    private final CategoryManagementService categories;

    public AdminCategoryController(CategoryManagementService categories) {
        this.categories = categories;
    }

    @org.springframework.web.bind.annotation.GetMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public com.orderflow.product.dto.PageResponse<CategoryResponse> list(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size) {
        return categories.list(page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CreateCategoryRequest request) {
        return categories.create(request);
    }

    @PutMapping("/{categoryId}")
    public CategoryResponse update(
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateCategoryRequest request) {
        return categories.update(categoryId, request);
    }

    @PatchMapping("/{categoryId}/activate")
    public CategoryResponse activate(
            @PathVariable UUID categoryId,
            @Valid @RequestBody VersionRequest request) {
        return categories.activate(categoryId, request);
    }

    @PatchMapping("/{categoryId}/deactivate")
    public CategoryResponse deactivate(
            @PathVariable UUID categoryId,
            @Valid @RequestBody VersionRequest request) {
        return categories.deactivate(categoryId, request);
    }
}
