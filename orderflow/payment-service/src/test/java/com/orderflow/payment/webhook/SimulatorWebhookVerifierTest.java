package com.orderflow.payment.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.databind.ObjectMapper;
import com.orderflow.payment.config.PaymentProperties;
import com.orderflow.payment.error.PaymentDomainException;
import com.orderflow.payment.provider.SimulatorPaymentProvider;
import com.orderflow.platform.error.ErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SimulatorWebhookVerifierTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final SimulatorWebhookVerifier verifier = new SimulatorWebhookVerifier(
            new PaymentProperties(
                    "simulator", true, false, "unit-test-secret",
                    Duration.ofMinutes(5), Duration.ofSeconds(2),
                    Duration.ofSeconds(5), 2, Duration.ofMillis(250), 100),
            new SimulatorPaymentProvider(tools.jackson.databind.json.JsonMapper.builder().configureForJackson2().build()),
            new SimpleMeterRegistry(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void exactRawBytesAreSignedAndConstantTimeValidationAcceptsThem() {
        byte[] body = "{\"type\":\"payment.succeeded\","
                .concat("\"providerPaymentId\":\"sim_pay_1\"}")
                .getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(NOW.getEpochSecond());
        String signature = verifier.signature(timestamp, body);
        VerifiedWebhookRequest result =
                verifier.verify(body, timestamp, "sha256=" + signature, "evt-1");
        assertThat(result.event().eventType()).isEqualTo("payment.succeeded");
        assertThat(result.event().retryable()).isFalse();
        assertThat(result.payloadHash()).hasSize(64);
    }

    @Test
    void changedPayloadInvalidSignatureAndStaleTimestampAreRejected() {
        byte[] body = "{\"type\":\"payment.succeeded\","
                .concat("\"providerPaymentId\":\"sim_pay_1\"}")
                .getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(NOW.getEpochSecond());
        String signature = verifier.signature(timestamp, body);
        assertThatThrownBy(() -> verifier.verify(
                        "{}".getBytes(StandardCharsets.UTF_8),
                        timestamp, signature, "evt-2"))
                .isInstanceOfSatisfying(PaymentDomainException.class, exception ->
                        assertThat(exception.code())
                                .isEqualTo(ErrorCode.PAYMENT_WEBHOOK_SIGNATURE_INVALID));
        assertThatThrownBy(() -> verifier.verify(
                        body,
                        Long.toString(NOW.minus(Duration.ofMinutes(6)).getEpochSecond()),
                        signature,
                        "evt-3"))
                .isInstanceOfSatisfying(PaymentDomainException.class, exception ->
                        assertThat(exception.code())
                                .isEqualTo(ErrorCode.PAYMENT_WEBHOOK_TIMESTAMP_INVALID));
    }

    @Test
    void missingMalformedAndFutureHeadersAreRejectedBeforeParsing() {
        byte[] invalidJson = "not-json".getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(NOW.getEpochSecond());
        assertCode(
                () -> verifier.verify(invalidJson, timestamp, null, "evt-missing-signature"),
                ErrorCode.PAYMENT_WEBHOOK_SIGNATURE_INVALID);
        assertCode(
                () -> verifier.verify(invalidJson, null, "00", "evt-missing-timestamp"),
                ErrorCode.PAYMENT_WEBHOOK_TIMESTAMP_INVALID);
        assertCode(
                () -> verifier.verify(invalidJson, "not-a-time", "00", "evt-bad-time"),
                ErrorCode.PAYMENT_WEBHOOK_TIMESTAMP_INVALID);
        assertCode(
                () -> verifier.verify(
                        invalidJson,
                        Long.toString(NOW.plus(Duration.ofMinutes(6)).getEpochSecond()),
                        "00",
                        "evt-future"),
                ErrorCode.PAYMENT_WEBHOOK_TIMESTAMP_INVALID);
        assertCode(
                () -> verifier.verify(invalidJson, timestamp, "00", "evt-invalid-json"),
                ErrorCode.PAYMENT_WEBHOOK_SIGNATURE_INVALID);
    }

    @Test
    void verifiedInvalidJsonReturnsAStableProviderNeutralError() {
        byte[] invalidJson = "not-json".getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(NOW.getEpochSecond());
        assertCode(
                () -> verifier.verify(
                        invalidJson,
                        timestamp,
                        verifier.signature(timestamp, invalidJson),
                        "evt-verified-invalid-json"),
                ErrorCode.PAYMENT_EVENT_UNSUPPORTED);
    }

    private void assertCode(Runnable operation, ErrorCode expected) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(PaymentDomainException.class, exception ->
                        assertThat(exception.code()).isEqualTo(expected));
    }
}
