package com.orderflow.payment.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.payment.repository.PaymentRepository;
import com.orderflow.payment.repository.RefundRepository;
import com.orderflow.payment.webhook.SimulatorWebhookVerifier;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Portfolio-only callbacks; all state changes still pass normal signature and ledger checks. */
@Component
@Profile("cloud-demo & !prod & !production")
@ConditionalOnProperty(name = "app.payment.demo-completion.enabled", havingValue = "true")
public class DemoCompletionWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoCompletionWorker.class);
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final SimulatorWebhookVerifier verifier;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final MeterRegistry metrics;
    private final URI callback;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(1)).followRedirects(HttpClient.Redirect.NEVER).build();

    public DemoCompletionWorker(PaymentRepository payments, RefundRepository refunds,
            SimulatorWebhookVerifier verifier, ObjectMapper mapper, Clock clock,
            MeterRegistry metrics, @Value("${server.port:8086}") int port) {
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid callback port");
        this.payments = payments;
        this.refunds = refunds;
        this.verifier = verifier;
        this.mapper = mapper;
        this.clock = clock;
        this.metrics = metrics;
        this.callback = URI.create("http://127.0.0.1:" + port + "/api/payments/webhooks/simulator");
    }

    @Scheduled(fixedDelayString = "${app.payment.demo-completion.delay:5s}",
            initialDelayString = "${app.payment.demo-completion.initial-delay:15s}")
    public void complete() {
        try {
            for (var payment : payments.findDemoProcessing(clock.instant(), PageRequest.of(0, 10))) {
                send("payment.succeeded", "payment", payment.getId().toString(),
                        "providerPaymentId", payment.getProviderPaymentId());
            }
            for (var refund : refunds.findDemoProcessing(PageRequest.of(0, 10))) {
                send("refund.succeeded", "refund", refund.getId().toString(),
                        "providerRefundId", refund.getProviderRefundId());
            }
        } catch (RuntimeException exception) {
            metrics.counter("orderflow_demo_completion_total", "outcome", "query_failure").increment();
            LOGGER.warn("Cloud demo completion query unavailable; the next bounded cycle will retry");
        }
    }

    private void send(String type, String entityType, String id, String referenceField, String reference) {
        if (reference == null || Thread.currentThread().isInterrupted()) return;
        try {
            byte[] payload = mapper.writeValueAsBytes(Map.of("type", type, referenceField, reference));
            String timestamp = Long.toString(clock.instant().getEpochSecond());
            HttpRequest request = HttpRequest.newBuilder(callback).timeout(Duration.ofSeconds(2))
                    .header("Content-Type", "application/json")
                    .header("X-Payment-Timestamp", timestamp)
                    .header("X-Payment-Signature", "sha256=" + verifier.signature(timestamp, payload))
                    .header("X-Payment-Event-ID", "cloud-demo-" + entityType + "-" + id)
                    .header("X-Correlation-ID", "demo-" + id)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payload)).build();
            int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            metrics.counter("orderflow_demo_completion_total", "outcome",
                    status == 200 ? "accepted" : "callback_failure").increment();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException exception) {
            metrics.counter("orderflow_demo_completion_total", "outcome", "callback_failure").increment();
        }
    }
}
