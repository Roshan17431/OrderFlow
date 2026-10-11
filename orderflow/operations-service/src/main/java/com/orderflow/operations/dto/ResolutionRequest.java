package com.orderflow.operations.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolutionRequest(@NotBlank @Size(max = 300) String resolutionNote) {}
