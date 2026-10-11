package com.orderflow.payment.domain;

public enum ProviderFailureCategory {
    VALIDATION,
    DECLINED,
    AUTHENTICATION_REQUIRED,
    TEMPORARY_PROVIDER_FAILURE,
    NETWORK_FAILURE,
    TIMEOUT,
    RATE_LIMITED,
    DUPLICATE_REQUEST,
    UNKNOWN
}
