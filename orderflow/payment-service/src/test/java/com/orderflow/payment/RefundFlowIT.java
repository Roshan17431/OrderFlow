package com.orderflow.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orderflow.payment.domain.Payment;
import com.orderflow.payment.domain.PaymentStatus;
import com.orderflow.payment.domain.Refund;
import com.orderflow.payment.domain.RefundStatus;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class RefundFlowIT extends PaymentFlowIT {

    @Test
    void partialAndFullRefundsAreIdempotentAndCannotExceedTheBalance()
            throws Exception {
        Payment payment = successfulPayment("70.00", "refund-lifecycle");

        MvcResult first = createRefund(
                payment.getId(), "partial-refund", "20.00", 202);
        UUID firstRefundId = UUID.fromString(
                objectMapper.readTree(first.getResponse().getContentAsString())
                        .get("id").asText());
        createRefund(payment.getId(), "partial-refund", "20.00", 202)
                .getResponse();
        assertThat(refunds.count()).isEqualTo(1);

        mockMvc.perform(post("/api/admin/payments/{id}/refunds", payment.getId())
                        .with(admin())
                        .header("Idempotency-Key", "partial-refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":21.00,\"reason\":\"CUSTOMER_REQUEST\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("REFUND_IDEMPOTENCY_CONFLICT"));

        Refund firstRefund = refunds.findById(firstRefundId).orElseThrow();
        webhook(
                "evt-partial-refund",
                "{\"type\":\"refund.succeeded\",\"providerRefundId\":\""
                        + firstRefund.getProviderRefundId() + "\"}",
                200);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            Payment updated = payments.findById(payment.getId()).orElseThrow();
            assertThat(updated.getStatus())
                    .isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
            assertThat(updated.getRefundedAmount()).isEqualByComparingTo("20.00");
        });

        mockMvc.perform(post("/api/admin/payments/{id}/refunds", payment.getId())
                        .with(admin())
                        .header("Idempotency-Key", "refund-too-large")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":50.01,\"reason\":\"CUSTOMER_REQUEST\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("REFUND_AMOUNT_EXCEEDS_AVAILABLE"));

        MvcResult second = createRefund(
                payment.getId(), "full-refund-remainder", "50.00", 202);
        UUID secondRefundId = UUID.fromString(
                objectMapper.readTree(second.getResponse().getContentAsString())
                        .get("id").asText());
        Refund secondRefund = refunds.findById(secondRefundId).orElseThrow();
        webhook(
                "evt-full-refund",
                "{\"type\":\"refund.succeeded\",\"providerRefundId\":\""
                        + secondRefund.getProviderRefundId() + "\"}",
                200);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            Payment updated = payments.findById(payment.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(updated.getRefundedAmount()).isEqualByComparingTo("70.00");
            assertThat(count(
                    "select count(*) from outbox_event"
                            + " where event_type = 'PAYMENT_REFUNDED'"
                            + " and status = 'PUBLISHED'"))
                    .isEqualTo(2);
        });
    }

    @Test
    void failedAndConcurrentRefundsPreserveThePaymentBalance() throws Exception {
        Payment failedRefundPayment =
                successfulPayment("40.00", "failed-refund");
        MvcResult failed = createRefund(
                failedRefundPayment.getId(), "failed-refund-key", "10.00", 202);
        UUID failedRefundId = UUID.fromString(
                objectMapper.readTree(failed.getResponse().getContentAsString())
                        .get("id").asText());
        Refund failedRefund = refunds.findById(failedRefundId).orElseThrow();
        webhook(
                "evt-failed-refund",
                "{\"type\":\"refund.failed\",\"providerRefundId\":\""
                        + failedRefund.getProviderRefundId()
                        + "\",\"failureCode\":\"PROVIDER_UNAVAILABLE\","
                        + "\"failureCategory\":\"TEMPORARY_PROVIDER_FAILURE\","
                        + "\"retryable\":true}",
                200);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            Payment updated =
                    payments.findById(failedRefundPayment.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
            assertThat(updated.getRefundedAmount()).isEqualByComparingTo("0.00");
            assertThat(refunds.findById(failedRefundId).orElseThrow().getStatus())
                    .isEqualTo(RefundStatus.FAILED);
            assertThat(count(
                    "select count(*) from outbox_event"
                            + " where event_type = 'PAYMENT_REFUND_FAILED'"
                            + " and status = 'PUBLISHED'"))
                    .isEqualTo(1);
        });

        Payment concurrentPayment =
                successfulPayment("50.00", "concurrent-refund");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> {
                start.await();
                return refundStatus(
                        concurrentPayment.getId(), "concurrent-refund-a", "40.00");
            });
            Future<Integer> second = executor.submit(() -> {
                start.await();
                return refundStatus(
                        concurrentPayment.getId(), "concurrent-refund-b", "40.00");
            });
            start.countDown();
            assertThat(List.of(
                            first.get(20, TimeUnit.SECONDS),
                            second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(202, 409);
        } finally {
            executor.shutdownNow();
        }
        assertThat(refunds.findByPaymentId(
                        concurrentPayment.getId(),
                        org.springframework.data.domain.PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(1);
        assertThat(payments.findById(concurrentPayment.getId())
                        .orElseThrow().getRefundedAmount())
                .isEqualByComparingTo("0.00");
    }

    private Payment successfulPayment(String amount, String key) throws Exception {
        UUID orderId = UUID.randomUUID();
        publish(paymentRequested(orderId, CUSTOMER_ID, amount));
        Payment payment = awaitPayment(orderId);
        mockMvc.perform(post("/api/payments/orders/{id}/confirm", orderId)
                        .with(customer())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethodToken\":\"tok_success\"}"))
                .andExpect(status().isAccepted());
        payment = payments.findById(payment.getId()).orElseThrow();
        webhook(
                "evt-success-" + key,
                "{\"type\":\"payment.succeeded\",\"providerPaymentId\":\""
                        + payment.getProviderPaymentId() + "\"}",
                200);
        UUID paymentId = payment.getId();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(payments.findById(paymentId).orElseThrow().getStatus())
                        .isEqualTo(PaymentStatus.SUCCEEDED));
        return payments.findById(paymentId).orElseThrow();
    }

    private MvcResult createRefund(
            UUID paymentId, String key, String amount, int expectedStatus)
            throws Exception {
        return mockMvc.perform(post("/api/admin/payments/{id}/refunds", paymentId)
                        .with(admin())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + amount
                                + ",\"reason\":\"CUSTOMER_REQUEST\"}"))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    private int refundStatus(UUID paymentId, String key, String amount)
            throws Exception {
        return mockMvc.perform(post("/api/admin/payments/{id}/refunds", paymentId)
                        .with(admin())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + amount
                                + ",\"reason\":\"CUSTOMER_REQUEST\"}"))
                .andReturn()
                .getResponse()
                .getStatus();
    }
}
