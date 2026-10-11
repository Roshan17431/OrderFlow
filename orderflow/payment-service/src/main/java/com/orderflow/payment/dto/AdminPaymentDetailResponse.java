package com.orderflow.payment.dto;

import java.util.List;

public record AdminPaymentDetailResponse(
        PaymentResponse payment,
        List<PaymentAttemptResponse> attempts,
        List<RefundResponse> refunds) {
    public AdminPaymentDetailResponse {
        attempts = List.copyOf(attempts);
        refunds = List.copyOf(refunds);
    }
}
