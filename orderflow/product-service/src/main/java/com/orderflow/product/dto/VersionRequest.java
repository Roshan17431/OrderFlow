package com.orderflow.product.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record VersionRequest(@PositiveOrZero long version) {}
