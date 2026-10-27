package com.orderflow.payment.provider;

import tools.jackson.databind.ObjectMapper;
import com.orderflow.payment.domain.ProviderFailureCategory;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class SimulatorPaymentProvider implements PaymentProvider {

    private static final Set<String> TOKENS = Set.of(
            "tok_success", "tok_declined", "tok_requires_action",
            "tok_timeout", "tok_temporary_failure");
    private final ObjectMapper objectMapper;
    private final Map<String, String> registered = new ConcurrentHashMap<>();
    private final Map<String, ProviderPaymentResult> confirmations = new ConcurrentHashMap<>();
    private final Map<String, ProviderPaymentStatus> statuses = new ConcurrentHashMap<>();

    public SimulatorPaymentProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "SIMULATOR";
    }

    @Override
    public String register(ProviderPaymentRequest request) {
        return registered.computeIfAbsent(
                request.idempotencyReference(),
                ignored -> "sim_pay_" + request.paymentId());
    }

    @Override
    public ProviderPaymentResult confirm(
            ProviderPaymentRequest request, String opaquePaymentMethodToken) {
        if (!TOKENS.contains(opaquePaymentMethodToken)) {
            throw new IllegalArgumentException("Unsupported opaque payment method token");
        }
        if ("tok_timeout".equals(opaquePaymentMethodToken)) {
            throw new ProviderTimeoutException();
        }
        if ("tok_temporary_failure".equals(opaquePaymentMethodToken)) {
            throw new ProviderUnavailableException();
        }
        String resultKey = request.idempotencyReference();
        return confirmations.computeIfAbsent(resultKey, ignored -> {
            String providerPaymentId = register(request);
            String attemptId = "sim_attempt_" + resultKey;
            return switch (opaquePaymentMethodToken) {
                case "tok_declined" -> new ProviderPaymentResult(
                        ProviderPaymentResult.Result.DECLINED,
                        providerPaymentId,
                        attemptId,
                        "PAYMENT_DECLINED",
                        ProviderFailureCategory.DECLINED,
                        false);
                case "tok_requires_action" -> new ProviderPaymentResult(
                        ProviderPaymentResult.Result.REQUIRES_ACTION,
                        providerPaymentId,
                        attemptId,
                        "AUTHENTICATION_REQUIRED",
                        ProviderFailureCategory.AUTHENTICATION_REQUIRED,
                        false);
                default -> new ProviderPaymentResult(
                        ProviderPaymentResult.Result.PROCESSING,
                        providerPaymentId,
                        attemptId,
                        null,
                        null,
                        false);
            };
        });
    }

    @Override
    public ProviderCancellationResult cancel(
            String providerPaymentId, String idempotencyReference) {
        statuses.put(providerPaymentId, new ProviderPaymentStatus(
                ProviderPaymentStatus.State.FAILED, "PAYMENT_CANCELLED"));
        return new ProviderCancellationResult(true, providerPaymentId);
    }

    @Override
    public ProviderPaymentStatus lookup(String providerPaymentId) {
        ProviderPaymentStatus known = statuses.get(providerPaymentId);
        if (known != null) return known;
        return confirmations.values().stream()
                .filter(result -> providerPaymentId.equals(result.providerPaymentId()))
                .findFirst().map(result -> switch (result.result()) {
                    case DECLINED -> new ProviderPaymentStatus(ProviderPaymentStatus.State.FAILED, result.failureCode());
                    case PROCESSING, REQUIRES_ACTION -> new ProviderPaymentStatus(ProviderPaymentStatus.State.PENDING, null);
                }).orElse(new ProviderPaymentStatus(
                        registered.containsValue(providerPaymentId) ? ProviderPaymentStatus.State.PENDING : ProviderPaymentStatus.State.UNKNOWN, null));
    }

    @Override
    public ProviderRefundResult refund(ProviderRefundRequest request) {
        return new ProviderRefundResult(true, "sim_ref_" + request.refundId());
    }

    @Override
    public VerifiedProviderWebhook parseWebhook(byte[] verifiedPayload) {
        try {
            WebhookPayload payload =
                    objectMapper.readValue(verifiedPayload, WebhookPayload.class);
            if (!Set.of(
                            "payment.succeeded",
                            "payment.failed",
                            "payment.cancelled",
                            "refund.succeeded",
                            "refund.failed")
                    .contains(payload.type())) {
                throw new IllegalArgumentException("Unsupported simulator webhook type");
            }
            if (payload.providerPaymentId() != null) {
                if ("payment.succeeded".equals(payload.type())) {
                    statuses.put(payload.providerPaymentId(), new ProviderPaymentStatus(
                            ProviderPaymentStatus.State.SUCCEEDED, null));
                } else if ("payment.failed".equals(payload.type()) || "payment.cancelled".equals(payload.type())) {
                    statuses.put(payload.providerPaymentId(), new ProviderPaymentStatus(
                            ProviderPaymentStatus.State.FAILED, payload.failureCode()));
                }
            }
            return new VerifiedProviderWebhook(
                    payload.type(),
                    payload.providerPaymentId(),
                    payload.providerRefundId(),
                    payload.failureCode(),
                    payload.failureCategory(),
                    payload.retryable());
        } catch (tools.jackson.core.JacksonException exception) {
            throw new IllegalArgumentException("Malformed simulator webhook payload");
        }
    }

    public static boolean supportedToken(String token) {
        return token != null
                && token.getBytes(StandardCharsets.UTF_8).length <= 128
                && TOKENS.contains(token);
    }

    private record WebhookPayload(
            String type,
            String providerPaymentId,
            String providerRefundId,
            String failureCode,
            ProviderFailureCategory failureCategory,
            boolean retryable) {}
}
