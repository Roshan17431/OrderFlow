package com.orderflow.auth.dto.response;

public record CsrfResponse(String headerName, String parameterName, String token) {
}
