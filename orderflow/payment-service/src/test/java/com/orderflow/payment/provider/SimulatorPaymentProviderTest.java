package com.orderflow.payment.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.databind.ObjectMapper;
import com.orderflow.payment.domain.ProviderFailureCategory;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SimulatorPaymentProviderTest {
    private final SimulatorPaymentProvider provider =
            new SimulatorPaymentProvider(tools.jackson.databind.json.JsonMapper.builder().configureForJackson2().build());
    private final ProviderPaymentRequest request = new ProviderPaymentRequest(
            UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10.00"),
            "USD", "same-operation");

    @Test
    void opaqueTokensMapToStableProviderNeutralResults() {
        ProviderPaymentResult success = provider.confirm(request, "tok_success");
        assertThat(success.result()).isEqualTo(ProviderPaymentResult.Result.PROCESSING);
        assertThat(provider.confirm(request, "tok_success")).isEqualTo(success);

        ProviderPaymentResult decline = provider.confirm(
                new ProviderPaymentRequest(
                        UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE,
                        "USD", "decline-operation"),
                "tok_declined");
        assertThat(decline.result()).isEqualTo(ProviderPaymentResult.Result.DECLINED);
        assertThat(decline.failureCategory()).isEqualTo(ProviderFailureCategory.DECLINED);
        assertThat(decline.retryable()).isFalse();
    }

    @Test
    void timeoutTemporaryFailureAndCardShapedInputAreRejectedSafely() {
        assertThatThrownBy(() -> provider.confirm(request, "tok_timeout"))
                .isInstanceOf(ProviderTimeoutException.class);
        assertThatThrownBy(() -> provider.confirm(request, "tok_temporary_failure"))
                .isInstanceOf(ProviderUnavailableException.class);
        assertThatThrownBy(() -> provider.confirm(request, "4242424242424242"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(SimulatorPaymentProvider.supportedToken("tok_requires_action")).isTrue();
        assertThat(SimulatorPaymentProvider.supportedToken("4111111111111111")).isFalse();
    }

    @Test
    void verifiedWebhookParsingUsesOnlySupportedEventShapes() {
        VerifiedProviderWebhook webhook = provider.parseWebhook(
                "{\"type\":\"payment.failed\",\"providerPaymentId\":\"sim_pay_1\","
                        .concat("\"failureCode\":\"DECLINED\",\"failureCategory\":\"DECLINED\",")
                        .concat("\"retryable\":false}")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(webhook.eventType()).isEqualTo("payment.failed");
        assertThat(webhook.failureCategory()).isEqualTo(ProviderFailureCategory.DECLINED);
        assertThatThrownBy(() -> provider.parseWebhook(
                        "{\"type\":\"unknown\"}".getBytes(
                                java.nio.charset.StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
