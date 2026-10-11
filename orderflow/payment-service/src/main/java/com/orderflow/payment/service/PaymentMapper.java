package com.orderflow.payment.service;

import com.orderflow.payment.domain.Payment;
import com.orderflow.payment.domain.PaymentAttempt;
import com.orderflow.payment.domain.Refund;
import com.orderflow.payment.dto.PaymentAttemptResponse;
import com.orderflow.payment.dto.PaymentResponse;
import com.orderflow.payment.dto.RefundResponse;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {
    public PaymentResponse payment(Payment value) {
        return new PaymentResponse(
                value.getId(), value.getOrderId(), value.getStatus(), value.getAmount(),
                value.getCurrency(), value.getProvider(), value.getRefundedAmount(),
                value.getFailureCode(), value.getPaymentDeadline(), value.getCreatedAt(),
                value.getUpdatedAt(), value.getSucceededAt(), value.getFailedAt(),
                value.getCancelledAt());
    }
    public PaymentAttemptResponse attempt(PaymentAttempt value) {
        return new PaymentAttemptResponse(
                value.getAttemptNumber(), value.getStatus(), value.getFailureCode(),
                value.getCreatedAt(), value.getCompletedAt());
    }
    public RefundResponse refund(Refund value) {
        return new RefundResponse(
                value.getId(), value.getPaymentId(), value.getAmount(), value.getCurrency(),
                value.getStatus(), value.getReasonCode(), value.getFailureCode(),
                value.getCreatedAt(), value.getUpdatedAt(), value.getSucceededAt(),
                value.getFailedAt());
    }
}
