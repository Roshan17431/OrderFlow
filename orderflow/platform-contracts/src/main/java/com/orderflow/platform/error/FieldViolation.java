package com.orderflow.platform.error;

public record FieldViolation(String field, String message, Object rejectedValue) {
}
