package com.orderflow.payment.webhook;

import com.orderflow.payment.config.PaymentProperties;
import com.orderflow.payment.error.PaymentDomainException;
import com.orderflow.payment.provider.PaymentProvider;
import com.orderflow.platform.error.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SimulatorWebhookVerifier {
    private final PaymentProperties properties;
    private final PaymentProvider provider;
    private final MeterRegistry metrics;
    private final Clock clock;

    public SimulatorWebhookVerifier(
            PaymentProperties properties,
            PaymentProvider provider,
            MeterRegistry metrics,
            Clock clock) {
        this.properties = properties;
        this.provider = provider;
        this.metrics = metrics;
        this.clock = clock;
    }

    public VerifiedWebhookRequest verify(
            byte[] payload, String timestampHeader, String signatureHeader,
            String eventId) {
        if (eventId == null || eventId.isBlank() || eventId.length() > 160) {
            throw signatureError();
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (RuntimeException exception) {
            timestampFailure();
            throw new PaymentDomainException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.PAYMENT_WEBHOOK_TIMESTAMP_INVALID,
                    "The webhook timestamp is invalid.");
        }
        Instant supplied = Instant.ofEpochSecond(timestamp);
        Instant now = Instant.now(clock);
        if (supplied.isBefore(now.minus(properties.webhookTolerance()))
                || supplied.isAfter(now.plus(properties.webhookTolerance()))) {
            timestampFailure();
            throw new PaymentDomainException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.PAYMENT_WEBHOOK_TIMESTAMP_INVALID,
                    "The webhook timestamp is outside the accepted tolerance.");
        }
        byte[] actual;
        try {
            String signature = signatureHeader != null && signatureHeader.startsWith("sha256=")
                    ? signatureHeader.substring(7) : signatureHeader;
            actual = signature == null ? new byte[0] : HexFormat.of().parseHex(signature);
        } catch (IllegalArgumentException exception) {
            throw signatureError();
        }
        byte[] expected = hmac(timestampHeader, payload);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw signatureError();
        }
        try {
            return new VerifiedWebhookRequest(
                    eventId, sha256(payload), provider.parseWebhook(payload));
        } catch (IllegalArgumentException exception) {
            throw new PaymentDomainException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.PAYMENT_EVENT_UNSUPPORTED,
                    "The verified webhook payload is invalid or unsupported.");
        }
    }

    public String signature(String timestamp, byte[] payload) {
        return HexFormat.of().formatHex(hmac(timestamp, payload));
    }

    public String sha256(byte[] payload) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private byte[] hmac(String timestamp, byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    properties.simulatorWebhookSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            return mac.doFinal(payload);
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Webhook signature verification unavailable", exception);
        }
    }

    private PaymentDomainException signatureError() {
        metrics.counter(
                "orderflow_payment_webhook_signature_failures_total",
                "provider", "SIMULATOR").increment();
        return new PaymentDomainException(
                HttpStatus.UNAUTHORIZED,
                ErrorCode.PAYMENT_WEBHOOK_SIGNATURE_INVALID,
                "The webhook signature is invalid.");
    }

    private void timestampFailure() {
        metrics.counter(
                "orderflow_payment_webhook_timestamp_failures_total",
                "provider", "SIMULATOR").increment();
    }
}
