package com.orderflow.payment.controller;

import com.orderflow.payment.correlation.PaymentRequestAttributes;
import com.orderflow.payment.service.PaymentWebhookService;
import com.orderflow.payment.webhook.SimulatorWebhookVerifier;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SimulatorWebhookController {
    private final SimulatorWebhookVerifier verifier;
    private final PaymentWebhookService webhooks;

    public SimulatorWebhookController(
            SimulatorWebhookVerifier verifier, PaymentWebhookService webhooks) {
        this.verifier = verifier;
        this.webhooks = webhooks;
    }

    @PostMapping(
            value = "/api/payments/webhooks/simulator",
            consumes = "application/json")
    ResponseEntity<Map<String, String>> receive(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-Payment-Signature", required = false)
                    String signature,
            @RequestHeader(value = "X-Payment-Timestamp", required = false)
                    String timestamp,
            @RequestHeader(value = "X-Payment-Event-ID", required = false)
                    String eventId,
            HttpServletRequest request) {
        var verified = verifier.verify(rawBody, timestamp, signature, eventId);
        webhooks.process(
                verified,
                (String) request.getAttribute(PaymentRequestAttributes.CORRELATION_ID));
        return ResponseEntity.ok(Map.of("status", "accepted"));
    }
}
