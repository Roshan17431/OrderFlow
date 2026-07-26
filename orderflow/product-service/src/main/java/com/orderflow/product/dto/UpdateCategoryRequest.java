package com.orderflow.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank
                @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$")
                @Size(max = 120)
                String slug,
        @Size(max = 1000) String description,
        @PositiveOrZero long version) {}
